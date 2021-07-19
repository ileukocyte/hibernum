package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class LeaveCommand : Command {
    override val name = "leave"
    override val description = "Makes the bot leave your voice channel"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        event.guild.selfMember.voiceState?.channel?.let { botVc ->
            event.member?.voiceState?.channel?.let {
                if (it == botVc) {
                    event.guild.audioPlayer?.stop()
                    event.guild.audioManager.closeAudioConnection()

                    event.channel.sendSuccess("Left the voice channel!").queue()
                } else throw CommandException("You are not connected to the required voice channel!")
            } ?: throw CommandException("You are not connected to a voice channel!")
        } ?: throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        event.guild?.selfMember?.voiceState?.channel?.let { botVc ->
            event.member?.voiceState?.channel?.let {
                if (it == botVc) {
                    event.guild?.audioPlayer?.stop()
                    event.guild?.audioManager?.closeAudioConnection()

                    event.replySuccess("Left the voice channel!")
                        .setEphemeral(true)
                        .queue()
                } else throw CommandException("You are not connected to the required voice channel!")
            } ?: throw CommandException("You are not connected to a voice channel!")
        } ?: throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }
}