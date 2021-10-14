package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers

import java.util.concurrent.Executors

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

import org.jetbrains.kotlin.utils.addToStdlib.cast

private val musicContextDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

object MusicContext : CoroutineContext by musicContextDispatcher, AutoCloseable by musicContextDispatcher

val PLAYER_MANAGER = DefaultAudioPlayerManager().apply {
    AudioSourceManagers.registerLocalSource(this)
    AudioSourceManagers.registerRemoteSources(this)
}

val MUSIC_MANAGERS = hashMapOf<Long, GuildMusicManager>()

val Guild.audioPlayer: GuildMusicManager?
    get() {
        val manager = MUSIC_MANAGERS[idLong]

        audioManager.sendingHandler = manager?.sendHandler

        return manager
    }

fun JDA.loadGuildMusicManagers() = guildCache.forEach { MUSIC_MANAGERS[it.idLong] = GuildMusicManager(PLAYER_MANAGER) }

fun GuildMusicManager.stop() {
    player.playingTrack?.userData?.cast<TrackUserData>()?.announcement?.delete()?.queue({}) {}

    player.destroy()
    player.isPaused = false
    player.volume = 100
    scheduler.loopMode = LoopMode.DISABLED
    scheduler.queue.clear()
}

fun getEmbedProgressBar(currentTime: Long, totalDuration: Long, blocks: Int = 15): String {
    val passed = (currentTime.toDouble() / totalDuration * blocks).toInt()

    return buildString {
        passed.takeIf { it > 0 }?.let { _ ->
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

data class TrackUserData(
    val user: User,
    val channel: MessageChannel,
    val thumbnail: String? = null,
    val announcement: Message? = null,
    val announceQueued: Boolean = false,
    val firstTrackPlaying: Boolean = false,
    val ifFromSlashCommand: SlashCommandEvent? = null,
    val playCount: Int = 0,
)