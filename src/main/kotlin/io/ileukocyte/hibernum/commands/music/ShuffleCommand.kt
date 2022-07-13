package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class ShuffleCommand : TextCommand {
    override val name = "shuffle"
    override val description = "Shuffles the queue"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val audioPlayer = event.guild.audioPlayer ?: return

        if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                audioPlayer.scheduler.shuffle()

                event.channel.sendSuccess("The queue has been shuffled!").queue()
            } else {
                throw CommandException("The queue is empty at the moment!")
            }
        } else {
            throw CommandException("You are not connected to the required voice channel!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return
        val audioPlayer = guild.audioPlayer ?: return

        if (event.member?.voiceState?.channel == guild.selfMember.voiceState?.channel) {
            if (audioPlayer.scheduler.queue.isNotEmpty()) {
                audioPlayer.scheduler.shuffle()

                event.replySuccess("The queue has been shuffled!").queue()
            } else {
                throw CommandException("The queue is empty at the moment!")
            }
        } else {
            throw CommandException("You are not connected to the required voice channel!")
        }
    }
}