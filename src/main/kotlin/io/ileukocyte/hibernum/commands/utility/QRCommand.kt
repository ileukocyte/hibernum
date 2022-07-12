package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.MessageContextCommand
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.await

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import net.glxn.qrgen.core.image.ImageType
import net.glxn.qrgen.javase.QRCode

class QRCommand : Command, MessageContextCommand {
    override val name = "qr"
    override val contextName = "Generate QR Code"
    override val description = "Generates a QR code from the provided input"
    override val usages = setOf(setOf("input"))
    override val options = setOf(
        OptionData(OptionType.STRING, "input", "The provided input", true))
    override val cooldown = 3L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val qr = QRCode.from(args ?: throw NoArgumentsException)
            .withSize(768, 768)
            .withCharset("UTF-8")
            .to(ImageType.PNG)
            .stream()

        qr.use { event.channel.sendFile(it.toByteArray(), "qr.png").queue() }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val deferred = event.deferReply().await()

        val qr = QRCode.from(event.getOption("input")?.asString ?: return)
            .withSize(768, 768)
            .withCharset("UTF-8")
            .to(ImageType.PNG)
            .stream()

        qr.use { deferred.sendFile(it.toByteArray(), "qr.png").queue() }
    }

    override suspend fun invoke(event: MessageContextInteractionEvent) {
        val input = event.target.contentRaw.takeUnless { it.isEmpty() }
            ?: throw CommandException("No text content has been provided in the message!")

        try {
            val deferred = event.deferReply().await()

            val qr = QRCode.from(input)
                .withSize(768, 768)
                .withCharset("UTF-8")
                .to(ImageType.PNG)
                .stream()

            qr.use { deferred.sendFile(it.toByteArray(), "qr.png").await() }
        } catch (_: ErrorResponseException) {
            val qr = QRCode.from(input)
                .withSize(768, 768)
                .withCharset("UTF-8")
                .to(ImageType.PNG)
                .stream()

            qr.use { event.messageChannel.sendFile(it.toByteArray(), "qr.png").queue() }
        }
    }
}