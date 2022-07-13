package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replyWarning
import io.ileukocyte.hibernum.extensions.sendWarning

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button

class InviteCommand : TextCommand {
    override val name = "invite"
    override val description = "Sends the link for inviting the bot to your server"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        event.channel.sendWarning("In order to invite the bot, go to its profile " +
                "since the command is deprecated and will be removed " +
                "once the profile invitation button is supported on Discord on mobile!")
            .setActionRow(linkButtonButton(event.jda.selfUser.id))
            .queue()

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        event.replyWarning("In order to invite the bot, go to its profile " +
                "since the command is deprecated and will be removed " +
                "once the profile invitation button is supported on Discord on mobile!")
            .addActionRow(linkButtonButton(event.jda.selfUser.id))
            .queue()

    private fun linkButtonButton(botId: String) =
        Button.link(Immutable.INVITE_LINK_FORMAT.format(botId), "Invite Link")
}