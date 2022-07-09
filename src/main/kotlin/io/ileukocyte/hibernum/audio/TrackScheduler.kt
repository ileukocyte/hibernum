package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.extensions.*

import java.util.concurrent.ConcurrentLinkedQueue

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.interactions.InteractionHook

import org.jetbrains.kotlin.utils.addToStdlib.cast

class TrackScheduler(private val player: AudioPlayer) : AudioEventAdapter() {
    var queue = ConcurrentLinkedQueue<AudioTrack>()
    var loopMode = LoopMode.DISABLED

    operator fun plusAssign(track: AudioTrack) {
        if (!player.startTrack(track, true)) {
            val userData = track.userData as TrackUserData

            if (userData.announceQueueing) {
                val embed = defaultEmbed(
                    desc = "[${track.info.title}](${track.info.uri}) " +
                            "has been added to the queue!",
                    type = EmbedType.SUCCESS,
                )

                userData.ifFromSlashCommand?.let {
                    it.editOriginalComponents().setEmbeds(embed).queue(null) {
                        userData.channel.sendMessageEmbeds(embed).queue()
                    }
                } ?: userData.channel.sendMessageEmbeds(embed).queue()
            }

            queue.offer(track)
        }
    }

    fun nextTrack(
        ifFromSlashCommand: InteractionHook? = null,
        newAnnouncementChannel: MessageChannel? = null,
    ) {
        if (queue.isNotEmpty()) {
            val next = queue.poll()

            next.userData = next.userData.cast<TrackUserData>().copy(
                ifFromSlashCommand = ifFromSlashCommand,
                channel = newAnnouncementChannel ?: next.userData.cast<TrackUserData>().channel,
            )

            player.startTrack(next, false)
        } else player.destroy()
    }

    fun shuffle() {
        queue = ConcurrentLinkedQueue(queue.shuffled())
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val userData = track.userData as TrackUserData

        val embed = if (userData.isFirstTrackPlaying && userData.playCount == 0) {
            defaultEmbed(
                desc = "[${track.info.title}](${track.info.uri}) is playing now!",
                type = EmbedType.SUCCESS,
            )
        } else {
            buildEmbed {
                color = Immutable.SUCCESS
                description = "[${track.info.title}](${track.info.uri}) is playing now!"
            }
        }

        val consumer = { message: Message ->
            track.userData = userData.copy(announcement = message, playCount = userData.playCount + 1)
        }

        userData.ifFromSlashCommand?.let {
            it.editOriginalComponents().setEmbeds(embed).queue(consumer) {
                userData.channel.sendMessageEmbeds(embed).queue(consumer)
            }
        } ?: userData.channel.sendMessageEmbeds(embed).queue(consumer)
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