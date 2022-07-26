package io.ileukocyte.hibernum.commands.music

import com.google.common.collect.Lists

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleInteractionHandling
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.bold
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.maskedLink
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

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class QueueCommand : TextCommand {
    override val name = "queue"
    override val description = "Shows the current playlist"
    override val aliases = setOf("q", "playlist")
    override val usages = setOf(setOf("page".toClassicTextUsage(true)))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "page", "Initial page number"))
    override val staleInteractionHandling = StaleInteractionHandling.REMOVE_COMPONENTS

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val partitionSize = ceil(audioPlayer.scheduler.queue.size / 10.0).toInt()
        val initialPage = args?.toIntOrNull()
            ?.takeIf { it in 1..partitionSize }
            ?.dec()
            ?: 0

        event.channel.sendMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .let {
                it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                    setActionRow(pageButtons(event.author.id, initialPage, partitionSize))
                }
            }.queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val partitionSize = ceil(audioPlayer.scheduler.queue.size / 10.0).toInt()

        val initialPage = event.getOption("page")?.asString?.toIntOrNull()
            ?.takeIf { it in 1..partitionSize }
            ?.dec()
            ?: 0

        event.replyEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .let {
                it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                    addActionRow(pageButtons(event.user.id, initialPage, partitionSize))
                }
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
            val track = audioPlayer.player.playingTrack

            if (track === null) {
                event.message.delete().queue()

                return
            }

            val pageNumber = id[1].toInt()
            val pagesCount = ceil(audioPlayer.scheduler.queue.size / 10.0).toInt()

            when (id.last()) {
                "first" -> {
                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, 0)
                    ).setComponents(emptyList()).let {
                        it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                            setActionRow(pageButtons(id.first(), 0, pagesCount))
                        }
                    }.queue(null) { _ ->
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, 0)
                        ).setComponents(emptyList()).let {
                            it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                                setActionRow(pageButtons(id.first(), 0, pagesCount))
                            }
                        }.queue()
                    }
                }
                "last" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 10)
                    val lastPage = partition.lastIndex

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, lastPage)
                    ).setComponents(emptyList()).let {
                        it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                            setActionRow(pageButtons(id.first(), lastPage, pagesCount))
                        }
                    }.queue(null) { _ ->
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, lastPage)
                        ).setComponents(emptyList()).let {
                            it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                                setActionRow(pageButtons(id.first(), lastPage, pagesCount))
                            }
                        }.queue()
                    }
                }
                "back" -> {
                    val newPage = max(0, pageNumber.dec())

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, newPage)
                    ).setComponents(emptyList()).let {
                        it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                            setActionRow(pageButtons(id.first(), newPage, pagesCount))
                        }
                    }.queue(null) { _ ->
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, newPage)
                        ).setComponents(emptyList()).let {
                            it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                                setActionRow(pageButtons(id.first(), newPage, pagesCount))
                            }
                        }.queue()
                    }
                }
                "next" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 10)
                    val lastPage = partition.lastIndex
                    val newPage = min(pageNumber.inc(), lastPage)

                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                        .setComponents(emptyList())
                        .let {
                            it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                                setActionRow(pageButtons(id.first(), newPage, pagesCount))
                            }
                        }.queue(null) { _ ->
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                                .setComponents(emptyList())
                                .let {
                                    it.applyIf(audioPlayer.scheduler.queue.size > 10) {
                                        setActionRow(pageButtons(id.first(), newPage, pagesCount))
                                    }
                                }.queue()
                        }
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private fun pageButtons(userId: String, page: Int, size: Int) = setOf(
        Button.secondary("$name-$userId-$page-first", "First Page")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$name-$userId-$page-back", "Back")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$name-$userId-$page-next", "Next")
            .applyIf(page == size.dec()) { asDisabled() },
        Button.secondary("$name-$userId-$page-last", "Last Page")
            .applyIf(page == size.dec()) { asDisabled() },
        Button.danger("$name-$userId-exit", "Close"),
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
            val partition = Lists.partition(musicManager.scheduler.queue.toList(), 10)

            description = buildString {
                partition[page].forEachIndexed { i, t ->
                    val userData = t.customUserData

                    val trackTitle = t.info.uri.maskedLink(
                        t.info.title
                            .limitTo(32)
                            .replace('[', '(')
                            .replace(']', ')')
                    )

                    val trackDuration = if (t.info.isStream) {
                        "(LIVE)"
                    } else {
                        asDuration(t.duration)
                    }

                    appendLine("${i.inc() + page * 10}. $trackTitle ($trackDuration, ${userData.user.asMention})")
                }
            }

            footer {
                text = "Total Songs: ${musicManager.scheduler.queue.size.inc()}" +
                        " \u2022 Page: ${page.inc()}/${partition.size}".takeIf { partition.size > 1 }.orEmpty()
            }
        }

        field {
            val userData = track.customUserData

            val timeline = getEmbedProgressBar(
                track.position.takeUnless { track.info.isStream } ?: Long.MAX_VALUE,
                track.duration,
            )

            title = "Playing Now"
            description = "${track.info.uri.maskedLink(
                track.info.title
                    .replace('[', '(')
                    .replace(']', ')')
            ).bold()} (${userData.user.asMention})\n" +
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