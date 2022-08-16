package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import java.util.concurrent.Executors

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.serialization.json.*

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.AudioChannel
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildMessageChannel
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.InteractionType

import org.jetbrains.kotlin.utils.addToStdlib.cast

private val musicContextDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

object MusicContext : CoroutineContext by musicContextDispatcher, AutoCloseable by musicContextDispatcher

val PLAYER_MANAGER = DefaultAudioPlayerManager().apply {
    configuration.isFilterHotSwapEnabled = true

    registerSourceManager(YoutubeAudioSourceManager().apply { setPlaylistPageCount(10) })

    AudioSourceManagers.registerLocalSource(this)
    AudioSourceManagers.registerRemoteSources(this)
}

val MUSIC_MANAGERS = hashMapOf<Long, GuildMusicManager>()

val Guild.audioPlayer: GuildMusicManager?
    get() {
        val manager = MUSIC_MANAGERS[idLong]

        audioManager.sendingHandler = manager?.sendingHandler

        return manager
    }

fun JDA.loadGuildMusicManagers() =
    guildCache.forEach { MUSIC_MANAGERS[it.idLong] = GuildMusicManager(PLAYER_MANAGER) }

fun GuildMusicManager.stop() {
    val announcement = player.playingTrack?.customUserData?.announcement

    announcement?.takeIf {
        it.interaction?.takeIf { i -> i.type == InteractionType.COMMAND } === null
    }?.delete()?.queue(null) {}

    player.destroy()
    player.isPaused = false
    player.volume = 100
    player.setFilterFactory(null)
    scheduler.loopMode = LoopMode.DISABLED
    scheduler.pitchOffset.set(0.0)
    scheduler.speed.set(1.0)

    scheduler.queue.clear()
}

fun getEmbedProgressBar(currentTime: Long, totalDuration: Long, blocks: Int = 15): String {
    val passed = (currentTime.toDouble() / totalDuration * blocks).toInt()

    return buildString {
        passed.takeIf { it > 0 }?.let {
            append("[")

            for (i in 0 until blocks) {
                append("\u25AC".takeIf { passed > i }.orEmpty())
            }

            append("](https://discord.com)")
        }

        for (i in 0 until blocks) {
            append("\u25AC".takeUnless { passed >= i }.orEmpty())
        }
    }
}

fun AudioChannel.canJoinFromAnother(member: Member): Boolean {
    val isNotPlaying = guild.audioPlayer?.player?.playingTrack === null
    val isPlayingAndCanMove = !isNotPlaying && member.hasPermission(Permission.VOICE_MOVE_OTHERS)
    val isPlayingInEmptyChannel = !isNotPlaying
            && guild.selfMember.voiceState?.channel?.members?.none { !it.user.isBot } == true

    return isNotPlaying || isPlayingAndCanMove || isPlayingInEmptyChannel
}

val AudioTrack.customUserData: TrackUserData
    get() = userData.cast()

data class TrackUserData(
    val requester: User?,
    val channel: GuildMessageChannel?,
    val thumbnail: String? = null,
    val announcement: Message? = null,
    val announceQueueing: Boolean = false,
    val isFirstToPlay: Boolean = false,
    val ifFromSlashCommand: InteractionHook? = null,
    val playCount: Int = 0,
)

fun GuildMusicManager.exportQueueAsJson(
    includeInternalData: Boolean,
    includePlayerVolume: Boolean = false,
): JsonObject {
    fun AudioTrack.exportTrackAsJson(isCurrent: Boolean): JsonObject {
        val uri = info.uri
        val title = info.title

        val data = customUserData
        val requester = data.requester?.idLong
        val channel = data.channel?.idLong
        val thumbnail = data.thumbnail
        val announcement = data.announcement?.idLong
        val announceQueueing = data.announceQueueing
        val isFirstToPlay = data.isFirstToPlay
        val playCount = data.playCount

        val map = mutableMapOf(
            "title" to JsonPrimitive(title),
            "url" to JsonPrimitive(uri),
            "requester_id" to JsonPrimitive(requester),
            "thumbnail" to JsonPrimitive(thumbnail),
        )

        if (includeInternalData) {
            map["first_to_play"] = JsonPrimitive(isFirstToPlay)
            map["announce_queueing"] = JsonPrimitive(announceQueueing)
            map["announcement_id"] = JsonPrimitive(announcement)
            map["play_count"] = JsonPrimitive(playCount)
            map["channel_id"] = JsonPrimitive(channel)

            if (isCurrent) {
                map["position_millis"] = JsonPrimitive(position)
            }
        }

        return JsonObject(map)
    }

    fun exportPlayerAsJson() = JsonObject(mutableMapOf(
        "is_paused" to JsonPrimitive(player.isPaused),
        "looping_mode" to JsonPrimitive(scheduler.loopMode.name.lowercase()),
    ).apply {
        if (includePlayerVolume) {
            this["volume"] = JsonPrimitive(player.volume)
        }
    })

    return JsonObject(player.playingTrack?.exportTrackAsJson(true)?.let { current ->
        mapOf(
            "current_track" to current,
            "queue" to JsonArray(scheduler.queue.map { it.exportTrackAsJson(false) }),
            "player" to exportPlayerAsJson(),
        )
    } ?: emptyMap())
}

suspend fun AudioPlayerManager.loadItemAsync(orderingKey: Any, identifier: String) = suspendCoroutine {
    loadItemOrdered(orderingKey, identifier, object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            it.resume(track)
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            it.resume(playlist)
        }

        override fun noMatches() {
            it.resume(null)
        }

        override fun loadFailed(exception: FriendlyException) {
            it.resumeWithException(exception)
        }
    })
}