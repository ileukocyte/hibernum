package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.invert

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

import javax.imageio.ImageIO

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.apache.commons.validator.routines.UrlValidator

class InvertCommand : TextCommand, UniversalContextCommand {
    override val name = "invert"
    override val contextName = "Image Negation"
    override val description = "Inverts the provided image (see the command's help menu)"
    override val fullDescription = "Inverts the image provided as a link, a file, or " +
            "a profile picture of the mentioned user (or yours in case of no arguments provided)"
    override val aliases = setOf("negate", "negative")
    override val usages = setOf(
        setOf("image file"),
        setOf("image link"),
        setOf("user mention"),
    )
    override val options = setOf(
        OptionData(OptionType.STRING, "link", "The provided image"),
        OptionData(OptionType.USER, "user", "The provided mention (has a higher priority than a link)"),
        OptionData(OptionType.ATTACHMENT, "attachment", "The provided image file (has the highest priority)"),
    )
    override val cooldown = 7L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        event.message.attachments.firstOrNull { it.isImage }?.let { attachment ->
            val deferred = event.channel.sendEmbed {
                color = Immutable.SUCCESS
                description = "Trying to invert the image\u2026"
            }.await()

            attachment.proxy.download().await().use {
                try {
                    val image = ImageIO.read(ByteArrayInputStream(it.readAllBytes()))
                        .apply { invert() }

                    val bytesToSend = ByteArrayOutputStream()
                        .apply { ImageIO.write(image, "png", this) }
                        .use { s -> s.toByteArray() }

                    deferred.editMessageEmbeds().addFile(bytesToSend, "inverted.png").queue({}) {
                        event.channel.sendFile(bytesToSend, "inverted.png").queue()
                    }
                } catch (e: Exception) {
                    deferred.delete().queue({}) {}

                    throw CommandException("${it::class.qualifiedName ?: "An unknown exception"}: ${e.message ?: "something went wrong!"}")
                }
            }
        } ?: run {
            val input = event.message.mentions.usersBag.firstOrNull()?.effectiveAvatarUrl
                ?.let { "$it?size=2048".replace("gif", "png") }
                ?: args?.split("\\s+".toRegex())?.firstOrNull { UrlValidator().isValid(it) }
                ?: "${event.author.effectiveAvatarUrl}?size=2048".replace("gif", "png")

            val deferred = event.channel.sendEmbed {
                color = Immutable.SUCCESS
                description = "Trying to invert the image\u2026"
            }.await()

            val response = try {
                HttpClient(CIO).get(input).readBytes()
            } catch (_: Exception) {
                deferred.delete().queue({}) {}

                throw CommandException("The provided link is invalid!")
            }

            val image = withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(response)) }
                .apply { invert() }

            val bytesToSend = ByteArrayOutputStream()
                .apply { withContext(Dispatchers.IO) { ImageIO.write(image, "png", this@apply) } }
                .use { it.toByteArray() }

            deferred.editMessageEmbeds().addFile(bytesToSend, "inverted.png").queue({}) {
                event.channel.sendFile(bytesToSend, "inverted.png").queue()
            }
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val input = event.getOption("attachment")?.asAttachment?.takeIf { it.isImage }?.proxyUrl
            ?: event.getOption("user")?.asUser?.effectiveAvatarUrl
                ?.let { "$it?size=2048".replace("gif", "png") }
            ?: event.getOption("link")
                ?.asString
                ?.apply {
                    if (!UrlValidator().isValid(this)) {
                        throw CommandException("The provided input is invalid!")
                    }
                }
            ?: "${event.user.effectiveAvatarUrl}?size=2048".replace("gif", "png")

        val deferred = event.replyEmbed {
            color = Immutable.SUCCESS
            description = "Trying to invert the image\u2026"
        }.await()

        val response = try {
            HttpClient(CIO).get(input).readBytes()
        } catch (_: Exception) {
            deferred.editOriginalEmbeds(
                defaultEmbed("The provided link is invalid!", EmbedType.FAILURE)
            ).queue(null) { throw CommandException("The provided link is invalid!") }

            return
        }

        val image = withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(response)) }
            .apply { invert() }

        val bytesToSend = ByteArrayOutputStream()
            .apply { withContext(Dispatchers.IO) { ImageIO.write(image, "png", this@apply) } }
            .toByteArray()

        deferred.editOriginalEmbeds().addFile(bytesToSend, "inverted.png").queue(null) {
            event.channel.sendFile(bytesToSend, "inverted.png").queue()
        }
    }

    override suspend fun invoke(event: MessageContextInteractionEvent) {
        event.target.attachments.firstOrNull { it.isImage }?.let { attachment ->
            val deferred = event.replyEmbed {
                color = Immutable.SUCCESS
                description = "Trying to invert the image\u2026"
            }.await()

            attachment.proxy.download().await().use {
                try {
                    val image = ImageIO.read(ByteArrayInputStream(it.readAllBytes()))
                        .apply { invert() }

                    val bytesToSend = ByteArrayOutputStream()
                        .apply { ImageIO.write(image, "png", this) }
                        .use { s -> s.toByteArray() }

                    deferred.editOriginalEmbeds().addFile(bytesToSend, "inverted.png").queue({}) {
                        event.messageChannel.sendFile(bytesToSend, "inverted.png").queue()
                    }
                } catch (e: Exception) {
                    deferred.deleteOriginal().queue({}) {}

                    throw CommandException("${it::class.qualifiedName ?: "An unknown exception"}: ${e.message ?: "something went wrong!"}")
                }
            }
        } ?: run {
            val input = event.target.mentions.usersBag.firstOrNull()?.effectiveAvatarUrl
                ?.let { "$it?size=2048".replace("gif", "png") }
                ?: event.target.contentRaw.split("\\s+".toRegex()).firstOrNull { UrlValidator().isValid(it) }
                ?: "${event.target.author.effectiveAvatarUrl}?size=2048".replace("gif", "png")

            val deferred = event.replyEmbed {
                color = Immutable.SUCCESS
                description = "Trying to invert the image\u2026"
            }.await()

            val response = try {
                HttpClient(CIO).get(input).readBytes()
            } catch (_: Exception) {
                deferred.deleteOriginal().queue({}) {}

                throw CommandException("The provided link is invalid!")
            }

            val image = withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(response)) }
                .apply { invert() }

            val bytesToSend = ByteArrayOutputStream()
                .apply { withContext(Dispatchers.IO) { ImageIO.write(image, "png", this@apply) } }
                .use { it.toByteArray() }

            deferred.editOriginalEmbeds().addFile(bytesToSend, "inverted.png").queue({}) {
                event.messageChannel.sendFile(bytesToSend, "inverted.png").queue()
            }
        }
    }

    override suspend fun invoke(event: UserContextInteractionEvent) {
        val deferred = event.replyEmbed {
            color = Immutable.SUCCESS
            description = "Trying to invert the image\u2026"
        }.await()

        val response = try {
            HttpClient(CIO).get("${event.target.effectiveAvatarUrl}?size=2048").readBytes()
        } catch (_: Exception) {
            deferred.editOriginalEmbeds(
                defaultEmbed("The profile picture request has been unsuccessful!", EmbedType.FAILURE)
            ).queue(null) { throw CommandException("The profile picture request has been unsuccessful!") }

            return
        }

        val image = withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(response)) }
            .apply { invert() }

        val bytesToSend = ByteArrayOutputStream()
            .apply { withContext(Dispatchers.IO) { ImageIO.write(image, "png", this@apply) } }
            .toByteArray()

        deferred.editOriginalEmbeds().addFile(bytesToSend, "inverted.png").queue(null) {
            event.messageChannel.sendFile(bytesToSend, "inverted.png").queue()
        }
    }
}