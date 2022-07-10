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
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.utils.asDuration

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class QueueCommand : Command {
    override val name = "queue"
    override val description = "Shows the current playlist"
    override val aliases = setOf("q", "playlist")
    override val usages = setOf(setOf("page (optional)"))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "page", "Initial page number"))
    override val eliminateStaleInteractions = false

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val partitionSize = Lists.partition(audioPlayer.scheduler.queue.toList(), 7).size
        val initialPage = args?.toIntOrNull()
            ?.takeIf { it in 1..partitionSize }
            ?.let { it - 1 }
            ?: 0

        event.channel.sendMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .let { it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                ?.setActionRow(pageButtons(event.author.id, initialPage, partitionSize))
                ?: it
            }.queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val partitionSize = Lists.partition(audioPlayer.scheduler.queue.toList(), 7).size

        val initialPage = event.getOption("page")?.asString?.toIntOrNull()
            ?.takeIf { it in 1..partitionSize }
            ?.let { it - 1 }
            ?: 0

        event.replyEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .let { it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                ?.addActionRow(pageButtons(event.user.id, initialPage, partitionSize))
                ?: it
            }.queue()
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            if (id.last() == "exit") {
                event.editComponents().queue()

                return
            }

            val audioPlayer = event.guild?.audioPlayer ?: return
            val track = audioPlayer.player.playingTrack.let {
                if (it === null) {
                    event.message.delete().queue()

                    return
                } else it
            }

            val pageNumber = id[1].toInt()
            val pagesCount = ceil(audioPlayer.scheduler.queue.size / 7.0).toInt()

            when (id.last()) {
                "first" -> {
                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, 0)
                    ).setActionRows().let {
                        it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                            ?.setActionRow(pageButtons(id.first(), 0, pagesCount))
                            ?: it
                    }.queue(null) { _ ->
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, 0)
                        ).setActionRows().let {
                            it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                                ?.setActionRow(pageButtons(id.first(), 0, pagesCount))
                                ?: it
                        }.queue()
                    }
                }
                "last" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 7)
                    val lastPage = partition.lastIndex

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, lastPage)
                    ).setActionRows().let {
                        it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                            ?.setActionRow(pageButtons(id.first(), lastPage, pagesCount))
                            ?: it
                    }.queue(null) { _ ->
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, lastPage)
                        ).setActionRows().let {
                            it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                                ?.setActionRow(pageButtons(id.first(), lastPage, pagesCount))
                                ?: it
                        }.queue()
                    }
                }
                "back" -> {
                    val newPage = max(0, pageNumber - 1)

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, newPage)
                    ).setActionRows().let {
                        it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                            ?.setActionRow(pageButtons(id.first(), newPage, pagesCount))
                            ?: it
                    }.queue(null) { _ ->
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, newPage)
                        ).setActionRows().let {
                            it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                                ?.setActionRow(pageButtons(id.first(), newPage, pagesCount))
                                ?: it
                        }.queue()
                    }
                }
                "next" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 7)
                    val lastPage = partition.lastIndex
                    val newPage = min(pageNumber + 1, lastPage)

                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                        .setActionRows()
                        .let {
                            it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                                ?.setActionRow(pageButtons(id.first(), newPage, pagesCount))
                                ?: it
                        }.queue(null) { _ ->
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                                .setActionRows()
                                .let {
                                    it.takeIf { audioPlayer.scheduler.queue.size > 7 }
                                        ?.setActionRow(pageButtons(id.first(), newPage, pagesCount))
                                        ?: it
                                }.queue()
                        }
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private fun pageButtons(userId: String, page: Int, size: Int) = setOf(
        Button.secondary("$name-$userId-$page-first", "First Page")
            .let { if (page == 0) it.asDisabled() else it },
        Button.secondary("$name-$userId-$page-back", "Back")
            .let { if (page == 0) it.asDisabled() else it },
        Button.secondary("$name-$userId-$page-next", "Next")
            .let { if (page == size - 1) it.asDisabled() else it },
        Button.secondary("$name-$userId-$page-last", "Last Page")
            .let { if (page == size - 1) it.asDisabled() else it },
        Button.danger("$name-$userId-exit", "Exit"),
    )

    private fun queueEmbed(
        jda: JDA,
        musicManager: GuildMusicManager,
        track: AudioTrack,
        page: Int,
    ) = buildEmbed {
        val queueIsNotEmpty = musicManager.scheduler.queue.isNotEmpty()

        color = Immutable.SUCCESS

        author {
            name = "HiberPlayer"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        if (queueIsNotEmpty) {
            val partition = Lists.partition(musicManager.scheduler.queue.toList(), 7)

            field {
                title = "Queue"
                description = buildString {
                    partition[page].forEachIndexed { i, t ->
                        val userData = t.userData as TrackUserData

                        val trackTitle = "[${t.info.title.limitTo(35)}]" +
                                "(${t.info.uri})"
                        val trackDuration = if (t.info.isStream) "(LIVE)" else asDuration(t.duration)

                        appendLine("${i + 1 + page * 7}. $trackTitle ($trackDuration, ${userData.user.asMention})")
                    }
                }
            }

            footer {
                text = "Total Songs: ${musicManager.scheduler.queue.size + 1}" +
                        " \u2022 Page: ${page + 1}/${partition.size}".takeIf { partition.size > 1 }.orEmpty()
            }
        }

        field {
            val userData = track.userData as TrackUserData

            val timeline = getEmbedProgressBar(
                track.position.takeUnless { track.info.isStream } ?: Long.MAX_VALUE,
                track.duration,
            )

            title = "Playing Now"
            description = "**[${track.info.title}](${track.info.uri})** (${userData.user.asMention})\n" +
                    "$timeline " + ("(${asDuration(track.position)}/${asDuration(track.duration)})"
                .takeUnless { track.info.isStream } ?: "(LIVE)")
        }

        if (queueIsNotEmpty) {
            field {
                title = "Total Duration"
                description = asDuration(track.duration + musicManager.scheduler.queue.sumOf { it.duration })
                isInline = true
            }
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