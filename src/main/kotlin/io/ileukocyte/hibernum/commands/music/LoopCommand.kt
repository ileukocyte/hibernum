package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.LoopMode
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class LoopCommand : TextCommand {
    override val name = "loop"
    override val description = "Sets a repeating mode for the music player"
    override val options = setOf(
        OptionData(OptionType.STRING, "mode", "The repeating mode to set")
            .addChoices(LoopMode.values().map {
                Choice(it.toString().removeSuffix("d"), it.name)
            }),
    )
    override val aliases = setOf("repeat")

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                val buttons = LoopMode.values()
                    .filter { it != audioPlayer.scheduler.loopMode }
                    .map {
                        Button.secondary(
                            "$name-${event.author.idLong}-${it.name}",
                            it.toString().removeSuffix("d"),
                        )
                    }

                event.channel.sendConfirmation("Choose a repeating mode to set!")
                    .setActionRow(
                        *buttons.toTypedArray(),
                        Button.danger("$name-${event.author.idLong}-exit", "Exit"),
                    ).queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val audioPlayer = event.guild?.audioPlayer ?: return

        if (audioPlayer.player.playingTrack !== null) {
            if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                val option = event.getOption("mode")?.asString

                if (option !== null) {
                    val currentMode = audioPlayer.scheduler.loopMode
                    val newMode = LoopMode.valueOf(option)

                    audioPlayer.scheduler.loopMode = newMode

                    val description = "$newMode looping has been enabled!".takeUnless { newMode == LoopMode.DISABLED }
                        ?: "$currentMode looping has been disabled!".applyIf(currentMode == LoopMode.DISABLED) {
                            removePrefix("$currentMode ").replaceFirstChar { it.uppercase() }
                        }

                    event.replySuccess(description).queue()
                } else {
                    val buttons = LoopMode.values()
                        .filter { it != audioPlayer.scheduler.loopMode }
                        .map {
                            Button.secondary(
                                "$name-${event.user.idLong}-${it.name}",
                                it.toString().removeSuffix("d"),
                            )
                        }

                    event.replyConfirmation("Choose a repeating mode to set!")
                        .addActionRow(
                            *buttons.toTypedArray(),
                            Button.danger("$name-${event.user.idLong}-exit", "Exit"),
                        ).queue()
                }
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("No track is currently playing!")
        }
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            if (id.last() == "exit") {
                event.message.delete().queue()

                return
            }

            val currentMode = event.guild?.audioPlayer?.scheduler?.loopMode ?: return
            val newMode = LoopMode.valueOf(id.last())

            event.guild?.audioPlayer?.scheduler?.loopMode = newMode

            val description = "$newMode looping has been enabled!".takeUnless { newMode == LoopMode.DISABLED }
                ?: "$currentMode looping has been disabled!"

            event.message.editMessageEmbeds(defaultEmbed(description, EmbedType.SUCCESS))
                .setComponents(emptyList())
                .queue(null) {
                    event.messageChannel.sendSuccess(description).queue()
                }
        }
    }

    companion object {
        fun LoopMode.getButton(command: String, userId: String) =
            "$command-$userId-loop".let {
                when (this) {
                    LoopMode.SONG -> Button.secondary(it, Emoji.fromUnicode("\uD83D\uDD02"))
                    LoopMode.QUEUE -> Button.secondary(it, Emoji.fromUnicode("\uD83D\uDD01"))
                    LoopMode.DISABLED -> Button.secondary(it, "Repeat")
                }
            }

        fun LoopMode.getNext(isQueueEmpty: Boolean) = when (this) {
            LoopMode.SONG -> if (isQueueEmpty) {
                LoopMode.DISABLED
            } else {
                LoopMode.QUEUE
            }
            LoopMode.QUEUE -> LoopMode.DISABLED
            LoopMode.DISABLED -> LoopMode.SONG
        }
    }
}