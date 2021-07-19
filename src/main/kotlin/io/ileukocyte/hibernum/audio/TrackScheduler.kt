package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import java.util.concurrent.ConcurrentLinkedQueue

class TrackScheduler(private val player: AudioPlayer) : AudioEventAdapter() {
    var queue = ConcurrentLinkedQueue<AudioTrack>()
    var loopMode = LoopMode.DISABLED

    operator fun plusAssign(track: AudioTrack) {
        if (!player.startTrack(track, true))
            queue.offer(track)
    }

    fun nextTrack() {
        if (queue.isNotEmpty())
            player.startTrack(queue.poll(), false)
        else
            player.destroy()
    }

    fun shuffle() {
        queue = ConcurrentLinkedQueue(queue.shuffled())
    }

    /*override fun onTrackStart(player: AudioPlayer, track: AudioTrack) =
        track.userData.cast<TrackUserData>().channel
            .sendMessage("DEBUG: starting ${track.info.title}")
            .queue()*/

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            CoroutineScope(MusicContext).launch {
                when (loopMode) {
                    LoopMode.SONG ->
                        player.playTrack(track.makeClone().apply { userData = track.userData })
                    LoopMode.QUEUE -> {
                        queue.offer(track.makeClone().apply { userData = track.userData })

                        nextTrack()
                    }
                    else -> nextTrack()
                }
            }
        }
    }
}