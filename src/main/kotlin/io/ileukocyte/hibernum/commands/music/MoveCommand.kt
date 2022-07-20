package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.isInt
import io.ileukocyte.hibernum.extensions.replySuccess
import io.ileukocyte.hibernum.extensions.sendSuccess

import java.util.concurrent.ConcurrentLinkedQueue

import kotlin.math.max
import kotlin.math.min

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class MoveCommand : TextCommand {
    override val name = "move"
    override val description = "Moves the selected track in the queue to the specified position"
    override val usages = setOf(setOf("song", "index"))
    override val options = setOf(
        OptionData(OptionType.INTEGER, "song", "The index of the song to move", true),
        OptionData(OptionType.INTEGER, "index", "The index to move the song to", true),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val (song, index) = (args ?: throw NoArgumentsException)
            .split(" ")
            .take(2)
            .filter { it.isInt }
            .takeIf { it.size == 2 }
            ?.map { it.toInt().dec() }
            ?: throw CommandException("You have provided invalid arguments!")

        val audioPlayer = event.guild.audioPlayer ?: throw CommandException()

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                if (song == index)
                    throw CommandException("Both indices must be different!")

                val list = audioPlayer.scheduler.queue.toMutableList()
                val track = list.getOrNull(song)
                    ?: throw CommandException("You have specified an invalid song index!")

                list.removeAt(song)
                list.add(max(0, min(index, audioPlayer.scheduler.queue.size.dec())), track)

                val description = "[${track.info.title}](${track.info.uri}) has been moved to the new position!"

                audioPlayer.scheduler.queue = ConcurrentLinkedQueue(list)

                event.channel.sendSuccess(description).queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The current queue is empty!")
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val song = event.getOption("song")?.asString?.toIntOrNull()?.dec() ?: return
        val index = event.getOption("index")?.asString?.toIntOrNull()?.dec() ?: return

        val audioPlayer = event.guild?.audioPlayer ?: return

        if (audioPlayer.scheduler.queue.isNotEmpty()) {
            if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                if (song == index) {
                    throw CommandException("Both indices must be different!")
                }

                val list = audioPlayer.scheduler.queue.toMutableList()
                val track = list.getOrNull(song)
                    ?: throw CommandException("You have specified an invalid song index!")

                list.removeAt(song)
                list.add(max(0, min(index, audioPlayer.scheduler.queue.size.dec())), track)

                val description = "[${track.info.title}](${track.info.uri}) has been moved to the new position!"

                audioPlayer.scheduler.queue = ConcurrentLinkedQueue(list)

                event.replySuccess(description).queue()
            } else {
                throw CommandException("You are not connected to the required voice channel!")
            }
        } else {
            throw CommandException("The current queue is empty!")
        }
    }
}