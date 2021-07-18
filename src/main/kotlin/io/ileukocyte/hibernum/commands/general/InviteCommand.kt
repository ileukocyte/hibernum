package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class InviteCommand : Command {
    override val name = "invite"
    override val description = "The command that sends the link for inviting the bot to your server"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        event.channel.sendMessageEmbeds(inviteEmbed(event.jda.selfUser)).queue()

    override suspend fun invoke(event: SlashCommandEvent) =
        event.replyEmbeds(inviteEmbed(event.jda.selfUser)).queue()

    private fun inviteEmbed(bot: User) = buildEmbed {
        val link = "https://discord.com/api/oauth2/authorize?client_id=${bot.idLong}" +
                "&permissions=${Immutable.INVITE_PERMISSIONS}" +
                "&scope=applications.commands%20bot"

        color = Immutable.SUCCESS
        description = "Want to invite the bot to your own server? Do it **[here]($link)**!"

        author {
            name = bot.name
            iconUrl = bot.effectiveAvatarUrl
        }
    }
}