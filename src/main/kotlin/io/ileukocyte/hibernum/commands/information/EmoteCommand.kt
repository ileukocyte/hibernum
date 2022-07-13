package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.asWord
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.MarkdownSanitizer

class EmoteCommand : TextCommand {
    override val name = "emote"
    override val description = "Sends the available information about the provided **custom** emoji"
    override val aliases = setOf(
        "customemoji",
        "custom-emoji",
        "emoji",
        "emojiinfo",
        "emoji-info",
        "emoteinfo",
        "emote-info",
    )
    override val options = setOf(
        OptionData(OptionType.STRING, "emote", "The custom emoji to check information about", true))
    override val usages = setOf(setOf("custom emoji"))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val emote = event.message.mentions.customEmojis.firstOrNull()
            ?: throw CommandException("No **custom** emoji has been provided!")

        event.channel.sendMessageEmbeds(emoteEmbed(emote, event.jda, event.guild)).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val regex = "<a?:\\w+:\\d{18}>".toRegex()
        val emote = event.getOption("emote")
            ?.asString
            ?.let { regex.find(it) }
            ?.let { Emoji.fromFormatted(it.value) as? CustomEmoji }
            ?: throw CommandException("No **custom** emoji has been provided!")

        try {
            event.replyEmbeds(emoteEmbed(emote, event.jda, event.guild ?: return)).queue()
        } catch (_: Exception) {
            throw CommandException("No **custom** emoji has been provided!")
        }
    }

    private suspend fun emoteEmbed(emote: CustomEmoji, jda: JDA, currentGuild: Guild) = buildEmbed {
        val richEmoji = jda.emojiCache.getElementById(emote.idLong)
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
            description = richEmoji?.guild?.name?.let { MarkdownSanitizer.escape(it) } ?: "Unknown"
            isInline = true
        }

        field {
            title = "Uploader"
            description = richEmoji?.retrieveOwner()?.await()?.let {
                if (currentGuild.isMember(it)) {
                    it.asMention
                } else {
                    MarkdownSanitizer.escape(it.asTag)
                }
            } ?: "Unknown"
            isInline = true
        }

        field {
            title = "ID"
            description = emote.id
            isInline = true
        }

        field {
            title = "Animated"
            description = emote.isAnimated.asWord
            isInline = true
        }

        field {
            val timestamp = emote.timeCreated.toEpochSecond()

            title = "Creation Date"
            description = "<t:$timestamp:F> (<t:$timestamp:R>)"
            isInline = true
        }
    }
}