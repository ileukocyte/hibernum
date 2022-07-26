package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.asText
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.MarkdownSanitizer

class ServerCommand : TextCommand {
    override val name = "server"
    override val description = "Sends the server's icon, its list of custom emojis, or detailed information about it"
    override val aliases = setOf("guild", "guildinfo", "guild-info", "serverinfo", "server-info")
    override val cooldown = 5L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val buttons by lazy {
            val info = Button.secondary("$name-${event.author.idLong}-info", "Information")
            val icon = Button.secondary("$name-${event.author.idLong}-icon", "Server Icon")
                .takeUnless { event.guild.iconUrl === null }
            val emotes = Button.secondary("$name-${event.author.idLong}-emotes", "Custom Emojis")
                .takeUnless { event.guild.emojiCache.isEmpty }

            setOfNotNull(info, icon, emotes)
        }

        if (buttons.size == 1) {
            event.guild.takeUnless { it.isLoaded }?.loadMembers()?.await()

            event.channel.sendMessageEmbeds(infoEmbed(event.guild)).queue()

            return
        }

        event.channel.sendConfirmation("Choose the type of information that you want to check!")
            .setActionRow(
                *buttons.toTypedArray(),
                Button.danger("$name-${event.author.idLong}-exit", "Exit"),
            ).queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return

        val buttons by lazy {
            val info = Button.secondary("$name-${event.user.idLong}-info", "Information")
            val icon = Button.secondary("$name-${event.user.idLong}-icon", "Server Icon")
                .takeUnless { guild.iconUrl === null }
            val emotes = Button.secondary("$name-${event.user.idLong}-emotes", "Custom Emojis")
                .takeUnless { guild.emojiCache.isEmpty }

            setOfNotNull(info, icon, emotes)
        }

        if (buttons.size == 1) {
            val deferred = event.deferReply().await()

            guild.takeUnless { it.isLoaded }?.loadMembers()?.await()

            deferred.editOriginalEmbeds(infoEmbed(guild)).queue()

            return
        }

        event.replyConfirmation("Choose the type of information that you want to check!")
            .addActionRow(
                *buttons.toTypedArray(),
                Button.danger("$name-${event.user.idLong}-exit", "Exit"),
            ).queue()
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val type = id.last()

            if (type == "exit") {
                event.message.delete().queue()

                return
            }

            val guild = event.guild ?: return
            val deferred = event.editMessage("Executing\u2026".italics())
                .setEmbeds()
                .setComponents(emptyList())
                .await()

            val embed = when (type) {
                "info" -> {
                    guild.takeUnless { it.isLoaded }?.loadMembers()?.await()

                    infoEmbed(guild)
                }
                "icon" -> iconEmbed(guild)
                "emotes" -> emotesEmbed(guild)
                else -> return
            }

            deferred.editOriginal(ZERO_WIDTH_SPACE)
                .setEmbeds(embed)
                .queue()
        } else throw CommandException("You did not invoke the initial command!")
    }

    private suspend fun infoEmbed(guild: Guild) = buildEmbed {
        val members = guild.memberCache

        guild.iconUrl?.let { icon ->
            color = getDominantColorByImageUrl(icon)
            thumbnail = icon
        }

        guild.description?.let { appendLine("**Description**: $it") }
        guild.vanityUrl?.let { appendLine("**Vanity URL**: $it") }

        field {
            title = "Name"
            description = MarkdownSanitizer.escape(guild.name)
            isInline = true
        }

        field {
            title = "ID"
            description = guild.id
            isInline = true
        }

        field {
            title = "Owner"
            description = guild.owner?.user?.asMention?.let { MarkdownSanitizer.escape(it) }
                ?: "None (${guild.ownerIdLong})"
            isInline = true
        }

        field {
            title = "Online Members"
            description = "${members.count { it.onlineStatus != OnlineStatus.OFFLINE }}/${members.size()}"
            isInline = true
        }

        members.count { it.user.isBot }.let { bots ->
            field {
                title = "Humans"
                description = "${members.size() - bots}/${members.size()}"
                isInline = true
            }

            field {
                title = "Bots"
                description = "$bots/${members.size()}"
                isInline = true
            }
        }

        field {
            title = "Channel Categories"
            description = "${guild.categoryCache.size()}"
            isInline = true
        }

        field {
            title = "Text Channels"
            description = "${guild.textChannelCache.size()}"
            isInline = true
        }

        field {
            title = "Voice Channels"
            description = "${guild.voiceChannelCache.size()}"
            isInline = true
        }

        field {
            title = "System Channel"
            description = guild.systemChannel?.asMention ?: "None"
            isInline = true
        }

        field {
            title = "Rules Channel"
            description = guild.rulesChannel?.asMention ?: "None"
            isInline = true
        }

        field {
            title = "AFK Voice Channel"
            description = guild.afkChannel?.asMention
                ?.let { "$it (${asText(guild.afkTimeout.seconds * 1000L)})" }
                ?: "None"
            isInline = true
        }

        field {
            title = "Roles"
            description = "${guild.roleCache.size()}"
            isInline = true
        }

        field {
            title = "Custom Emojis"
            description = "${guild.emojiCache.size()}"
            isInline = true
        }

        field {
            title = "Stickers"
            description = "${guild.stickerCache.size()}"
            isInline = true
        }

        field {
            title = "Boosts"
            description = "${guild.boostCount}"
            isInline = true
        }

        field {
            val tier = guild.boostTier

            title = "Boost Tier"
            description = "**Level ${tier.key}**:\n" +
                    "Bitrate: ${tier.maxBitrate / 1000} kbps\n" +
                    "Emojis: ${tier.maxEmojis} slots\n" +
                    "File Size: ${tier.maxFileSize shr 20} MB"
            isInline = true
        }

        field {
            title = "Features"
            description = guild.features.takeUnless { it.isEmpty() }?.joinToString {
                it.replace('_', ' ').capitalizeAll()
                    .replace("Url", "URL")
                    .replace("Vip", "VIP")
            } ?: "None"
            isInline = true
        }

        field {
            title = "Verification Level"
            description = guild.verificationLevel.name.replace('_', ' ').capitalizeAll()
            isInline = true
        }

        field {
            title = "Notification Level"
            description = guild.defaultNotificationLevel.name.replace('_', ' ').capitalizeAll()
            isInline = true
        }

        field {
            title = "NSFW Level"
            description = guild.nsfwLevel.name.replace('_', ' ').capitalizeAll()
            isInline = true
        }

        field {
            title = "MFA Requirement"
            description = (guild.requiredMFALevel == Guild.MFALevel.TWO_FACTOR_AUTH).asWord
            isInline = true
        }

        field {
            val timestamp = guild.timeCreated.toEpochSecond()

            title = "Creation Date"
            description = "<t:$timestamp:F> (<t:$timestamp:R>)"
            isInline = true
        }
    }

    private suspend fun iconEmbed(guild: Guild) = buildEmbed {
        val icon = guild.iconUrl ?: return@buildEmbed

        color = getDominantColorByImageUrl(icon)

        author {
            name = guild.name
            iconUrl = icon
        }

        "$icon?size=2048".let { hqIcon ->
            description = "[Server Icon URL]($hqIcon)".bold()
            image = hqIcon
        }
    }

    private suspend fun emotesEmbed(guild: Guild) = buildEmbed {
        val emotes = guild.emojiCache.filter { it.isAvailable }.map { it.asMention }

        author {
            name = "Custom Emojis"
            iconUrl = guild.iconUrl
        }

        description = emotes.joinToString().let {
            if (it.length > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                it.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH).let { l ->
                    if (!l.removePrefix("\u2026").endsWith(">")) {
                        l.split(", ").toMutableList().apply { removeLast() }
                            .joinToString() + "\u2026"
                    } else l
                }
            } else it
        }

        guild.iconUrl?.let { icon -> color = getDominantColorByImageUrl(icon) }
    }
}