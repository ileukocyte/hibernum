package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.canJoinFromAnother
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class JoinCommand : TextCommand {
    override val name = "join"
    override val description = "Makes the bot join your voice channel"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        event.member?.voiceState?.channel?.let {
            if (it == event.guild.selfMember.voiceState?.channel) {
                throw CommandException("${event.jda.selfUser.name} is already connected to the voice channel!")
            }

            if (!it.canJoinFromAnother(event.member ?: return)) {
                throw CommandException("The bot cannot leave another voice channel " +
                        "unless it is playing no track, you have the permission to move members, " +
                        "or the current voice channel is empty!")
            }

            event.guild.audioManager.openAudioConnection(it)

            event.channel.sendSuccess("Joined the voice channel!").queue()
        } ?: throw CommandException("You are not connected to a voice channel!")
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        event.member?.voiceState?.channel?.let {
            if (it == event.guild?.selfMember?.voiceState?.channel) {
                throw CommandException("${event.jda.selfUser.name} is already connected to the voice channel!")
            }

            if (!it.canJoinFromAnother(event.member ?: return)) {
                throw CommandException("The bot cannot leave another voice channel " +
                        "unless it is playing no track, you have the permission to move members, " +
                        "or the current voice channel is empty!")
            }

            event.guild?.audioManager?.openAudioConnection(it)

            event.replySuccess("Joined the voice channel!")
                .setEphemeral(true)
                .queue()
        } ?: throw CommandException("You are not connected to a voice channel!")
    }
}