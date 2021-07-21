package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.ileukocyte.hibernum.Immutable

import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.extensions.sendSuccess
import net.dv8tion.jda.api.entities.Message

import java.util.concurrent.ConcurrentLinkedQueue

class TrackScheduler(private val player: AudioPlayer) : AudioEventAdapter() {
    var queue = ConcurrentLinkedQueue<AudioTrack>()
    var loopMode = LoopMode.DISABLED

    operator fun plusAssign(track: AudioTrack) {
        if (!player.startTrack(track, true)) {
            val userData = track.userData as TrackUserData

            if (userData.announceQueued) {
                val action = userData.ifFromSlashCommand?.replySuccess("[${track.info.title}](${track.info.uri}) " +
                        "has been successfully added to the queue!")
                    ?: userData.channel.sendSuccess("[${track.info.title}](${track.info.uri}) " +
                            "has been successfully added to the queue!")

                action.queue({}) {}
            }

            queue.offer(track)
        }
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

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val userData = track.userData as TrackUserData
        val action =
            if (userData.firstTrackPlaying)
                userData.ifFromSlashCommand?.replySuccess("[${track.info.title}](${track.info.uri}) is playing now!")
                    ?: userData.channel.sendSuccess("[${track.info.title}](${track.info.uri}) is playing now!")
            else userData.channel.sendEmbed {
                color = Immutable.SUCCESS
                description = "[${track.info.title}](${track.info.uri}) is playing now!"
            }

        action.queue({ track.userData = userData.copy(announcement = it as? Message) }) {}
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            when (loopMode) {
                LoopMode.SONG ->
                    player.playTrack(track.makeClone().apply { userData = track.userData })
                LoopMode.QUEUE -> {
                    queue.offer(track.makeClone().apply { userData = track.userData })

                    val userData = track.userData as TrackUserData

                    userData.announcement?.delete()?.queue({}) {}

                    nextTrack()
                }
                else -> {
                    val userData = track.userData as TrackUserData

                    userData.announcement?.delete()?.queue({}) {}

                    nextTrack()
                }
            }
        }
    }
}