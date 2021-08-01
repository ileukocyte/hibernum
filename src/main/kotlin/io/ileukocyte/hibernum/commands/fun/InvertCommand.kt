package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.sendEmbed

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

import org.apache.commons.validator.routines.UrlValidator

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class InvertCommand : TextOnlyCommand {
    override val name = "invert"
    override val description = "N/A"

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
                    .toByteArray()

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
                HttpClient(CIO).get<ByteArray>(input)
            } catch (_: Exception) {
                deferred.delete().queue({}) {}

                throw CommandException("The provided link is invalid!")
            }

            val image = withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(response)) }
                .apply { invert() }

            val bytesToSend = ByteArrayOutputStream()
                .apply { withContext(Dispatchers.IO) { ImageIO.write(image, "png", this@apply) } }
                .toByteArray()

            deferred.editMessageEmbeds().addFile(bytesToSend, "inverted.png").queue({}) {
                event.channel.sendFile(bytesToSend, "inverted.png").queue()
            }
        }
    }

    private fun BufferedImage.invert() {
        for (x in 0 until width) {
            for (y in 0 until height) {
                val rgba = getRGB(x, y)

                val a = rgba shr 24 and 0xff shl 24
                var r = rgba shr 16 and 0xff
                var g = rgba shr 8 and 0xff
                var b = rgba and 0xff

                r = 255 - r shl 16
                g = 255 - g shl 8
                b = 255 - b

                setRGB(x, y, a or r or g or b)
            }
        }
    }
}