package io.ileukocyte.hibernum.commands.utils

import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.glxn.qrgen.core.image.ImageType
import net.glxn.qrgen.javase.QRCode

class QRCommand : TextOnlyCommand {
    override val name = "qr"
    override val description = "N/A"

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val qr = QRCode.from(args ?: throw NoArgumentsException)
            .withSize(768, 768)
            .to(ImageType.PNG)
            .stream()

        qr.use { event.channel.sendFile(it.toByteArray(), "qr.png").queue() }
    }
}