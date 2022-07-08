package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
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

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.apache.commons.validator.routines.UrlValidator

class InvertCommand : Command {
    override val name = "invert"
    override val description = "Inverts the provided image (see the command's help menu)"
    override val fullDescription = "Inverts the image provided as a link, a file (non-slash-only), " +
            "or a profile picture of the mentioned user (or yours in case of no arguments provided)"
    override val aliases = setOf("negative")
    override val usages = setOf(
        setOf("image file"),
        setOf("image link"),
        setOf("user mention"),
    )
    override val options = setOf(
        OptionData(OptionType.STRING, "link", "The provided image"),
        OptionData(OptionType.USER, "user", "The provided mention (has a higher priority)"),
    )
    override val cooldown = 7L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (event.message.attachments.any { it.isImage }) {
            val deferred = event.channel.sendEmbed {
                color = Immutable.SUCCESS
                description = "Trying to invert the image\u2026"
            }.await()

            val attachment = event.message.attachments.first { it.isImage }

            attachment.retrieveInputStream().thenAccept {
                val image = ImageIO.read(ByteArrayInputStream(it.readAllBytes()))
                    .apply { invert() }

                val bytesToSend = ByteArrayOutputStream()
                    .apply { ImageIO.write(image, "png", this) }
                    .use { s -> s.toByteArray() }

                deferred.editMessageEmbeds().addFile(bytesToSend, "inverted.png").queue({}) {
                    event.channel.sendFile(bytesToSend, "inverted.png").queue()
                }
            }.exceptionally {
                deferred.delete().queue({}) {}

                throw CommandException("${it::class.qualifiedName ?: "An unknown exception"}: ${it.message ?: "something went wrong!"}")
            }
        } else {
            val input = event.message.mentionedUsers.firstOrNull()?.effectiveAvatarUrl
                ?.let { "$it?size=2048".replace("gif", "png") }
                ?: args?.apply { if (!UrlValidator().isValid(this)) throw CommandException("The provided input is invalid!") }
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

    override suspend fun invoke(event: SlashCommandEvent) {
        val input = event.getOption("user")?.asUser?.effectiveAvatarUrl
            ?.let { "$it?size=2048".replace("gif", "png") }
            ?: event.getOption("link")
                ?.asString
                ?.apply { if (!UrlValidator().isValid(this)) throw CommandException("The provided input is invalid!") }
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
}