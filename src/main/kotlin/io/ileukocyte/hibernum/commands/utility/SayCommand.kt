package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.extensions.remove
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await as futureAwait

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.utils.FileUpload

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class SayCommand : TextCommand {
    override val name = "say"
    override val description = "Sends your message on behalf of the bot as an embed message"
    override val aliases = setOf("announce")
    override val usages = setOf(
        setOf("text input".toClassicTextUsage()),
        setOf("image file".toClassicTextUsage()),
    )
    override val options = setOf(
        OptionData(
            OptionType.STRING,
            "text",
            "The text to send on behalf of the bot (multi-line input can only be sent from mobile Discord now)",
        ),
        OptionData(OptionType.ATTACHMENT, "image", "The image to send on behalf of the bot"),
        OptionData(
            OptionType.BOOLEAN,
            "modal",
            "Whether to get text input from a modal window which allows multi-line input, but disallows mentions",
        ),
        OptionData(
            OptionType.STRING,
            "non-embed",
            "The text to include outside of the embed message (e.g. mentions)",
        ),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (args === null && event.message.attachments.none { it.isImage }) {
            throw NoArgumentsException
        }

        val reference = event.message.messageReference?.resolve()?.await()

        val (imageName, imageStream) = event.message.attachments.firstOrNull {
            it.isImage && it.size <= event.guild.maxFileSize
        }.let { it?.fileName to it?.proxy?.download()?.futureAwait() }

        try {
            event.message.delete().await()
        } catch (_: ErrorResponseException) {}

        val embed = buildEmbed {
            color = Immutable.SUCCESS
            description = args?.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)
            image = imageStream?.let { "attachment://$imageName" }

            author {
                name = event.author.asTag
                iconUrl = event.author.effectiveAvatarUrl
            }
        }

        val restAction = reference?.replyEmbeds(embed)
            ?: event.channel.sendMessageEmbeds(embed)

        imageStream?.let {
            val file = FileUpload.fromData(it, imageName.orEmpty())

            restAction.setFiles(file).queue(null) {
                file.close()
            }
        } ?: restAction.queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return

        val text = event.getOption("text")?.asString
        val image = event.getOption("image")
            ?.asAttachment
            ?.takeIf { it.isImage && it.size <= guild.maxFileSize }
            ?.let { it.fileName to it.proxy.download().futureAwait() }

        val nonEmbed = event.getOption("non-embed")?.asString?.let {
            if (guild.publicRole.asMention in it
                && event.member?.hasPermission(Permission.MESSAGE_MENTION_EVERYONE) == false) {
                it.remove(guild.publicRole.asMention).remove("@here")
            } else {
                it
            }
        }?.trim()

        val isModal = event.getOption("modal")?.asBoolean ?: false

        if (!isModal) {
            if (text === null && image === null) {
                throw NoArgumentsException
            }

            try {
                event.deferReply().await()
                    .deleteOriginal()
                    .await()
            } catch (_: ErrorResponseException) {}

            val restAction = event.channel.sendEmbed {
                color = Immutable.SUCCESS
                description = text?.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)
                this.image = image?.first?.let { "attachment://$it" }

                author {
                    name = event.user.asTag
                    iconUrl = event.user.effectiveAvatarUrl
                }
            }.applyIf(nonEmbed !== null) {
                addContent(nonEmbed ?: return)
            }

            image?.second?.let {
                val file = FileUpload.fromData(it, image.first)

                restAction.setFiles(file).queue(null) {
                    file.close()
                }
            } ?: restAction.queue()
        } else {
            val input = TextInput
                .create("$interactionName-input", "Enter Your Text:", TextInputStyle.PARAGRAPH)
                .setValue(text)
                .build()
            val modal = Modal
                .create("$interactionName-modal", "Embed Announcement")
                .addActionRow(input)
                .build()

            event.replyModal(modal).await()

            try {
                val modalEvent = event.jda.awaitEvent<ModalInteractionEvent>(15, TimeUnit.MINUTES, waiterProcess {
                    users += event.user.idLong
                    channel = event.channel.idLong
                    command = this@SayCommand
                }) {
                    it.modalId == "$interactionName-modal" && it.user == event.user && it.channel == event.channel
                } ?: return

                val content = modalEvent.getValue("$interactionName-input")?.asString ?: return

                try {
                    modalEvent.deferReply().await()
                        .deleteOriginal()
                        .await()
                } catch (_: ErrorResponseException) {}

                val restAction = event.channel.sendEmbed {
                    color = Immutable.SUCCESS
                    description = content.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)
                    this.image = image?.first?.let { "attachment://$it" }

                    author {
                        name = event.user.asTag
                        iconUrl = event.user.effectiveAvatarUrl
                    }
                }.applyIf(nonEmbed !== null) {
                    addContent(nonEmbed ?: return)
                }

                image?.second?.let {
                    val file = FileUpload.fromData(it, image.first)

                    restAction.setFiles(file).queue(null) {
                        file.close()
                    }
                } ?: restAction.queue()
            } catch (_: TimeoutCancellationException) {}
        }
    }
}