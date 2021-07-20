package io.ileukocyte.hibernum.handlers

import io.ileukocyte.hibernum.audio.audioPlayer
import io.ileukocyte.hibernum.audio.stop
import io.ileukocyte.hibernum.extensions.EmbedType
import io.ileukocyte.hibernum.extensions.defaultEmbed
import io.ileukocyte.hibernum.extensions.sendMessage
import io.ileukocyte.hibernum.utils.getProcessByMessage
import io.ileukocyte.hibernum.utils.kill

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

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

    @OptIn(ExperimentalTime::class)
    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        event.jda.getProcessByMessage(event.messageIdLong)?.let { process ->
            process.kill(event.jda)

            val description =
                "The ${process.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} process " +
                        "running in this channel has been terminated by message deletion!"

            event.jda.getTextChannelById(process.channel)
                ?.sendMessage {
                    embeds += defaultEmbed(description, EmbedType.WARNING, "This message will self-delete in 5 seconds")

                    process.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                        .takeUnless { it.isEmpty() }
                        ?.let { content += it }
                }?.queue({ it.delete().queueAfter(5, DurationUnit.SECONDS, {}) {} }, {})
        }
    }
}