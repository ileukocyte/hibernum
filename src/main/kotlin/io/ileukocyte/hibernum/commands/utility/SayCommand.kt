package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class SayCommand : Command {
    override val name = "say"
    override val description = "Sends your message on behalf of the bot as an embed message"
    override val aliases = setOf("announce")
    override val usages = setOf("message", "image file")
    override val options = setOf(
        OptionData(OptionType.STRING, "message", "The message to send on behalf of the bot", true)
    )

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (args === null && event.message.attachments.none { it.isImage })
            throw NoArgumentsException

        val (imageName, imageStream) = event.message.attachments.firstOrNull { it.isImage }
            .let { it?.fileName to it?.retrieveInputStream() }

        event.message.delete().queue()

        val restAction = event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = args?.limitTo(4000)
            image = imageStream?.let { "attachment://$imageName" }

            author {
                name = event.author.asTag
                iconUrl = event.author.effectiveAvatarUrl
            }
        }

        imageStream?.thenAccept { restAction.addFile(it, imageName.orEmpty()).queue() }
            ?: restAction.queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        event.deferReply().queue { it.deleteOriginal().queue({}) {} }

        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = event.getOption("message")?.asString?.limitTo(4000) ?: return

            author {
                name = event.user.asTag
                iconUrl = event.user.effectiveAvatarUrl
            }
        }.queue()
    }
}