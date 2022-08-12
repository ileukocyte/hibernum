package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.ClassicTextOnlyCommand
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.MessageContextOnlyCommand
import io.ileukocyte.hibernum.commands.usageGroupOf
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.escapeMarkdown
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.sticker.Sticker.StickerFormat
import net.dv8tion.jda.api.entities.sticker.StickerItem
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class StickerCommand : ClassicTextOnlyCommand, MessageContextOnlyCommand {
    override val name = "sticker"
    override val contextName = "Sticker Information"
    override val description = "Sends the available information about the provided sticker"
    override val aliases = setOf("sticker-info")
    override val usages = setOf(
        usageGroupOf("sticker".toClassicTextUsage()),
        usageGroupOf("reply to a sticker message".toClassicTextUsage()),
    )

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val sticker = event.message.let {
            it.stickers.firstOrNull() ?: it.messageReference?.resolve()
                ?.await()
                ?.stickers
                ?.firstOrNull()
        } ?: throw CommandException("No sticker has been provided!")

        val response = stickerEmbed(sticker, event.jda, event.guild)

        event.channel.sendMessageEmbeds(response.applyIf(response.color === null) {
            EmbedBuilder(response).setColor(Immutable.SUCCESS).build()
        }).queue()
    }

    override suspend fun invoke(event: MessageContextInteractionEvent) {
        val sticker = event.target.stickers.firstOrNull()
            ?: throw CommandException("No sticker is present in the message!")

        try {
            val deferred = event.deferReply().await()

            val response = stickerEmbed(sticker, event.jda, event.guild ?: return)

            deferred.editOriginalEmbeds(response.applyIf(response.color === null) {
                EmbedBuilder(response).setColor(Immutable.SUCCESS).build()
            }).await()
        } catch (_: ErrorResponseException) {
            val response = stickerEmbed(sticker, event.jda, event.guild ?: return)

            event.messageChannel.sendMessageEmbeds(response.applyIf(response.color === null) {
                EmbedBuilder(response).setColor(Immutable.SUCCESS).build()
            }).queue()
        }
    }

    private suspend fun stickerEmbed(
        sticker: StickerItem,
        jda: JDA,
        currentGuild: Guild,
    ) = buildEmbed {
        val icon = sticker.iconUrl.takeUnless { sticker.formatType == StickerFormat.LOTTIE }
        val richSticker = jda.retrieveSticker(sticker).await()

        val asGuildSticker = try {
            richSticker.asGuildSticker()
        } catch (_: IllegalStateException) {
            null
        }

        val asStandardSticker = try {
            richSticker.asStandardSticker()
        } catch (_: IllegalStateException) {
            null
        }

        icon?.let {
            color = getDominantColorByImageUrl(it)
            image = "$it?size=2048"
        }

        author {
            name = "Sticker"
            iconUrl = icon
        }

        field {
            title = "Name"
            description = sticker.name
            isInline = true
        }

        field {
            title = "ID"
            description = sticker.id
            isInline = true
        }

        asGuildSticker?.let { gs ->
            field {
                title = "Server"
                description = gs.guild?.name?.escapeMarkdown() ?: "Unknown"
                isInline = true
            }

            field {
                title = "Uploader"
                description = gs.retrieveOwner().await().let {
                    if (it !== null) {
                        if (currentGuild.isMember(it)) {
                            it.asMention
                        } else {
                            it.asTag
                        }
                    } else {
                        "Unknown"
                    }
                }
                isInline = true
            }

            field {
                title = "Tags"
                description = gs.tags.joinToString().ifEmpty { "None" }
                isInline = true
            }

            field {
                title = "Description"
                description = gs.description.ifEmpty { "None" }
                isInline = description == "None"
            }
        }

        asStandardSticker?.let { ss ->
            field {
                title = "Built-In"
                description = "Yes"
                isInline = true
            }

            field {
                title = "Tags"
                description = ss.tags.joinToString().ifEmpty { "None" }
                isInline = true
            }

            field {
                title = "Description"
                description = ss.description.ifEmpty { "None" }
                isInline = description == "None"
            }

            jda.retrieveNitroStickerPacks().await().firstOrNull { ss in it.stickers }?.let { pack ->
                field {
                    title = "Sticker Pack"
                    description = pack.name
                    isInline = true
                }

                field {
                    title = "Pack ID"
                    description = pack.id
                    isInline = true
                }

                pack.bannerUrl?.let {
                    val hq = "$it?size=2048"

                    if (ss.formatType == StickerFormat.LOTTIE) {
                        image = hq
                        color = getDominantColorByImageUrl(it)
                    } else {
                        field {
                            title = "Pack Banner Image"
                            description = "[Pack Banner URL]($hq)"
                            isInline = true
                        }
                    }
                }

                field {
                    title = "Pack Description"
                    description = pack.description
                }
            }
        }
    }
}