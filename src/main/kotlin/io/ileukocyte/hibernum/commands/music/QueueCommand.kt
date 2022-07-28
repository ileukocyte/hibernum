package io.ileukocyte.hibernum.commands.music

import com.google.common.collect.Lists

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleInteractionHandling
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.music.LoopCommand.Companion.getButton
import io.ileukocyte.hibernum.commands.music.LoopCommand.Companion.getNext
import io.ileukocyte.hibernum.extensions.bold
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.maskedLink
import io.ileukocyte.hibernum.extensions.replyConfirmation
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asDuration

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.InteractionType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

import org.jetbrains.kotlin.utils.addToStdlib.applyIf
import org.jetbrains.kotlin.utils.addToStdlib.cast

class QueueCommand : TextCommand {
    override val name = "queue"
    override val description = "Shows the current playlist"
    override val aliases = setOf("q", "playlist")
    override val usages = setOf(setOf("page".toClassicTextUsage(true)))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "page", "Initial page number"),
        OptionData(OptionType.BOOLEAN, "gui-player", "Whether the button player should " +
                "be added to the queue message (default is true)"),
    )
    override val staleInteractionHandling = StaleInteractionHandling.REMOVE_COMPONENTS

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val partitionSize = ceil(audioPlayer.scheduler.queue.size / 10.0).toInt()
        val initialPage = args?.toIntOrNull()
            ?.takeIf { it in 1..partitionSize }
            ?.dec()
            ?: 0

        val actionRows = mutableSetOf<ActionRow>()

        if (audioPlayer.scheduler.queue.size > 10) {
            actionRows += ActionRow.of(pageButtons(event.author.id, initialPage, partitionSize, true))
        }

        val playerButtons = mutableSetOf(
            Button.primary("$name-${event.author.idLong}-$initialPage-true-update", "Update"),
            Button.secondary("$name-${event.author.idLong}-$initialPage-true-stop", "Stop"),
            audioPlayer.scheduler.loopMode.getButton(name, "${event.author.id}-$initialPage-true"),
        )

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            playerButtons +=
                Button.secondary("$name-${event.author.idLong}-$initialPage-true-skip", "Skip")

            if (audioPlayer.scheduler.queue.size > 1) {
                playerButtons +=
                    Button.secondary("$name-${event.author.idLong}-$initialPage-true-shuffle", "Shuffle")
            }
        }

        actionRows += ActionRow.of(playerButtons)
        actionRows += ActionRow.of(Button.danger("$name-${event.author.idLong}-exit", "Close"))

        event.channel.sendMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .setComponents(actionRows)
            .queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack ?: throw CommandException("No track is currently playing!")

        val addGui = event.getOption("gui-player")?.asBoolean ?: true

        val partitionSize = ceil(audioPlayer.scheduler.queue.size / 10.0).toInt()

        val initialPage = event.getOption("page")?.asString?.toIntOrNull()
            ?.takeIf { it in 1..partitionSize }
            ?.dec()
            ?: 0

        val actionRows = mutableSetOf<ActionRow>()

        if (audioPlayer.scheduler.queue.size > 10) {
            actionRows += ActionRow.of(pageButtons(event.user.id, initialPage, partitionSize, addGui))
        }

        if (addGui) {
            val playerButtons = mutableSetOf(
                Button.primary("$name-${event.user.idLong}-$initialPage-true-update", "Update"),
                Button.secondary("$name-${event.user.idLong}-$initialPage-true-stop", "Stop"),
                audioPlayer.scheduler.loopMode.getButton(name, "${event.user.id}-$initialPage-true"),
            )

            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                playerButtons +=
                    Button.secondary("$name-${event.user.idLong}-$initialPage-true-skip", "Skip")

                if (audioPlayer.scheduler.queue.size > 1) {
                    playerButtons +=
                        Button.secondary("$name-${event.user.idLong}-$initialPage-true-shuffle", "Shuffle")
                }
            }

            actionRows += ActionRow.of(playerButtons)
        }

        actionRows += ActionRow.of(Button.danger("$name-${event.user.idLong}-exit", "Close"))

        event.replyEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .setComponents(actionRows)
            .queue()
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val audioPlayer = event.guild?.audioPlayer ?: return
            val track = audioPlayer.player.playingTrack

            if (id.last() == "exit" || track === null) {
                event.editComponents().queue()

                return
            }

            val pageNumber = id[1].toInt()
            val pagesCount = ceil(audioPlayer.scheduler.queue.size / 10.0).toInt()

            val addGui = id[2].toBooleanStrict()

            fun getUpdatedButtons(initialPage: Int): Set<ActionRow> {
                val actionRows = mutableSetOf<ActionRow>()

                if (audioPlayer.scheduler.queue.size > 10) {
                    actionRows += ActionRow.of(pageButtons(event.user.id, initialPage, pagesCount, addGui))
                }

                if (addGui) {
                    val playerButtons = mutableSetOf(
                        Button.primary("$name-${event.user.idLong}-$initialPage-true-update", "Update"),
                        Button.secondary("$name-${event.user.idLong}-$initialPage-true-stop", "Stop"),
                        audioPlayer.scheduler.loopMode.getButton(name, "${event.user.id}-$initialPage-true"),
                    )

                    if (audioPlayer.scheduler.queue.isNotEmpty()) {
                        playerButtons +=
                            Button.secondary("$name-${event.user.idLong}-$initialPage-true-skip", "Skip")

                        if (audioPlayer.scheduler.queue.size > 1) {
                            playerButtons += Button
                                .secondary("$name-${event.user.idLong}-$initialPage-true-shuffle", "Shuffle")
                        }
                    }

                    actionRows += ActionRow.of(playerButtons)
                }

                actionRows += ActionRow.of(Button.danger("$name-${event.user.idLong}-exit", "Close"))

                return actionRows
            }

            when (id.last()) {
                "first" -> {
                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, 0)
                    ).setComponents(getUpdatedButtons(0)).queue(null) {
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, 0)
                        ).setComponents(getUpdatedButtons(0)).queue()
                    }
                }
                "last" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 10)
                    val lastPage = partition.lastIndex

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, lastPage)
                    ).setComponents(getUpdatedButtons(lastPage)).queue(null) {
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, lastPage)
                        ).setComponents(getUpdatedButtons(lastPage)).queue()
                    }
                }
                "back" -> {
                    val newPage = max(0, pageNumber.dec())

                    event.editMessageEmbeds(
                        queueEmbed(event.jda, audioPlayer, track, newPage)
                    ).setComponents(getUpdatedButtons(newPage)).queue(null) {
                        event.message.editMessageEmbeds(
                            queueEmbed(event.jda, audioPlayer, track, newPage)
                        ).setComponents(getUpdatedButtons(newPage)).queue()
                    }
                }
                "next" -> {
                    val partition = Lists.partition(audioPlayer.scheduler.queue.toList(), 10)
                    val lastPage = partition.lastIndex
                    val newPage = min(pageNumber.inc(), lastPage)

                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                        .setComponents(getUpdatedButtons(newPage))
                        .queue(null) {
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                                .setComponents(getUpdatedButtons(newPage))
                                .queue()
                        }
                }
                "update" -> {
                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, pageNumber))
                        .setComponents(getUpdatedButtons(pageNumber))
                        .queue(null) {
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, pageNumber))
                                .setComponents(getUpdatedButtons(pageNumber))
                                .queue()
                        }
                }
                else -> {
                    if (event.member?.voiceState?.channel != event.guild?.selfMember?.voiceState?.channel) {
                        throw CommandException("You are not connected to the required voice channel!")
                    }

                    when (id.last()) {
                        "shuffle" -> {
                            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                                audioPlayer.scheduler.shuffle()
                            }

                            event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, pageNumber))
                                .setComponents(getUpdatedButtons(pageNumber))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, pageNumber))
                                        .setComponents(getUpdatedButtons(pageNumber))
                                        .queue()
                                }
                        }
                        "loop" -> {
                            audioPlayer.scheduler.loopMode = audioPlayer.scheduler.loopMode
                                .getNext(audioPlayer.scheduler.queue.isEmpty())

                            event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, pageNumber))
                                .setComponents(getUpdatedButtons(pageNumber))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, pageNumber))
                                        .setComponents(getUpdatedButtons(pageNumber))
                                        .queue()
                                }
                        }
                        "stop" -> {
                            val stop = CommandHandler["stop"].cast<StopCommand>().name

                            val description = "Are you sure you want the bot to stop playing music and clear the queue?"
                            val buttons = setOf(
                                Button.danger("$stop-${event.user.idLong}-${event.messageIdLong}-stop", "Yes"),
                                Button.secondary("$stop-${event.user.idLong}-exit", "No"),
                            )

                            event.replyConfirmation(description).addActionRow(buttons).queue()
                        }
                        "skip" -> {
                            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                                val announcement = audioPlayer.player.playingTrack.customUserData.announcement

                                announcement?.takeUnless {
                                    val interaction = it.interaction?.takeIf { i -> i.type == InteractionType.COMMAND }

                                    interaction !== null && interaction.name != "skip"
                                }?.delete()?.queue(null) {}

                                audioPlayer.scheduler.nextTrack(newAnnouncementChannel = event.guildChannel)

                                if (audioPlayer.player.playingTrack === null) {
                                    event.editComponents().queue(null) {}

                                    return
                                }
                            }

                            val embed = queueEmbed(event.jda, audioPlayer, audioPlayer.player.playingTrack, pageNumber)

                            event.editMessageEmbeds(embed)
                                .setComponents(getUpdatedButtons(pageNumber))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(embed)
                                        .setComponents(getUpdatedButtons(pageNumber))
                                        .queue()
                                }
                        }
                    }
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private fun pageButtons(userId: String, page: Int, size: Int, gui: Boolean) = setOf(
        Button.secondary("$name-$userId-$page-$gui-first", "First Page")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$name-$userId-$page-$gui-back", "Back")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$name-$userId-$page-$gui-next", "Next")
            .applyIf(page == size.dec()) { asDisabled() },
        Button.secondary("$name-$userId-$page-$gui-last", "Last Page")
            .applyIf(page == size.dec()) { asDisabled() },
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