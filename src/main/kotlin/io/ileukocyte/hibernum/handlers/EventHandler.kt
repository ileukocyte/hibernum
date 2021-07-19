package io.ileukocyte.hibernum.handlers

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

object EventHandler : ListenerAdapter() {
    override fun onSlashCommand(event: SlashCommandEvent) =
        CommandHandler(event)

    override fun onButtonClick(event: ButtonClickEvent) =
        CommandHandler(event)

    override fun onSelectionMenu(event: SelectionMenuEvent) =
        CommandHandler(event)

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) =
        CommandHandler(event)

    override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        if (event.member == event.guild.selfMember)
            event.guild.audioPlayer?.stop() // just in case
    }
}