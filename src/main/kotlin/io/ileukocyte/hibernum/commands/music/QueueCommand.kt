package io.ileukocyte.hibernum.commands.music

import com.google.common.collect.Lists

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.GuildMusicManager
import io.ileukocyte.hibernum.audio.TrackUserData
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.getEmbedProgressBar
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.utils.asDuration

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Button

import kotlin.math.max
import kotlin.math.min

class QueueCommand : Command {
    override val name = "queue"
    override val description = "Shows the current playlist"
    override val aliases = setOf("q", "playlist")
    override val usages = setOf("page (optional)")
    override val options = setOf(
        OptionData(OptionType.INTEGER, "page", "Initial page number")
    )

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val initialPage = args?.toIntOrNull()
            ?.takeIf { it in 1..Lists.partition(audioPlayer.scheduler.queue.toList(), 7).size }
            ?.let { it - 1 }
            ?: 0

        event.channel.sendMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .let { it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                ?.setActionRow(pageButtons(event.author.id, initialPage))
                ?: it
            }.queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: throw CommandException()
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val initialPage = event.getOption("page")?.asString?.toIntOrNull()
            ?.takeIf { it in 1..Lists.partition(audioPlayer.scheduler.queue.toList(), 7).size }
            ?.let { it - 1 }
            ?: 0

        event.replyEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .let { it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                ?.addActionRow(pageButtons(event.user.id, initialPage))
                ?: it
            }.queue()
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val audioPlayer = event.guild?.audioPlayer ?: return
            val track = audioPlayer.player.playingTrack.let {
                if (it === null) {
                    event.message?.delete()?.queue()
                    return
                } else it
            }

            val pageNumber = id[1].toInt()

            when (id.last()) {
                "first" -> {
                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, 0)
                    ).setActionRows().let {
                        it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                            ?.setActionRow(pageButtons(id.first(), 0))
                            ?: it
                    }.queue()
                }
                "last" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 7)
                    val lastPage = partition.lastIndex

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, lastPage)
                    ).setActionRows().let {
                        it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                            ?.setActionRow(pageButtons(id.first(), lastPage))
                            ?: it
                    }.queue()
                }
                "back" -> {
                    val newPage = max(0, pageNumber - 1)

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, newPage)
                    ).setActionRows().let {
                        it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                            ?.setActionRow(pageButtons(id.first(), newPage))
                            ?: it
                    }.queue()
                }
                "next" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 7)
                    val lastPage = partition.lastIndex
                    val newPage = min(pageNumber + 1, lastPage)

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, newPage)
                    ).setActionRows().let {
                        it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                            ?.setActionRow(pageButtons(id.first(), newPage))
                            ?: it
                    }.queue()
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private fun pageButtons(userId: String, page: Int) = setOf(
        Button.secondary("$name-$userId-$page-first", "First Page"),
        Button.secondary("$name-$userId-$page-back", "Back"),
        Button.secondary("$name-$userId-$page-next", "Next"),
        Button.secondary("$name-$userId-$page-last", "Last Page")
    )

    private fun queueEmbed(jda: JDA, musicManager: GuildMusicManager, track: AudioTrack, page: Int) = buildEmbed {
        color = Immutable.SUCCESS

        author {
            name = "HiberPlayer"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        if (musicManager.scheduler.queue.isNotEmpty()) {
            val partition = Lists.partition(musicManager.scheduler.queue.toList(), 7)

            field {
                title = "Queue"
                description = buildString {
                    partition[page].filterNotNull().forEachIndexed { i, t ->
                        val userData = track.userData as TrackUserData

                        val trackTitle = "[${t.info.title.take(34).let { if (it.length == 34) "$it\u2026" else it }}]" +
                                "(${t.info.uri})"
                        val trackDuration = if (t.info.isStream) "(LIVE)" else asDuration(t.duration)

                        appendLine("${i + 1 + page * 7}. $trackTitle ($trackDuration, ${userData.user.asMention})")
                    }
                }
            }

            footer {
                text = "Total songs: ${musicManager.scheduler.queue.size + 1}"
                text += " \u2022 Page: ${page + 1}/${partition.size}".takeIf { partition.size > 1 }.orEmpty()
            }
        }

        field {
            val userData = track.userData as TrackUserData

            val timeline = getEmbedProgressBar(
                track.position.takeUnless { track.info.isStream } ?: Long.MAX_VALUE,
                track.duration
            )

            title = "Playing Now"
            description = "**[${track.info.title}](${track.info.uri})** (${userData.user.asMention})\n" +
                    "$timeline " + ("(${asDuration(track.position)}/${asDuration(track.duration)})"
                .takeUnless { track.info.isStream } ?: "(LIVE)")
        }

        field {
            title = "Looping Mode"
            description = musicManager.scheduler.loopMode.toString()
            isInline = true
        }

        field {
            title = "Volume"
            description = "${musicManager.player.volume}%"
            isInline = true
        }
    }
}