package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleComponentHandling
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.music.LoopCommand.Companion.getButton
import io.ileukocyte.hibernum.commands.music.LoopCommand.Companion.getNext
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asDuration

import kotlin.math.absoluteValue

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

class NowPlayingCommand : TextCommand {
    override val name = "nowplaying"
    override val description = "Shows information about the track that is currently playing"
    override val options = setOf(
        OptionData(OptionType.BOOLEAN, "gui-player", "Whether the button player should " +
                "be added to the bot message (default is true)"))
    override val aliases = setOf("np", "now", "playing", "playing-now")
    override val staleComponentHandling = StaleComponentHandling.EXECUTE_COMMAND

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack
            ?: throw CommandException("No track is currently playing!")

        val buttons = mutableSetOf(
            Button.secondary(
                "$interactionName-${event.author.idLong}-true-playpause",
                "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
            ),
            Button.secondary("$interactionName-${event.author.idLong}-true-stop", "Stop"),
            audioPlayer.scheduler.loopMode.getButton(interactionName, "${event.author.id}-true"),
        )

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            buttons += Button.secondary("$interactionName-${event.author.idLong}-true-skip", "Skip")
        }

        event.channel.sendMessageEmbeds(playingEmbed(event.jda, audioPlayer, track))
            .setComponents(
                ActionRow.of(buttons),
                ActionRow.of(
                    Button.primary("$interactionName-${event.author.idLong}-true-update", "Update"),
                    Button.danger("$interactionName-${event.author.idLong}-true-exit", "Close"),
                ),
            ).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack
            ?: throw CommandException("No track is currently playing!")

        val addGui = event.getOption("gui-player")?.asBoolean ?: true

        val actionRows = mutableSetOf<ActionRow>()

        if (addGui) {
            val buttons = mutableSetOf(
                Button.secondary(
                    "$interactionName-${event.user.idLong}-true-playpause",
                    "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
                ),
                Button.secondary("$interactionName-${event.user.idLong}-true-stop", "Stop"),
                audioPlayer.scheduler.loopMode.getButton(interactionName, "${event.user.id}-true"),
            )

            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                buttons += Button.secondary("$interactionName-${event.user.idLong}-true-skip", "Skip")
            }

            actionRows += ActionRow.of(buttons)
        }

        actionRows += ActionRow.of(
            Button.primary("$interactionName-${event.user.idLong}-$addGui-update", "Update"),
            Button.danger("$interactionName-${event.user.idLong}-exit", "Close"),
        )

        event.replyEmbeds(playingEmbed(event.jda, audioPlayer, track))
            .setComponents(actionRows)
            .queue()
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            val audioPlayer = event.guild?.audioPlayer

            if (id.last() == "exit" || audioPlayer?.player?.playingTrack === null) {
                event.editComponents().queue(null) {}

                return
            }

            val addGui = id[1].toBooleanStrict()

            fun updatePlayer() {
                val actionRows = mutableSetOf<ActionRow>()

                if (addGui) {
                    val buttons = mutableSetOf(
                        Button.secondary(
                            "$interactionName-${event.user.idLong}-true-playpause",
                            "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
                        ),
                        Button.secondary("$interactionName-${event.user.idLong}-true-stop", "Stop"),
                        audioPlayer.scheduler.loopMode.getButton(interactionName, "${event.user.id}-true"),
                    )

                    if (audioPlayer.scheduler.queue.isNotEmpty()) {
                        buttons += Button.secondary("$interactionName-${event.user.idLong}-true-skip", "Skip")
                    }

                    actionRows += ActionRow.of(buttons)
                }

                actionRows += ActionRow.of(
                    Button.primary("$interactionName-${event.user.idLong}-$addGui-update", "Update"),
                    Button.danger("$interactionName-${event.user.idLong}-exit", "Close"),
                )

                event.editMessageEmbeds(playingEmbed(event.jda, audioPlayer, audioPlayer.player.playingTrack))
                    .setComponents(actionRows)
                    .queue(null) {}
            }

            if (event.member?.voiceState?.channel != event.guild?.selfMember?.voiceState?.channel
                    && id.last() != "update") {
                throw CommandException("You are not connected to the required voice channel!")
            }

            when (id.last()) {
                "update" -> updatePlayer()
                "playpause" -> {
                    audioPlayer.player.isPaused = !audioPlayer.player.isPaused

                    updatePlayer()
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
                "loop" -> {
                    audioPlayer.scheduler.loopMode = audioPlayer.scheduler.loopMode
                        .getNext(audioPlayer.scheduler.queue.isEmpty())

                    updatePlayer()
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

                    updatePlayer()
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private fun playingEmbed(
        jda: JDA,
        musicManager: GuildMusicManager,
        track: AudioTrack,
    ) = buildEmbed {
        val trackData = track.customUserData

        color = Immutable.SUCCESS
        thumbnail = trackData.thumbnail ?: jda.selfUser.effectiveAvatarUrl

        field {
            title = "Playing Now"
            description = track.info.uri.maskedLink(
                track.info.title
                    .replace('[', '(')
                    .replace(']', ')')
            ).bold()
        }

        field {
            title = "Track Requester"
            description = trackData.requester?.asMention ?: "Unknown"
        }

        field {
            title = "Volume"
            description = "${musicManager.player.volume}%"
            isInline = true
        }

        field {
            title = "Pitch Offset"
            description = musicManager.scheduler.pitchOffset.get().let {
                "${it.toDecimalFormat("+0.##;-0.##")} semitone"
                    .singularOrPlural(it.absoluteValue)
                    .applyIf(it == 0.0) { drop(1) }
            }
            isInline = true
        }

        field {
            title = "Speed Rate"
            description = musicManager.scheduler.speedRate.get().toDecimalFormat("0.##x")
            isInline = true
        }

        field {
            title = "Looping Mode"
            description = musicManager.scheduler.loopMode.toString()
            isInline = true
        }

        field {
            val timeline = getEmbedProgressBar(
                track.position.takeUnless { track.info.isStream } ?: Long.MAX_VALUE,
                track.duration,
            )

            title = "Duration"
            description = "$timeline " + ("(${asDuration(track.position)}/${asDuration(track.duration)})"
                .takeUnless { track.info.isStream } ?: "(LIVE)")
        }

        if (musicManager.scheduler.queue.isNotEmpty()) {
            val next = musicManager.scheduler.queue.first()

            field {
                val userData = next.customUserData

                val trackTitle = next.info.uri.maskedLink(
                    next.info.title
                        .limitTo(256)
                        .replace('[', '(')
                        .replace(']', ')')
                )

                val trackDuration = if (next.info.isStream) {
                    "LIVE"
                } else {
                    asDuration(next.duration)
                }

                title = "Next Song"
                description = "$trackTitle ($trackDuration, ${userData.requester?.asMention ?: "unknown requester"})"
            }
        }

        author {
            name = "HiberPlayer"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }
    }
}