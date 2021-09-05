package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.NoArgumentsException

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import net.glxn.qrgen.core.image.ImageType
import net.glxn.qrgen.javase.QRCode

class QRCommand : Command {
    override val name = "qr"
    override val description = "Generates a QR code from the provided input"
    override val usages = setOf("input")
    override val options = setOf(
        OptionData(OptionType.STRING, "input", "The provided input", true)
    )
    override val cooldown = 3L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val qr = QRCode.from(args ?: throw NoArgumentsException)
            .withSize(768, 768)
            .withCharset("UTF-8")
            .to(ImageType.PNG)
            .stream()

        qr.use { event.channel.sendFile(it.toByteArray(), "qr.png").queue() }
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val qr = QRCode.from(event.getOption("input")?.asString ?: return)
            .withSize(768, 768)
            .withCharset("UTF-8")
            .to(ImageType.PNG)
            .stream()

        qr.use { event.deferReply().addFile(it.toByteArray(), "qr.png").queue() }
    }
}