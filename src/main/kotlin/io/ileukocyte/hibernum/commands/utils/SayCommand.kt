package io.ileukocyte.hibernum.commands.utils

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

class SayCommand : TextOnlyCommand {
    override val name = "say"
    override val description = "Sends your message on behalf of the bot as an embed message"
    override val aliases = setOf("announce")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (args === null && event.message.attachments.none { it.isImage })
            throw NoArgumentsException

        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = args
            image = event.message.attachments.firstOrNull { it.isImage }?.url

            author {
                name = event.author.asTag
                iconUrl = event.author.effectiveAvatarUrl
            }
        }.queue()
    }
}