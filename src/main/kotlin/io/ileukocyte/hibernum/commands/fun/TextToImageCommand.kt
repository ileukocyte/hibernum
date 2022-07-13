package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.MessageContextCommand
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.limitTo

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File

import javax.imageio.ImageIO

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle

class TextToImageCommand : TextCommand, MessageContextCommand {
    override val name = "tti"
    override val contextName = "Text to Image"
    override val description = "Creates an image containing the provided text"
    override val aliases = setOf("texttoimage", "text-to-image")
    override val usages = setOf(setOf("input"))
    override val cooldown = 5L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val lines = args?.split("\n")?.map { it.limitTo(100) } ?: throw NoArgumentsException
        val bytes = textToImage(lines.take(25), lines.maxByOrNull { it.length } ?: return)

        event.channel.sendFile(bytes, "tti.png").queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val input = TextInput
            .create("input", "Enter Your Text:", TextInputStyle.PARAGRAPH)
            .build()
        val modal = Modal
            .create("$name-modal", "Text to Image")
            .addActionRow(input)
            .build()

        event.replyModal(modal).queue()
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        try {
            val deferred = event.deferReply().await()

            val lines = event.getValue("input")?.asString?.split("\n")?.map { it.limitTo(100) }
                ?: return
            val bytes = textToImage(lines.take(25), lines.maxByOrNull { it.length } ?: return)

            deferred.sendFile(bytes, "tti.png").await()
        } catch (_: ErrorResponseException) {
            val lines = event.getValue("input")?.asString?.split("\n")?.map { it.limitTo(100) }
                ?: return
            val bytes = textToImage(lines.take(25), lines.maxByOrNull { it.length } ?: return)

            event.messageChannel.sendFile(bytes, "tti.png").await()
        }
    }

    override suspend fun invoke(event: MessageContextInteractionEvent) {
        val content = event.target.contentRaw.takeUnless { it.isEmpty() }
            ?: throw CommandException("The message has no text provided!")

        val deferred = event.deferReply().await()

        val lines = content.split("\n").map { it.limitTo(100) }
        val bytes = textToImage(lines.take(25), lines.maxByOrNull { it.length } ?: return)

        deferred.sendFile(bytes, "tti.png").queue()
    }

    private fun textToImage(lines: List<String>, longest: String): ByteArray {
        var image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        var graphics = image.createGraphics()

        graphics.font = TTI_FONT

        val (width, height) = graphics.fontMetrics.let { it.stringWidth(longest) + 7 to it.height * lines.size }

        graphics.dispose()

        image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        graphics = image.createGraphics()

        graphics.font = TTI_FONT
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.color = Color.WHITE

        var ascent = graphics.fontMetrics.ascent

        for (line in lines) {
            graphics.drawString(line, 0, ascent)

            ascent += graphics.fontMetrics.height
        }

        graphics.dispose()

        return ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)

            it.toByteArray()
        }
    }

    companion object {
        @JvmField
        val TTI_FONT =
            Font.createFont(Font.TRUETYPE_FONT, File("src/main/resources/tti-font.ttf"))
                .deriveFont(32f)
    }
}