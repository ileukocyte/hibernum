package io.ileukocyte.hibernum.audio

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.extensions.*

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

import kotlin.time.Duration.Companion.minutes

import kotlinx.coroutines.*

import net.dv8tion.jda.api.entities.GuildMessageChannel
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.InteractionType

class TrackScheduler(private val player: AudioPlayer) : AudioEventAdapter() {
    var queue = ConcurrentLinkedQueue<AudioTrack>()
    var loopMode = LoopMode.DISABLED

    operator fun plusAssign(track: AudioTrack) {
        if (!player.startTrack(track, true)) {
            val userData = track.customUserData

            if (userData.announceQueueing) {
                val embed = defaultEmbed(
                    desc = "[${track.info.title}](${track.info.uri}) " +
                            "has been added to the queue!",
                    type = EmbedType.SUCCESS,
                )

                userData.ifFromSlashCommand?.let {
                    it.editOriginalComponents().setEmbeds(embed).queue(null) {
                        userData.channel?.sendMessageEmbeds(embed)?.queue()
                    }
                } ?: userData.channel?.sendMessageEmbeds(embed)?.queue()
            }

            queue.offer(track)
        }
    }

    fun nextTrack(
        ifFromSlashCommand: InteractionHook? = null,
        newAnnouncementChannel: GuildMessageChannel? = null,
    ) {
        if (queue.isNotEmpty()) {
            val next = queue.poll()

            next.userData = next.customUserData.copy(
                ifFromSlashCommand = ifFromSlashCommand,
                channel = newAnnouncementChannel ?: next.customUserData.channel,
            )

            player.startTrack(next, false)
        } else {
            player.destroy()
        }
    }

    fun shuffle() {
        queue = ConcurrentLinkedQueue(queue.shuffled())
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val userData = track.customUserData

        val embed = if (userData.isFirstToPlay && userData.playCount == 0) {
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
            track.userData = userData.copy(
                announcement = message,
                playCount = userData.playCount.inc(),
            )
        }

        userData.ifFromSlashCommand?.let {
            it.editOriginalComponents().setEmbeds(embed).queue(consumer) {
                userData.channel?.sendMessageEmbeds(embed)?.queue(consumer)
            }
        } ?: userData.channel?.sendMessageEmbeds(embed)?.queue(consumer)
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        val data = track.customUserData

        if (endReason.mayStartNext) {
            when (loopMode) {
                LoopMode.SONG -> {
                    data.announcement?.takeIf {
                        it.interaction?.takeIf { i -> i.type == InteractionType.COMMAND } === null
                    }?.delete()?.queue(null) {}

                    player.playTrack(track.makeClone().apply { userData = data })
                }
                LoopMode.QUEUE -> {
                    queue.offer(track.makeClone().apply { userData = track.userData })

                    data.announcement?.takeIf {
                        it.interaction?.takeIf { i -> i.type == InteractionType.COMMAND } === null
                    }?.delete()?.queue(null) {}

                    nextTrack()
                }
                else -> {
                    data.announcement?.takeIf {
                        it.interaction?.takeIf { i -> i.type == InteractionType.COMMAND } === null
                    }?.delete()?.queue(null) {}

                    nextTrack()
                }
            }
        }

        if (data.channel?.guild?.selfMember?.voiceState?.inAudioChannel() == true) {
            if (player.playingTrack === null) {
                CoroutineScope(MusicContext).launch {
                    val deferred = CompletableDeferred<Unit>()

                    val listener = object : AudioEventAdapter() {
                        override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
                            player.removeListener(this)

                            deferred.complete(Unit)
                        }
                    }

                    player.addListener(listener)

                    try {
                        withTimeout((5).minutes) { deferred.await() }
                    } catch (_: TimeoutCancellationException) {
                        player.removeListener(listener)

                        data.channel.guild.audioPlayer?.stop()

                        if (data.channel.guild.selfMember.voiceState?.inAudioChannel() == true) {
                            data.channel.guild.audioManager.closeAudioConnection()

                            data.channel.sendWarning(
                                "${data.channel.jda.selfUser.name} has been inactive " +
                                        "for too long to stay in the voice channel! The bot has left!"
                            ) { text = "This message will self-delete in a minute" }.queue({
                                it.delete().queueAfter(1, TimeUnit.MINUTES, null) {}
                            }) {}
                        }
                    }
                }
            }
        }
    }
}