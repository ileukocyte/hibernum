package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.FileUpload

class SayCommand : TextCommand {
    override val name = "say"
    override val description = "Sends your message on behalf of the bot as an embed message"
    override val aliases = setOf("announce")
    override val usages = setOf(
        setOf("text input".toClassicTextUsage()),
        setOf("image file".toClassicTextUsage()),
    )
    override val options = setOf(
        OptionData(OptionType.STRING, "text", "The text to send on behalf of the bot"),
        OptionData(OptionType.ATTACHMENT, "attachment", "The attachment to send on behalf of the bot"),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (args === null && event.message.attachments.none { it.isImage }) {
            throw NoArgumentsException
        }

        val (imageName, imageStream) = event.message.attachments.firstOrNull { it.isImage }
            .let { it?.fileName to it?.proxy?.download()?.await() }

        event.message.delete().queue()

        val restAction = event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = args?.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)
            image = imageStream?.let { "attachment://$imageName" }

            author {
                name = event.author.asTag
                iconUrl = event.author.effectiveAvatarUrl
            }
        }

        imageStream?.let {
            restAction.setFiles(FileUpload.fromData(it, imageName.orEmpty())).queue()
        } ?: restAction.queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val text = event.getOption("text")?.asString
        val attachment = event.getOption("attachment")
            ?.asAttachment
            ?.takeIf { it.isImage }
            ?.let { it.fileName to it.proxy.download().await() }

        if (text === null && attachment === null) {
            throw NoArgumentsException
        }

        event.deferReply().queue { it.deleteOriginal().queue(null) {} }

        val restAction = event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = text?.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)
            image = attachment?.first?.let { "attachment://$it" }

            author {
                name = event.user.asTag
                iconUrl = event.user.effectiveAvatarUrl
            }
        }

        attachment?.second?.let {
            restAction.setFiles(FileUpload.fromData(it, attachment.first)).queue()
        } ?: restAction.queue()
    }
}