package io.ileukocyte.hibernum.commands.music

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.audio.*
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleInteractionHandling
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replyConfirmation
import io.ileukocyte.hibernum.utils.asDuration

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.InteractionType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class NowPlayingCommand : TextCommand {
    override val name = "nowplaying"
    override val description = "Shows information about the track that is currently playing"
    override val options = setOf(
        OptionData(OptionType.BOOLEAN, "gui", "Whether any graphic buttons should " +
                "be added to the player (default is true)"))
    override val aliases = setOf("np", "now", "playing", "playing-now")
    override val staleInteractionHandling = StaleInteractionHandling.REMOVE_COMPONENTS

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack
            ?: throw CommandException("No track is currently playing!")

        val buttons = mutableSetOf(
            Button.primary("$name-${event.author.idLong}-update", "Update"),
            Button.secondary(
                "$name-${event.author.idLong}-playpause",
                "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
            ),
            Button.secondary("$name-${event.author.idLong}-stop", "Stop"),
        )

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            buttons += Button.secondary("$name-${event.author.idLong}-skip", "Skip")
        }

        buttons += Button.danger("$name-${event.author.idLong}-exit", "Close")

        event.channel.sendMessageEmbeds(playingEmbed(event.jda, audioPlayer, track))
            .setActionRow(buttons)
            .queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return
        val track = audioPlayer.player.playingTrack
            ?: throw CommandException("No track is currently playing!")

        val gui = event.getOption("gui")?.asBoolean ?: true

        val buttons = mutableSetOf(
            Button.primary("$name-${event.user.idLong}-update", "Update"),
            Button.secondary(
                "$name-${event.user.idLong}-playpause",
                "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
            ),
            Button.secondary("$name-${event.user.idLong}-stop", "Stop"),
        )

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            buttons += Button.secondary("$name-${event.user.idLong}-skip", "Skip")
        }

        buttons += Button.danger("$name-${event.user.idLong}-exit", "Close")

        event.replyEmbeds(playingEmbed(event.jda, audioPlayer, track))
            .applyIf(gui) { addActionRow(buttons) }
            .queue()
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val audioPlayer = event.guild?.audioPlayer

            if (id.last() == "exit" || audioPlayer?.player?.playingTrack === null) {
                event.editComponents().queue(null) {}

                return
            }

            fun updatePlayer() {
                val buttons = mutableSetOf(
                    Button.primary("$name-${event.user.idLong}-update", "Update"),
                    Button.secondary(
                        "$name-${event.user.idLong}-playpause",
                        "Pause".applyIf(audioPlayer.player.isPaused) { "Play" },
                    ),
                    Button.secondary("$name-${event.user.idLong}-stop", "Stop"),
                )

                if (audioPlayer.scheduler.queue.isNotEmpty()) {
                    buttons += Button.secondary("$name-${event.user.idLong}-skip", "Skip")
                }

                buttons += Button.danger("$name-${event.user.idLong}-exit", "Close")

                event.editMessageEmbeds(playingEmbed(event.jda, audioPlayer, audioPlayer.player.playingTrack))
                    .setActionRow(buttons)
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
                    val description = "Are you sure you want the bot to stop playing music and clear the queue?"
                    val buttons = setOf(
                        Button.danger("stop-${event.user.idLong}-${event.messageIdLong}-stop", "Yes"),
                        Button.secondary("stop-${event.user.idLong}-exit", "No"),
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

                        audioPlayer.scheduler.nextTrack(newAnnouncementChannel = event.channel)

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
            description = "**[${track.info.title
                .replace('[', '(')
                .replace(']', ')')}](${track.info.uri})**"
        }

        field {
            title = "Track Requester"
            description = trackData.user.asMention
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

        field {
            val timeline = getEmbedProgressBar(
                track.position.takeUnless { track.info.isStream } ?: Long.MAX_VALUE,
                track.duration,
            )

            title = "Duration"
            description = "$timeline " + ("(${asDuration(track.position)}/${asDuration(track.duration)})"
                .takeUnless { track.info.isStream } ?: "(LIVE)")
        }

        author {
            name = "HiberPlayer"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }
    }
}