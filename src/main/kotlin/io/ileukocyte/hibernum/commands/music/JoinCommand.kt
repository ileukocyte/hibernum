package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class JoinCommand : TextOnlyCommand {
    override val name = "join"
    override val description = "Makes the bot join your voice channel"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        event.member?.voiceState?.channel?.let {
            if (it == event.guild.selfMember.voiceState?.channel) {
                event.channel.sendFailure("${event.jda.selfUser.name} is already connected to the voice channel!").queue()
            } else {
                event.guild.audioManager.openAudioConnection(it)

                event.channel.sendSuccess("Joined the voice channel!").queue()
            }
        } ?: event.channel.sendFailure("You are not connected to a voice channel!").queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        event.member?.voiceState?.channel?.let {
            if (it == event.guild?.selfMember?.voiceState?.channel) {
                event.replyFailure("${event.jda.selfUser.name} is already connected to the voice channel!")
                    .setEphemeral(true)
                    .queue()
            } else {
                event.guild?.audioManager?.openAudioConnection(it)

                event.replySuccess("Joined the voice channel!")
                    .setEphemeral(true)
                    .queue()
            }
        } ?: event.replyFailure("You are not connected to a voice channel!")
            .setEphemeral(true)
            .queue()
    }
}