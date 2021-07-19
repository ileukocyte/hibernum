package io.ileukocyte.hibernum.commands.music

import io.ileukocyte.hibernum.audio.LoopMode
import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button

class LoopCommand : Command {
    override val name = "loop"
    override val description = "Sets a repeating mode for the music player"
    override val aliases = setOf("repeat")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (event.guild.selfMember.voiceState?.channel !== null) {
            if (event.member?.voiceState?.channel == event.guild.selfMember.voiceState?.channel) {
                val buttons = LoopMode.values()
                    .filter { it != event.guild.audioPlayer?.scheduler?.loopMode }
                    .map { Button.secondary("$name-${event.author.idLong}-${it.name}", it.toString().removeSuffix("d")) }

                event.channel.sendConfirmation("Choose a repeating mode to set!")
                    .setActionRow(*buttons.toTypedArray(), Button.danger("$name-${event.author.idLong}-exit", "Exit"))
                    .queue()
            } else throw CommandException("You are not connected to the required voice channel!")
        } else throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        if (event.guild?.selfMember?.voiceState?.channel !== null) {
            if (event.member?.voiceState?.channel == event.guild?.selfMember?.voiceState?.channel) {
                val buttons = LoopMode.values()
                    .filter { it != event.guild?.audioPlayer?.scheduler?.loopMode }
                    .map { Button.secondary("$name-${event.user.idLong}-${it.name}", it.toString().removeSuffix("d")) }

                event.replyConfirmation("Choose a repeating mode to set!")
                    .addActionRow(*buttons.toTypedArray(), Button.danger("$name-${event.user.idLong}-exit", "Exit"))
                    .queue()
            } else throw CommandException("You are not connected to the required voice channel!")
        } else throw CommandException("${event.jda.selfUser.name} is not connected to a voice channel!")
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            if (id.last() == "exit") {
                event.message?.delete()?.queue()

                return
            }

            val currentMode = event.guild?.audioPlayer?.scheduler?.loopMode ?: throw CommandException()
            val newMode = LoopMode.valueOf(id.last())

            event.guild?.audioPlayer?.scheduler?.loopMode = newMode

            val description = "$newMode looping has been enabled!".takeUnless { newMode == LoopMode.DISABLED }
                ?: "$currentMode looping has been disabled!"

            event.replySuccess(description)
                .setEphemeral(true)
                .flatMap { event.message?.delete() }
                .queue()
        }
    }
}