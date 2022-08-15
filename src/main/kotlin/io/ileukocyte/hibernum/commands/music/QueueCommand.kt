package io.ileukocyte.hibernum.commands.music

import com.google.common.collect.Lists

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleComponentHandling
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.music.LoopCommand.Companion.getButton
import io.ileukocyte.hibernum.commands.music.LoopCommand.Companion.getNext
import io.ileukocyte.hibernum.commands.usageGroupOf
import io.ileukocyte.hibernum.extensions.*
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
    override val usages = setOf(usageGroupOf("page".toClassicTextUsage(true)))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "page", "Initial page number"),
        OptionData(OptionType.BOOLEAN, "gui-player", "Whether the button player should " +
                "be added to the queue message (default is true)"),
    )
    override val staleComponentHandling = StaleComponentHandling.REMOVE_COMPONENTS

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
            Button.secondary(
                "$interactionName-${event.author.idLong}-$initialPage-true-playpause",
                "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
            ),
            Button.secondary("$interactionName-${event.author.idLong}-$initialPage-true-stop", "Stop"),
            audioPlayer.scheduler.loopMode.getButton(interactionName, "${event.author.id}-$initialPage-true"),
        )

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            playerButtons +=
                Button.secondary("$interactionName-${event.author.idLong}-$initialPage-true-skip", "Skip")

            if (audioPlayer.scheduler.queue.size > 1) {
                playerButtons +=
                    Button.secondary("$interactionName-${event.author.idLong}-$initialPage-true-shuffle", "Shuffle")
            }
        }

        actionRows += ActionRow.of(playerButtons)
        actionRows += ActionRow.of(
            Button.primary("$interactionName-${event.author.idLong}-$initialPage-true-update", "Update"),
            Button.danger("$interactionName-${event.author.idLong}-exit", "Close"),
        )

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
                Button.secondary(
                    "$interactionName-${event.user.idLong}-$initialPage-true-playpause",
                    "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
                ),
                Button.secondary("$interactionName-${event.user.idLong}-$initialPage-true-stop", "Stop"),
                audioPlayer.scheduler.loopMode.getButton(interactionName, "${event.user.id}-$initialPage-true"),
            )

            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                playerButtons +=
                    Button.secondary("$interactionName-${event.user.idLong}-$initialPage-true-skip", "Skip")

                if (audioPlayer.scheduler.queue.size > 1) {
                    playerButtons +=
                        Button.secondary("$interactionName-${event.user.idLong}-$initialPage-true-shuffle", "Shuffle")
                }
            }

            actionRows += ActionRow.of(playerButtons)
        }

        actionRows += ActionRow.of(
            Button.primary("$interactionName-${event.user.idLong}-$initialPage-$addGui-update", "Update"),
            Button.danger("$interactionName-${event.user.idLong}-exit", "Close"),
        )

        event.replyEmbeds(queueEmbed(event.jda, audioPlayer, track, initialPage))
            .setComponents(actionRows)
            .queue()
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

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
                        Button.secondary(
                            "$interactionName-${event.user.idLong}-$initialPage-true-playpause",
                            "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
                        ),
                        Button.secondary("$interactionName-${event.user.idLong}-$initialPage-true-stop", "Stop"),
                        audioPlayer.scheduler.loopMode.getButton(interactionName, "${event.user.id}-$initialPage-true"),
                    )

                    if (audioPlayer.scheduler.queue.isNotEmpty()) {
                        playerButtons +=
                            Button.secondary("$interactionName-${event.user.idLong}-$initialPage-true-skip", "Skip")

                        if (audioPlayer.scheduler.queue.size > 1) {
                            playerButtons += Button
                                .secondary("$interactionName-${event.user.idLong}-$initialPage-true-shuffle", "Shuffle")
                        }
                    }

                    actionRows += ActionRow.of(playerButtons)
                }

                actionRows += ActionRow.of(
                    Button.primary("$interactionName-${event.user.idLong}-$initialPage-$addGui-update", "Update"),
                    Button.danger("$interactionName-${event.user.idLong}-exit", "Close"),
                )

                return actionRows
            }

            when (id.last()) {
                "first" -> {
                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, 0))
                        .setComponents(getUpdatedButtons(0))
                        .queue(null) {
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, 0))
                                .setComponents(getUpdatedButtons(0))
                                .queue()
                        }
                }
                "last" -> {
                    val lastPage = max(0, pagesCount.dec())

                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, lastPage))
                        .setComponents(getUpdatedButtons(lastPage))
                        .queue(null) {
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, lastPage))
                                .setComponents(getUpdatedButtons(lastPage))
                                .queue()
                        }
                }
                "back" -> {
                    val newPage = min(max(0, pageNumber.dec()), max(0, pagesCount.dec()))

                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                        .setComponents(getUpdatedButtons(newPage))
                        .queue(null) {
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, newPage))
                                .setComponents(getUpdatedButtons(newPage))
                                .queue()
                        }
                }
                "next" -> {
                    val newPage = min(pageNumber.inc(), max(0, pagesCount.dec()))

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
                    val page = min(pageNumber, max(0, pagesCount.dec()))

                    event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                        .setComponents(getUpdatedButtons(page))
                        .queue(null) {
                            event.message
                                .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                                .setComponents(getUpdatedButtons(page))
                                .queue()
                        }
                }
                else -> {
                    if (event.member?.voiceState?.channel != event.guild?.selfMember?.voiceState?.channel) {
                        throw CommandException("You are not connected to the required voice channel!")
                    }

                    when (id.last()) {
                        "playpause" -> {
                            audioPlayer.player.isPaused = !audioPlayer.player.isPaused

                            val page = min(pageNumber, max(0, pagesCount.dec()))

                            event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                                .setComponents(getUpdatedButtons(page))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                                        .setComponents(getUpdatedButtons(page))
                                        .queue()
                                }
                        }
                        "shuffle" -> {
                            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                                audioPlayer.scheduler.shuffle()
                            }

                            val page = min(pageNumber, max(0, pagesCount.dec()))

                            event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                                .setComponents(getUpdatedButtons(page))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                                        .setComponents(getUpdatedButtons(page))
                                        .queue()
                                }
                        }
                        "loop" -> {
                            audioPlayer.scheduler.loopMode = audioPlayer.scheduler.loopMode
                                .getNext(audioPlayer.scheduler.queue.isEmpty())

                            val page = min(pageNumber, max(0, pagesCount.dec()))

                            event.editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                                .setComponents(getUpdatedButtons(page))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(queueEmbed(event.jda, audioPlayer, track, page))
                                        .setComponents(getUpdatedButtons(page))
                                        .queue()
                                }
                        }
                        "stop" -> {
                            val stop = CommandHandler["stop"].cast<StopCommand>().interactionName

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

                            val page = min(pageNumber, max(0, pagesCount.dec()))

                            val embed =
                                queueEmbed(event.jda, audioPlayer, audioPlayer.player.playingTrack, page)

                            event.editMessageEmbeds(embed)
                                .setComponents(getUpdatedButtons(page))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(embed)
                                        .setComponents(getUpdatedButtons(page))
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
        Button.secondary("$interactionName-$userId-$page-$gui-first", "First Page")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$interactionName-$userId-$page-$gui-back", "Back")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$interactionName-$userId-$page-$gui-next", "Next")
            .applyIf(page == size.dec()) { asDisabled() },
        Button.secondary("$interactionName-$userId-$page-$gui-last", "Last Page")
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
                        "LIVE"
                    } else {
                        asDuration(t.duration)
                    }

                    appendLine("${(i.inc() + page * 10).toDecimalFormat("#,###")}. " +
                            "$trackTitle ($trackDuration, ${userData.requester?.asMention ?: "unknown requester"})")
                }
            }

            footer {
                text = "Total Songs: ${musicManager.scheduler.queue.size.inc().toDecimalFormat("#,###")}" +
                        " \u2022 Page: ${page.inc().toDecimalFormat("#,###")}/${partition.size.toDecimalFormat("#,###")}"
                            .takeIf { partition.size > 1 }.orEmpty()
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
            ).bold()} (${userData.requester?.asMention ?: "unknown requester"})\n" +
                    "$timeline " + ("(${asDuration(track.position)}/${asDuration(track.duration)})"
                .takeUnless { track.info.isStream } ?: "(LIVE)")
        }

        if (queueIsNotEmpty) {
            field {
                val tracks = musicManager.scheduler.queue + track

                title = "Total Duration"
                description = asDuration(tracks.filter { !it.info.isStream }.sumOf { it.duration })
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