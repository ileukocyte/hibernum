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
    override val description = "The command that sends the link for inviting the bot to your server"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        event.channel.sendActionRow(linkButtonButton(event.jda.selfUser.idLong)).queue()

    override suspend fun invoke(event: SlashCommandEvent) =
        event.replyActionRow(linkButtonButton(event.jda.selfUser.idLong)).queue()

    private fun linkButtonButton(botId: Long): Button {
        val link = "https://discord.com/api/oauth2/authorize?client_id=$botId" +
                "&permissions=${Immutable.INVITE_PERMISSIONS}" +
                "&scope=applications.commands%20bot"


        return Button.link(link, "Invite Link")
    }
}