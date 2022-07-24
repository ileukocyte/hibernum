package io.ileukocyte.hibernum.commands.utility

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.MessageContextOnlyCommand
import io.ileukocyte.hibernum.commands.SubcommandHolder
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

import javax.imageio.ImageIO

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.utils.FileUpload

import net.glxn.qrgen.core.image.ImageType
import net.glxn.qrgen.javase.QRCode

class QRCommand : TextCommand, SubcommandHolder, MessageContextOnlyCommand {
    override val name = "qr"
    override val contextName = "QR Code"
    override val description = "Generates or reads a QR code from the provided input"
    override val cooldown = 5L
    override val subcommands = mapOf(
        SubcommandData("generate", "Generates a QR code from the provided input") to ::generate,
        SubcommandData("read", "Reads a QR code from the provided image")
            .addOption(OptionType.ATTACHMENT, "image", "The image to read a QR code from", true) to ::read,
    )
    override val usages = setOf(
        setOf("text input".toClassicTextUsage()),
        setOf("image".toClassicTextUsage()),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        event.message.attachments.firstOrNull { it.isImage }?.let { attachment ->
            val deferred = event.channel.sendEmbed {
                color = Immutable.SUCCESS
                description = "Trying to read the QR code\u2026"
            }.await()

            attachment.proxy.download().await().use {
                try {
                    val image = ImageIO.read(ByteArrayInputStream(it.readAllBytes()))
                    val source = BufferedImageLuminanceSource(image)
                    val bitmap = BinaryBitmap(HybridBinarizer(source))

                    try {
                        val result = MultiFormatReader().decode(bitmap)
                            ?.takeIf { r -> r.barcodeFormat == BarcodeFormat.QR_CODE }
                            ?: throw NotFoundException.getNotFoundInstance()
                        val resultEmbed = buildEmbed {
                            color = Immutable.SUCCESS
                            description = result.text.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

                            author {
                                name = "QR Reader"
                                iconUrl = event.jda.selfUser.effectiveAvatarUrl
                            }
                        }

                        deferred.editMessageEmbeds(resultEmbed).queue(null) {
                            event.channel.sendMessageEmbeds(resultEmbed).queue()
                        }
                    } catch (_: NotFoundException) {
                        deferred.delete().queue(null) {}

                        throw CommandException("No QR code has been found in the image!")
                    }
                } catch (e: Exception) {
                    deferred.delete().queue(null) {}

                    throw CommandException("${it::class.qualifiedName ?: "An unknown exception"}: " +
                            (e.message ?: "something went wrong!"))
                }
            }
        } ?: run {
            val input = args
                ?: throw CommandException("Neither text content nor image file has been provided in the message!")

            val qr = QRCode.from(input)
                .withSize(768, 768)
                .withCharset("UTF-8")
                .to(ImageType.PNG)
                .stream()

            qr.use {
                val resultEmbed = buildEmbed {
                    color = Immutable.SUCCESS
                    image = "attachment://qr.png"

                    author {
                        name = "QR Generator"
                        iconUrl = event.jda.selfUser.effectiveAvatarUrl
                    }
                }

                val file = FileUpload.fromData(it.toByteArray(), "qr.png")

                event.channel.sendMessageEmbeds(resultEmbed)
                    .setFiles(file)
                    .queue(null) { file.close() }
            }
        }
    }

    private suspend fun generate(event: SlashCommandInteractionEvent) {
        val input = TextInput
            .create("input", "Enter Your Text:", TextInputStyle.PARAGRAPH)
            .build()
        val modal = Modal
            .create("$name-modal", "Generate QR Code")
            .addActionRow(input)
            .build()

        event.replyModal(modal).await()
    }

    private suspend fun read(event: SlashCommandInteractionEvent) {
        val attachment = event.getOption("image")?.asAttachment?.takeIf { it.isImage }
            ?: throw CommandException("No image to read a QR code from is provided!")

        val deferred = event.replyEmbed {
            color = Immutable.SUCCESS
            description = "Trying to read the QR code\u2026"
        }.await()

        attachment.proxy.download().await().use {
            try {
                val image = ImageIO.read(ByteArrayInputStream(it.readAllBytes()))
                val source = BufferedImageLuminanceSource(image)
                val bitmap = BinaryBitmap(HybridBinarizer(source))

                try {
                    val result = MultiFormatReader().decode(bitmap)
                        ?.takeIf { r -> r.barcodeFormat == BarcodeFormat.QR_CODE }
                        ?: throw NotFoundException.getNotFoundInstance()
                    val resultEmbed = buildEmbed {
                        color = Immutable.SUCCESS
                        description = result.text.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

                        author {
                            name = "QR Reader"
                            iconUrl = event.jda.selfUser.effectiveAvatarUrl
                        }
                    }

                    deferred.editOriginalEmbeds(resultEmbed).queue(null) {
                        event.channel.sendMessageEmbeds(resultEmbed).queue()
                    }
                } catch (_: NotFoundException) {
                    deferred.setFailureEmbed("No QR code has been found in the image!").queue(null) {
                        throw CommandException("No QR code has been found in the image!")
                    }

                    return
                }
            } catch (e: Exception) {
                deferred.deleteOriginal().queue(null) {}

                throw CommandException(
                    "${it::class.qualifiedName ?: "An unknown exception"}: " +
                            (e.message ?: "something went wrong!")
                )
            }
        }
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val input = event.getValue("input")?.asString ?: return

        val deferred = event.deferReply().await()

        val qr = QRCode.from(input)
            .withSize(768, 768)
            .withCharset("UTF-8")
            .to(ImageType.PNG)
            .stream()

        val file = qr.use { FileUpload.fromData(it.toByteArray(), "qr.png") }

        val resultEmbed = buildEmbed {
            color = Immutable.SUCCESS
            image = "attachment://qr.png"

            author {
                name = "QR Generator"
                iconUrl = event.jda.selfUser.effectiveAvatarUrl
            }
        }

        try {
            deferred.editOriginalEmbeds(resultEmbed)
                .setFiles(file)
                .await()
        } catch (_: ErrorResponseException) {
            event.messageChannel.sendMessageEmbeds(resultEmbed)
                .setFiles(file)
                .queue(null) { file.close() }
        }
    }

    override suspend fun invoke(event: MessageContextInteractionEvent) {
        val attachmentProxy = event.target.attachments.firstOrNull { it.isImage }?.proxy
            ?: event.target.embeds.firstOrNull { it.image !== null }?.image?.proxy

        attachmentProxy?.let { attachment ->
            if (event.target.contentRaw.isEmpty()) {
                val deferred = event.replyEmbed {
                    color = Immutable.SUCCESS
                    description = "Trying to read the QR code\u2026"
                }.await()

                attachment.download().await().use {
                    try {
                        val image = ImageIO.read(ByteArrayInputStream(it.readAllBytes()))
                        val source = BufferedImageLuminanceSource(image)
                        val bitmap = BinaryBitmap(HybridBinarizer(source))

                        try {
                            val result = MultiFormatReader().decode(bitmap)
                                ?.takeIf { r -> r.barcodeFormat == BarcodeFormat.QR_CODE }
                                ?: throw NotFoundException.getNotFoundInstance()
                            val resultEmbed = buildEmbed {
                                color = Immutable.SUCCESS
                                description = result.text.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

                                author {
                                    name = "QR Reader"
                                    iconUrl = event.jda.selfUser.effectiveAvatarUrl
                                }
                            }

                            deferred.editOriginalEmbeds(resultEmbed).queue(null) {
                                event.messageChannel.sendMessageEmbeds(resultEmbed).queue()
                            }
                        } catch (_: NotFoundException) {
                            deferred.setFailureEmbed("No QR code has been found in the image!").queue(null) {
                                throw CommandException("No QR code has been found in the image!")
                            }

                            return
                        }
                    } catch (e: Exception) {
                        deferred.deleteOriginal().queue(null) {}

                        throw CommandException("${it::class.qualifiedName ?: "An unknown exception"}: " +
                                (e.message ?: "something went wrong!"))
                    }
                }
            } else {
                val buttons = setOf(
                    Button.secondary("$name-gen", "Generate"),
                    Button.secondary("$name-read", "Read"),
                    Button.danger("$name-exit", "Exit"),
                )

                val hook = event.replyConfirmation("The message has both image attachment and text content.\n" +
                        "What type of QR code operation would you like the bot to perform?")
                    .addActionRow(buttons)
                    .await()
                    .retrieveOriginal()
                    .await()

                try {
                    val buttonEvent = event.jda.awaitEvent<ButtonInteractionEvent>(
                        15,
                        TimeUnit.MINUTES,
                        waiterProcess {
                            users += event.user.idLong
                            channel = event.messageChannel.idLong
                            command = this@QRCommand
                            invoker = hook.idLong
                        },
                    ) {
                        val cmdName = it.componentId.split("-").first()

                        it.message.idLong == hook.idLong && it.user.idLong == event.user.idLong && cmdName == name
                    } ?: return

                    when (buttonEvent.componentId.split("-").last()) {
                        "exit" -> buttonEvent.message.delete().queue()
                        "gen" -> {
                            val deferred = buttonEvent.deferEdit()
                                .setComponents(emptyList())
                                .await()

                            val qr = QRCode.from(event.target.contentRaw)
                                .withSize(768, 768)
                                .withCharset("UTF-8")
                                .to(ImageType.PNG)
                                .stream()

                            val file = qr.use { FileUpload.fromData(it.toByteArray(), "qr.png") }

                            val resultEmbed = buildEmbed {
                                color = Immutable.SUCCESS
                                image = "attachment://qr.png"

                                author {
                                    name = "QR Generator"
                                    iconUrl = event.jda.selfUser.effectiveAvatarUrl
                                }
                            }

                            try {
                                deferred.editOriginalAttachments(file)
                                    .setEmbeds(resultEmbed)
                                    .await()
                            } catch (_: ErrorResponseException) {
                                buttonEvent.messageChannel
                                    .sendMessageEmbeds(resultEmbed)
                                    .setFiles(file)
                                    .queue(null) { file.close() }
                            }
                        }
                        "read" -> {
                            val deferred = buttonEvent.editComponents().setEmbeds(buildEmbed {
                                color = Immutable.SUCCESS
                                description = "Trying to read the QR code\u2026"
                            }).await()

                            attachment.download().await().use {
                                try {
                                    val image = ImageIO.read(ByteArrayInputStream(it.readAllBytes()))
                                    val source = BufferedImageLuminanceSource(image)
                                    val bitmap = BinaryBitmap(HybridBinarizer(source))

                                    try {
                                        val result = MultiFormatReader().decode(bitmap)
                                            ?.takeIf { r -> r.barcodeFormat == BarcodeFormat.QR_CODE }
                                            ?: throw NotFoundException.getNotFoundInstance()
                                        val resultEmbed = buildEmbed {
                                            color = Immutable.SUCCESS
                                            description = result.text.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

                                            author {
                                                name = "QR Reader"
                                                iconUrl = event.jda.selfUser.effectiveAvatarUrl
                                            }
                                        }

                                        deferred.editOriginalEmbeds(resultEmbed).queue(null) {
                                            buttonEvent.messageChannel.sendMessageEmbeds(resultEmbed).queue()
                                        }
                                    } catch (_: NotFoundException) {
                                        deferred.setFailureEmbed("No QR code has been found in the image!").queue(null) {
                                            throw CommandException("No QR code has been found in the image!")
                                        }

                                        return
                                    }
                                } catch (e: Exception) {
                                    deferred.deleteOriginal().queue(null) {}

                                    throw CommandException("${it::class.qualifiedName ?: "An unknown exception"}: " +
                                            (e.message ?: "something went wrong!"))
                                }
                            }
                        }
                    }

                    return
                } catch (_: TimeoutCancellationException) {
                    val embed = defaultEmbed("Time is out!", EmbedType.FAILURE) {
                        text = "This message will self-delete in 5 seconds"
                    }

                    hook.editMessageComponents().setEmbeds(embed).queue({
                        it.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                    }) {}
                }
            }
        } ?: run {
            val input = event.target.contentRaw.takeUnless { it.isEmpty() }
                ?: throw CommandException("Neither text content nor image file has been provided in the message!")

            val deferred = event.deferReply().await()

            val qr = QRCode.from(input)
                .withSize(768, 768)
                .withCharset("UTF-8")
                .to(ImageType.PNG)
                .stream()

            val file = qr.use { FileUpload.fromData(it.toByteArray(), "qr.png") }

            val resultEmbed = buildEmbed {
                color = Immutable.SUCCESS
                image = "attachment://qr.png"

                author {
                    name = "QR Generator"
                    iconUrl = event.jda.selfUser.effectiveAvatarUrl
                }
            }

            try {
                deferred.editOriginalEmbeds(resultEmbed)
                    .setFiles(file)
                    .await()
            } catch (_: ErrorResponseException) {
                event.messageChannel.sendMessageEmbeds(resultEmbed)
                    .setFiles(file)
                    .queue(null) { file.close() }
            }
        }
    }
}