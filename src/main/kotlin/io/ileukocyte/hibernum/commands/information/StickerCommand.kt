package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandCategory
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.asWord
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.surroundWith
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.sticker.Sticker.StickerFormat
import net.dv8tion.jda.api.entities.sticker.StickerItem
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.MarkdownSanitizer

class StickerCommand : TextOnlyCommand {
    override val name = "sticker"
    override val description = "N/A"
    override val category = CommandCategory.BETA

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val sticker = event.message.stickers.firstOrNull() ?: return

        event.channel.sendMessageEmbeds(stickerEmbed(sticker, event.jda)).queue()
    }

    private suspend fun stickerEmbed(sticker: StickerItem, jda: JDA) = buildEmbed {
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
        } ?: run {
            color = Immutable.SUCCESS
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
                description = gs.guild?.name?.let { MarkdownSanitizer.escape(it) } ?: "Unknown"
                isInline = true
            }

            field {
                title = "Available"
                description = gs.isAvailable.asWord
                isInline = true
            }

            field {
                title = "Tags"
                description = gs.tags.joinToString {
                    /*val reactionCode = Emoji.fromFormatted(":$it:").asReactionCode

                    if (reactionCode != it) {
                        reactionCode
                    } else {
                        it
                    }*/

                    it
                }.ifEmpty { "None" }
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
                description = ss.tags.joinToString {
                    /*val reactionCode = Emoji.fromFormatted(":$it:").asReactionCode

                    if (reactionCode != it) {
                        reactionCode
                    } else {
                        it
                    }*/

                    it
                }.ifEmpty { "None" }
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

                field {
                    title = "Pack Banner Image"
                    description = pack.bannerUrl?.let { "[Pack Banner URL]($it?size=2048)" } ?: "None"
                    isInline = true
                }

                field {
                    title = "Pack Description"
                    description = pack.description
                }
            }
        }
    }
}