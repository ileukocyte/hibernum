package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.extensions.replyActionRow
import io.ileukocyte.hibernum.extensions.sendActionRow

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button

class InviteCommand : Command {
    override val name = "invite"
    override val description = "Sends the link for inviting the bot to your server"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        event.channel.sendActionRow(linkButtonButton(event.jda.selfUser.id)).queue()

    override suspend fun invoke(event: SlashCommandEvent) =
        event.replyActionRow(linkButtonButton(event.jda.selfUser.id)).queue()

    private fun linkButtonButton(botId: String) =
        Button.link(Immutable.INVITE_LINK_FORMAT.format(botId), "Invite Link")
}