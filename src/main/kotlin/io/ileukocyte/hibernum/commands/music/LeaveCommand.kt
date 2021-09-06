package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.replyConfirmation
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendConfirmation
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button

class LeaveCommand : Command {
    override val name = "leave"
    override val description = "Makes the bot leave your voice channel"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        event.guild.selfMember.voiceState?.channel?.let { botVc ->
            event.member?.voiceState?.channel?.let {
                if (it == botVc) {
                    if (event.guild.audioPlayer?.player?.playingTrack !== null) {
                        sendLeaveConfirmation(event.author.idLong, event)
                    } else {
                        event.guild.audioPlayer?.stop()
                        event.guild.audioManager.closeAudioConnection()

                        event.channel.sendSuccess("Left the voice channel!").queue()
                    }
                } else throw CommandException("You are not connected to the required voice channel!")
            } ?: throw CommandException("You are not connected to a voice channel!")
        } ?: throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        event.guild?.selfMember?.voiceState?.channel?.let { botVc ->
            event.member?.voiceState?.channel?.let {
                if (it == botVc) {
                    if (event.guild?.audioPlayer?.player?.playingTrack !== null) {
                        sendLeaveConfirmation(event.user.idLong, event)
                    } else {
                        event.guild?.audioPlayer?.stop()
                        event.guild?.audioManager?.closeAudioConnection()

                        event.replySuccess("Left the voice channel!")
                            .setEphemeral(true)
                            .queue()
                    }
                } else throw CommandException("You are not connected to the required voice channel!")
            } ?: throw CommandException("You are not connected to a voice channel!")
        } ?: throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "leave" -> {
                    event.guild?.audioPlayer?.stop()
                    event.guild?.audioManager?.closeAudioConnection()

                    event.replySuccess("Left the voice channel!")
                        .setEphemeral(true)
                        .flatMap { event.message.delete() }
                        .queue()
                }
                "exit" -> event.message.delete().queue {
                    event.replySuccess("Successfully canceled!").setEphemeral(true).queue()
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private fun <E : Event> sendLeaveConfirmation(userId: Long, event: E) {
        val description = "Are you sure you want the bot to stop playing music and leave the channel?"
        val buttons = setOf(
            Button.danger("$name-$userId-leave", "Yes"),
            Button.secondary("$name-$userId-exit", "No")
        )

        val restAction = when (event) {
            is GuildMessageReceivedEvent ->
                event.channel.sendConfirmation(description).setActionRow(buttons)
            is SlashCommandEvent ->
                event.replyConfirmation(description).addActionRow(buttons)
            else -> null // must never occur
        }

        restAction?.queue()
    }
}