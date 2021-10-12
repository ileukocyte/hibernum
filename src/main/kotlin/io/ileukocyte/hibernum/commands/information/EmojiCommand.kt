package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import java.time.format.DateTimeFormatter
import java.util.Date

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.utils.MarkdownSanitizer

import org.ocpsoft.prettytime.PrettyTime

class EmojiCommand : TextOnlyCommand {
    override val name = "emoji"
    override val description = "Sends the available information about the provided custom emoji"
    override val aliases = setOf("emote")
    override val usages = setOf(setOf("custom emoji"))

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val emote = event.message.emotes.firstOrNull() ?: throw CommandException()

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
                title = "Guild"
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
                val time = emote.timeCreated
                    .format(DateTimeFormatter.ofPattern("E, d MMM yyyy, h:mm:ss a"))
                    .removeSuffix(" GMT")
                val ago = PrettyTime().format(Date.from(emote.timeCreated.toInstant()))

                title = "Creation Date"
                description = "$time ($ago)"
                isInline = true
            }
        }.queue()
    }
}