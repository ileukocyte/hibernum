package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.extensions.sendSuccess

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

import java.util.concurrent.Executors

import kotlin.coroutines.CoroutineContext

private val musicContextDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

object MusicContext : CoroutineContext by musicContextDispatcher, AutoCloseable by musicContextDispatcher

val PLAYER_MANAGER  = DefaultAudioPlayerManager().apply {
    AudioSourceManagers.registerLocalSource(this)
    AudioSourceManagers.registerRemoteSources(this)
}

val MUSIC_MANAGERS = hashMapOf<Long, GuildMusicManager>()

val Guild.audioPlayer: GuildMusicManager? get() {
    val manager = MUSIC_MANAGERS[idLong]
        ?: MUSIC_MANAGERS.put(idLong, GuildMusicManager(PLAYER_MANAGER ))

    audioManager.sendingHandler = manager?.sendHandler

    return manager
}

fun GuildMusicManager.stop() {
    player.destroy()
    player.isPaused = false
    player.volume = 100
    scheduler.loopMode = LoopMode.DISABLED
    scheduler.queue.close()
}