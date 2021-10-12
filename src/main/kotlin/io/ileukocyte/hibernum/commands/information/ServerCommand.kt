package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.asText
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import java.time.format.DateTimeFormatter
import java.util.Date

import kotlin.time.ExperimentalTime

import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.utils.MarkdownSanitizer

import org.ocpsoft.prettytime.PrettyTime

class ServerCommand : Command {
    override val name = "server"
    override val description = "Sends the server's icon, its list of custom emojis, or detailed information about it"
    override val aliases = setOf("guild", "guildinfo", "guild-info", "serverinfo", "server-info")
    override val cooldown = 5L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val buttons by lazy {
            val info = Button.secondary("$name-${event.author.idLong}-info", "Information")
            val icon = Button.secondary("$name-${event.author.idLong}-icon", "Server Icon")
                .takeUnless { event.guild.iconUrl === null }
            val emotes = Button.secondary("$name-${event.author.idLong}-emotes", "Custom Emojis")
                .takeUnless { event.guild.emoteCache.isEmpty }

            setOfNotNull(info, icon, emotes)
        }

        if (buttons.size == 1) {
            if (!event.guild.isLoaded) event.guild.loadMembers().await()

            event.channel.sendMessageEmbeds(infoEmbed(event.guild)).queue()

            return
        }

        event.channel.sendConfirmation("Choose the type of information that you want to check!")
            .setActionRow(
                *buttons.toTypedArray(),
                Button.danger("$name-${event.author.idLong}-exit", "Exit"),
            ).queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val guild = event.guild ?: return

        val buttons by lazy {
            val info = Button.secondary("$name-${event.user.idLong}-info", "Information")
            val icon = Button.secondary("$name-${event.user.idLong}-icon", "Server Icon")
                .takeUnless { guild.iconUrl === null }
            val emotes = Button.secondary("$name-${event.user.idLong}-emotes", "Custom Emojis")
                .takeUnless { guild.emoteCache.isEmpty }

            setOfNotNull(info, icon, emotes)
        }

        if (buttons.size == 1) {
            val deferred = event.deferReply().await()

            if (!guild.isLoaded) guild.loadMembers().await()

            deferred.editOriginalEmbeds(infoEmbed(guild)).queue()

            return
        }

        event.replyConfirmation("Choose the type of information that you want to check!")
            .addActionRow(
                *buttons.toTypedArray(),
                Button.danger("$name-${event.user.idLong}-exit", "Exit"),
            ).queue()
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val type = id.last()

            if (type == "exit") {
                event.message.delete().queue()

                return
            }

            val guild = event.guild ?: return
            val deferred = event.editMessage("Executing\u2026".surroundWith('*'))
                .setEmbeds()
                .setActionRows()
                .await()

            when (type) {
                "info" -> {
                    if (event.guild?.isLoaded == false) event.guild?.loadMembers()?.await()

                    deferred.editOriginal(ZERO_WIDTH_SPACE)
                        .setEmbeds(infoEmbed(guild))
                        .queue()
                }
                "icon" ->
                    deferred.editOriginal(ZERO_WIDTH_SPACE)
                        .setEmbeds(iconEmbed(guild))
                        .queue()
                "emotes" ->
                    deferred.editOriginal(ZERO_WIDTH_SPACE)
                        .setEmbeds(emotesEmbed(guild))
                        .queue()
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    @OptIn(ExperimentalTime::class)
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
            description = "${guild.emoteCache.size()}"
            isInline = true
        }

        field {
            val time = guild.timeCreated
                .format(DateTimeFormatter.ofPattern("E, d MMM yyyy, h:mm:ss a"))
                .removeSuffix(" GMT")
            val ago = PrettyTime().format(Date.from(guild.timeCreated.toInstant()))

            title = "Creation Date"
            description = "$time ($ago)"
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
                    "Emojis: ${tier.maxEmotes} slots\n" +
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
            description = if (guild.requiredMFALevel == Guild.MFALevel.TWO_FACTOR_AUTH) "Yes" else "No"
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
            description = "[Server Icon URL]($hqIcon)".surroundWith("**")
            image = hqIcon
        }
    }

    private suspend fun emotesEmbed(guild: Guild) = buildEmbed {
        val emotes = guild.emoteCache.filter { it.isAvailable }.map { it.asMention }

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