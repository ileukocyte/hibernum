package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.utils.MarkdownSanitizer

class EmojiCommand : TextOnlyCommand {
    override val name = "emoji"
    override val description = "Sends the available information about the provided custom emoji"
    override val aliases = setOf("emote", "emojiinfo", "emoji-info", "emoteinfo", "emote-info")
    override val usages = setOf(setOf("custom emoji"))

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val emote = event.message.emotes.firstOrNull() ?: throw CommandException("No custom emoji has been provided!")

        event.channel.sendEmbed {
            val img = emote.imageUrl

            color = getDominantColorByImageUrl(img)
            image = "$img?size=2048"

            author {
                name = "Custom Emoji"
                iconUrl = img
            }

            field {
                title = "Name"
                description = emote.name
                isInline = true
            }

            field {
                title = "Server"
                description = emote.guild?.name?.let { MarkdownSanitizer.escape(it) } ?: "Unknown"
                isInline = true
            }

            field {
                title = "ID"
                description = emote.id
                isInline = true
            }

            field {
                title = "Animated"
                description = if (emote.isAnimated) "Yes" else "No"
                isInline = true
            }

            field {
                val timestamp = emote.timeCreated.toEpochSecond()

                title = "Creation Date"
                description = "<t:$timestamp:F> (<t:$timestamp:R>)"
                isInline = true
            }
        }.queue()
    }
}