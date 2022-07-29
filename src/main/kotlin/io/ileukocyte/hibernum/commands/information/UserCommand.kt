package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.UserContextOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.getDominantColorByImageProxy

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.utils.TimeFormat.DATE_TIME_LONG
import net.dv8tion.jda.api.utils.TimeFormat.RELATIVE

class UserCommand : TextCommand, UserContextOnlyCommand {
    override val name = "user"
    override val contextName = "User Information"
    override val description = "Sends either detailed information about the specified user's account or their profile picture"
    override val aliases = setOf("member", "member-info", "user-info")
    override val usages = setOf(
        setOf("user name".toClassicTextUsage(true)),
        setOf("user mention".toClassicTextUsage(true)),
        setOf("user ID".toClassicTextUsage(true)),
    )
    override val options = setOf(
        OptionData(OptionType.USER, "user", "The user to check information about"))
    override val cooldown = 3L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        event.guild.takeUnless { it.isLoaded }?.loadMembers()?.await()

        if (args !== null) {
            val exception = CommandException("No users have been found by the query!")

            when {
                args matches Regex("\\d{17,20}") ->
                    chooseUserInfoOrPfp(event.guild.getMemberById(args) ?: throw exception, event.author, event)
                event.message.mentions.membersBag.isNotEmpty() ->
                    chooseUserInfoOrPfp(event.message.mentions.membersBag.firstOrNull() ?: return, event.author, event)
                args matches User.USER_TAG.toRegex() ->
                    chooseUserInfoOrPfp(event.guild.getMemberByTag(args) ?: throw exception, event.author, event)
                else -> {
                    val results = event.guild.searchMembers(args)
                        .takeUnless { it.isEmpty() }
                        ?.filterNotNull()
                        ?: throw exception

                    if (results.size == 1) {
                        chooseUserInfoOrPfp(results.first(), event.author, event)

                        return
                    }

                    val menu = SelectMenu
                        .create("$name-${event.author.idLong}-search")
                        .addOptions(
                            *results.filter { !it.user.isBot }.take(10).map { SelectOption.of(it.user.asTag, it.user.id) }.toTypedArray(),
                            SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C")),
                        ).build()

                    event.channel.sendEmbed {
                        color = Immutable.SUCCESS
                        description = "Select the user you want to check information about!"
                    }.setActionRow(menu).queue()
                }
            }
        } else {
            chooseUserInfoOrPfp(event.member ?: return, event.author, event)
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val member = event.getOption("user")?.asMember ?: event.member
            ?: return

        chooseUserInfoOrPfp(member, event.user, event, event.guild)
    }

    override suspend fun invoke(event: UserContextInteractionEvent) =
        chooseUserInfoOrPfp(event.targetMember!!, event.user, event, event.guild)

    override suspend fun invoke(event: SelectMenuInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            event.message.delete().queue()

            val value = event.selectedOptions.firstOrNull()?.value
                ?.takeUnless { it == "exit" }
                ?: return

            chooseUserInfoOrPfp(event.guild?.getMemberById(value) ?: return, event.user, event)
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val type = id.last()

            if (type == "exit") {
                event.message.delete().queue()

                return
            }

            try {
                val deferred = event.deferEdit().await()

                val member = event.guild?.getMemberById(id[1]) ?: return

                val embed = when (type) {
                    "info" -> infoEmbed(member)
                    "pfp" -> pfpEmbed(member)
                    else -> return
                }

                deferred.editOriginalEmbeds(embed).setComponents(emptyList()).await()
            } catch (_: ErrorResponseException) {
                val member = event.guild?.getMemberById(id[1]) ?: return

                val embed = when (type) {
                    "info" -> infoEmbed(member)
                    "pfp" -> pfpEmbed(member)
                    else -> return
                }

                event.channel.sendMessageEmbeds(embed).queue()
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun <E: GenericEvent> chooseUserInfoOrPfp(
        member: Member,
        author: User,
        event: E,
        guild: Guild? = null,
    ) {
        guild?.takeUnless { it.isLoaded }?.loadMembers()?.await()

        val user = member.user

        if (user.isBot) {
            when (event) {
                is MessageReceivedEvent -> event.channel.sendMessageEmbeds(pfpEmbed(member)).queue()
                is GenericCommandInteractionEvent -> try {
                    event.replyEmbeds(pfpEmbed(member)).await()
                } catch (_: ErrorResponseException) {
                    event.messageChannel.sendMessageEmbeds(pfpEmbed(member)).queue()
                }
            }

            return
        }

        val info = Button.secondary("$name-${author.idLong}-${user.idLong}-info", "Information")
        val pfp = Button.secondary("$name-${author.idLong}-${user.idLong}-pfp", "Profile Picture")
        val exit = Button.danger("$name-${author.idLong}-exit", "Exit")

        when (event) {
            is MessageReceivedEvent ->
                event.channel.sendConfirmation("Choose the type of information that you want to check!")
                    .setActionRow(info, pfp, exit)
                    .queue()
            is SelectMenuInteractionEvent ->
                event.channel.sendConfirmation("Choose the type of information that you want to check!")
                    .setActionRow(info, pfp, exit)
                    .queue()
            is GenericCommandInteractionEvent ->
                event.replyConfirmation("Choose the type of information that you want to check!")
                    .addActionRow(info, pfp, exit)
                    .queue()
        }
    }

    private suspend fun infoEmbed(member: Member) = buildEmbed {
        val user = member.user

        color = getDominantColorByImageProxy(user.effectiveAvatar)
        thumbnail = user.effectiveAvatarUrl

        author {
            name = user.asTag
            iconUrl = user.effectiveAvatarUrl
        }

        field {
            title = "Username"
            description = user.name.escapeMarkdown()
            isInline = true
        }

        field {
            title = "Nickname"
            description = member.nickname?.escapeMarkdown() ?: "None"
            isInline = true
        }

        field {
            title = "ID"
            description = member.id
            isInline = true
        }

        field {
            val clients = setOf(ClientType.DESKTOP, ClientType.MOBILE, ClientType.WEB)
                .associateWith { member.getOnlineStatus(it).name.replace('_', ' ').capitalizeAll() }

            title = "Online Status"
            description = clients.map { (k, v) -> "${k.name.capitalizeAll()}: $v" }.joinToString("\n")
            isInline = true
        }

        member.activities.let { activities ->
            fun ActivityType.humanized() = when (this) {
                ActivityType.PLAYING -> "Playing"
                ActivityType.STREAMING -> "Streaming"
                ActivityType.LISTENING -> "Listening To"
                ActivityType.WATCHING -> "Watching"
                ActivityType.COMPETING -> "Competing"
                else -> null
            }

            field {
                title = "Custom Status"
                description = activities.firstOrNull { it.type == ActivityType.CUSTOM_STATUS }?.let { cs ->
                    cs.emoji
                        ?.takeIf { it is RichCustomEmoji || it.type == Emoji.Type.UNICODE }
                        ?.let { "${it.formatted} " }
                        .orEmpty() + cs.name
                } ?: "None"
                isInline = true
            }

            activities.mapNotNull { it.takeUnless { a -> a.type == ActivityType.CUSTOM_STATUS } }.let { a ->
                field {
                    title = "Activities"
                    description = a.joinToString("\n") {
                        "${it.type.humanized()}: ${it.name}"
                    }.takeUnless { it.isEmpty() } ?: "None"
                    isInline = true
                }
            }
        }

        field {
            val timestamp = member.timeCreated

            title = "Registration Date"
            description = "${DATE_TIME_LONG.format(timestamp)} (${RELATIVE.format(timestamp)})"
            isInline = true
        }

        field {
            val timestamp = member.timeJoined

            title = "Join Date"
            description = "${DATE_TIME_LONG.format(timestamp)} (${RELATIVE.format(timestamp)})"
            isInline = true
        }

        field {
            title = "Color"
            description = member.color?.let { "#%02x%02x%02x".format(it.red, it.green, it.blue) } ?: "Default"
            isInline = true
        }

        field {
            title = "Server Status"
            description = when {
                member.isOwner -> "Owner"
                member.hasPermission(Permission.ADMINISTRATOR) -> "Administrator"
                member.hasPermission(Permission.MANAGE_SERVER) || member.hasPermission(Permission.MESSAGE_MANAGE) -> "Moderator"
                else -> "None"
            }
            isInline = true
        }

        field {
            title = "Voice Channel"
            description = member.voiceState?.channel?.asMention ?: "None"
            isInline = true
        }

        field {
            title = "Server Booster"
            description = (member.timeBoosted !== null).asWord
            isInline = true
        }

        user.retrieveProfile().await().let { profile ->
            field {
                title = "Banner ${profile.bannerUrl?.let { "Image" } ?: "Color"}"
                description = profile.bannerUrl?.let { "[Banner URL](${it}?size=2048)" }
                    ?: profile.accentColor?.let { "#%02x%02x%02x".format(it.red, it.green, it.blue) }
                    ?: "Default"
                isInline = true
            }
        }

        if (user.flags.isNotEmpty()) {
            field {
                title = "User Flags"
                description = user.flags.sortedBy { it.offset }.joinToString("\n") { it.getName() }
                isInline = true
            }
        }

        member.permissions.intersect(FEATURED_PERMISSIONS).takeUnless { it.isEmpty() }?.let { permissions ->
            field {
                title = "Featured Permissions"
                description = permissions
                    .sortedBy { it.getName() }
                    .joinToString { it.getName() }
            }
        }

        member.roles.filter { !it.isPublicRole }.let { roles ->
            field {
                title = "Roles" + " (${roles.size})".takeUnless { roles.isEmpty() }.orEmpty()
                description = roles.takeUnless { it.isEmpty() }
                    ?.joinToString { it.name }
                    ?.escapeMarkdown()
                    ?.limitTo(MessageEmbed.VALUE_MAX_LENGTH)
                    ?: "None"
                isInline = roles.isEmpty()
            }
        }
    }

    private suspend fun pfpEmbed(member: Member) = buildEmbed {
        "${member.user.effectiveAvatarUrl}?size=2048".let { pfp ->
            author {
                name = member.user.asTag
                iconUrl = pfp
            }

            description = "[Profile Picture URL]($pfp)".bold()
            image = pfp
            color = getDominantColorByImageProxy(member.user.effectiveAvatar)

            member.avatarUrl?.let { guildPfp ->
                append("\u2022".surroundWith(' '))
                append("[Local Profile Picture]($guildPfp?size=2048)".bold())
            }
        }
    }

    internal companion object {
        @JvmField
        val FEATURED_PERMISSIONS = setOf(
            Permission.ADMINISTRATOR,
            Permission.BAN_MEMBERS,
            Permission.CREATE_INSTANT_INVITE,
            Permission.CREATE_PRIVATE_THREADS,
            Permission.CREATE_PUBLIC_THREADS,
            Permission.KICK_MEMBERS,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_EMOJIS_AND_STICKERS,
            Permission.MANAGE_PERMISSIONS,
            Permission.MANAGE_ROLES,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_THREADS,
            Permission.MANAGE_WEBHOOKS,
            Permission.MESSAGE_MANAGE,
            Permission.MESSAGE_MENTION_EVERYONE,
            Permission.MESSAGE_TTS,
            Permission.MODERATE_MEMBERS,
            Permission.NICKNAME_MANAGE,
            Permission.PRIORITY_SPEAKER,
            Permission.VIEW_AUDIT_LOGS,
            Permission.VIEW_GUILD_INSIGHTS,
            Permission.VOICE_DEAF_OTHERS,
            Permission.VOICE_MOVE_OTHERS,
            Permission.VOICE_MUTE_OTHERS,
        )
    }
}