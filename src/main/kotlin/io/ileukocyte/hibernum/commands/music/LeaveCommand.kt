package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class LeaveCommand : TextOnlyCommand {
    override val name = "leave"
    override val description = "Makes the bot leave your voice channel"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        event.guild.selfMember.voiceState?.channel?.let { botVc ->
            event.member?.voiceState?.channel?.let {
                if (it == botVc) {
                    event.guild.audioPlayer?.stop()
                    event.guild.audioManager.closeAudioConnection()

                    event.channel.sendSuccess("Left the voice channel!").queue()
                } else {
                    event.channel.sendFailure("You are not connected to the required voice channel!").queue()
                }
            } ?: event.channel.sendFailure("You are not connected to a voice channel!").queue()
        } ?: event.channel.sendFailure("${event.jda.selfUser.name} is not connected to a voice channel!").queue()
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
                } else {
                    event.replyFailure("You are not connected to the required voice channel!")
                        .setEphemeral(true)
                        .queue()
                }
            } ?: event.replyFailure("You are not connected to a voice channel!")
                .setEphemeral(true)
                .queue()
        } ?: event.replyFailure("${event.jda.selfUser.name} is not connected to a voice channel!")
            .setEphemeral(true)
            .queue()
    }
}