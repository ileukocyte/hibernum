package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*

import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button

class LeaveCommand : TextCommand {
    override val name = "leave"
    override val description = "Makes the bot leave your voice channel"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        event.guild.selfMember.voiceState?.channel?.let { botVc ->
            event.member?.voiceState?.channel?.let {
                if (it == botVc) {
                    if (event.guild.audioPlayer?.player?.playingTrack !== null) {
                        val description = "Are you sure you want the bot to stop playing music and leave the channel?"
                        val buttons = setOf(
                            Button.danger("$name-${event.author.idLong}-leave", "Yes"),
                            Button.secondary("$name-${event.author.idLong}-exit", "No"),
                        )

                        event.channel.sendConfirmation(description).setActionRow(buttons).queue()
                    } else {
                        event.guild.audioPlayer?.stop()
                        event.guild.audioManager.closeAudioConnection()

                        event.channel.sendSuccess("Left the voice channel!") {
                            text = "This message will self-delete in 5 seconds"
                        }.queue { r ->
                            r.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                        }
                    }
                } else {
                    throw CommandException("You are not connected to the required voice channel!")
                }
            } ?: throw CommandException("You are not connected to a voice channel!")
        } ?: throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        event.guild?.selfMember?.voiceState?.channel?.let { botVc ->
            event.member?.voiceState?.channel?.let {
                if (it == botVc) {
                    if (event.guild?.audioPlayer?.player?.playingTrack !== null) {
                        val description = "Are you sure you want the bot to stop playing music and leave the channel?"
                        val buttons = setOf(
                            Button.danger("$name-${event.user.idLong}-leave", "Yes"),
                            Button.secondary("$name-${event.user.idLong}-exit", "No"),
                        )

                        event.replyConfirmation(description).addActionRow(buttons).queue()
                    } else {
                        event.guild?.audioPlayer?.stop()
                        event.guild?.audioManager?.closeAudioConnection()

                        event.replySuccess("Left the voice channel!")
                            .setEphemeral(true)
                            .queue()
                    }
                } else {
                    throw CommandException("You are not connected to the required voice channel!")
                }
            } ?: throw CommandException("You are not connected to a voice channel!")
        } ?: throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "leave" -> {
                    val embed = defaultEmbed("Left the voice channel!", EmbedType.SUCCESS) {
                        text = "This message will self-delete in 5 seconds"
                    }

                    event.guild?.audioPlayer?.stop()
                    event.guild?.audioManager?.closeAudioConnection()

                    event.editComponents().setEmbeds(embed).queue({
                        it.deleteOriginal().queueAfter(5, TimeUnit.SECONDS, null) {}
                    }) { _ ->
                        event.channel.sendMessageEmbeds(embed).queue {
                            it.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                        }
                    }
                }
                "exit" -> event.message.delete().queue(null) {}
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }
}