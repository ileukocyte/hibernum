package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.extensions.sendSuccess

import java.util.concurrent.ConcurrentLinkedQueue

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent

import org.jetbrains.kotlin.utils.addToStdlib.cast

class TrackScheduler(private val player: AudioPlayer) : AudioEventAdapter() {
    var queue = ConcurrentLinkedQueue<AudioTrack>()
    var loopMode = LoopMode.DISABLED

    operator fun plusAssign(track: AudioTrack) {
        if (!player.startTrack(track, true)) {
            val userData = track.userData as TrackUserData

            if (userData.announceQueued) {
                val action = userData.ifFromSlashCommand?.replySuccess("[${track.info.title}](${track.info.uri}) " +
                        "has been added to the queue!")
                    ?: userData.channel.sendSuccess("[${track.info.title}](${track.info.uri}) " +
                            "has been added to the queue!")

                action.queue({}) {}
            }

            queue.offer(track)
        }
    }

    fun nextTrack(ifFromSlashCommand: SlashCommandEvent? = null) {
        if (queue.isNotEmpty()) {
            val next = queue.poll()

            next.userData = next.userData.cast<TrackUserData>().copy(ifFromSlashCommand = ifFromSlashCommand)

            player.startTrack(next, false)
        } else player.destroy()
    }

    fun shuffle() {
        queue = ConcurrentLinkedQueue(queue.shuffled())
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val embedDescription = "[${track.info.title}](${track.info.uri}) is playing now!"
        val userData = track.userData as TrackUserData
        val action =
            if (userData.firstTrackPlaying && userData.playCount == 0) {
                userData.ifFromSlashCommand?.replySuccess(embedDescription)
                    ?: userData.channel.sendSuccess(embedDescription)
            } else {
                userData.ifFromSlashCommand?.replyEmbed {
                    color = Immutable.SUCCESS
                    description = embedDescription
                } ?: userData.channel.sendEmbed {
                    color = Immutable.SUCCESS
                    description = embedDescription
                }
            }

        action.queue({
            // Messages are saved to user data instances so a song announcement can be deleted after the song stops playing
            // Replies to slash commands won't be cast to Message in order that the command won't be deleted as well
            track.userData = userData.copy(announcement = it as? Message, playCount = userData.playCount + 1)
        }) {}
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