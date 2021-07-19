package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher

import net.dv8tion.jda.api.entities.Guild

import java.util.concurrent.Executors

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

fun getProgressBar(currentTime: Long, totalDuration: Long, blocks: Int = 15): String {
    val passed = (currentTime.toDouble() / totalDuration * blocks).toInt()

    return buildString {
        passed.takeIf { it > 0 }?.let { _ ->
            append("[")

            for (i in 0 until blocks)
                append("\u25AC".takeIf { passed > i }.orEmpty())

            append("](https://discord.com)")
        }

        for (i in 0 until blocks)
            append("\u25AC".takeUnless { passed >= i }.orEmpty())
    }
}