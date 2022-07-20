package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class RemoveCommand : TextCommand {
    override val name = "remove"
    override val description = "Removes the selected track from the queue"
    override val usages = setOf(setOf("song"))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "song", "The number of the song to remove", true))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val number = args?.toIntOrNull() ?: throw CommandException("You have specified a wrong number!")

        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                val track = audioPlayer.scheduler.queue.toList().getOrNull(number.dec())
                    ?: throw CommandException("You have specified a wrong number!")

                val description = "[${track.info.title}](${track.info.uri}) has been removed from the queue!"

                audioPlayer.scheduler.queue -= track

                event.channel.sendSuccess(description).queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The current queue is empty!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val number = event.getOption("song")?.asString?.toIntOrNull()
            ?: throw CommandException("You have specified a wrong number!")

        val audioPlayer = event.guild?.audioPlayer ?: throw CommandException()

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                val track = audioPlayer.scheduler.queue.toList().getOrNull(number.dec())
                    ?: throw CommandException("You have specified a wrong number!")

                val description = "[${track.info.title}](${track.info.uri}) has been removed from the queue!"

                audioPlayer.scheduler.queue -= track

                event.replySuccess(description).queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The current queue is empty!")
        }
    }
}