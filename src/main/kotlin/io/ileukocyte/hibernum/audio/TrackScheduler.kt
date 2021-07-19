package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.launch

class TrackScheduler(private val player: AudioPlayer) : AudioEventAdapter() {
    var queue = Channel<AudioTrack?>()
    var loopMode = LoopMode.DISABLED

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun plusAssign(track: AudioTrack?) {
        if (!queue.isClosedForSend) {
            if (!player.startTrack(track, true))
                queue.send(track)
        } else {
            queue = Channel()

            plusAssign(track)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun nextTrack() {
        if (!queue.isEmpty) {
            player.startTrack(queue.receive(), false)
        } else {
            queue.close()
            player.destroy()
        }
    }

    suspend fun shuffle() {
        queue = Channel<AudioTrack?>()
            .apply { queue.toList().shuffled().forEach { send(it) } }
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            CoroutineScope(MusicContext).launch {
                when (loopMode) {
                    LoopMode.SONG ->
                        this@TrackScheduler += track.makeClone().apply { userData = track.userData }
                    LoopMode.QUEUE -> {
                        queue.send(track.makeClone().apply { userData = track.userData })

                        nextTrack()
                    }
                    else -> nextTrack()
                }
            }
        }
    }
}