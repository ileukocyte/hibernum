package io.ileukocyte.hibernum.commands.information

import de.androidpit.colorthief.ColorThief

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.*

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import net.dv8tion.jda.api.utils.MarkdownSanitizer

import org.ocpsoft.prettytime.PrettyTime

import java.awt.Color
import java.io.InputStream
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO

class UserCommand : TextOnlyCommand {
    override val name = "user"
    override val description = "Sends either detailed information about the specified user or their profile picture"
    override val aliases = setOf("userinfo")
    override val usages = setOf(setOf("user name/ID (optional)"))
    override val cooldown = 3L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (args !== null) {
            val exception = CommandException("No users have been found by the query!")

            when {
                args matches Regex("\\d{17,20}") ->
                    chooseUserInfoOrPfp(event.guild.getMemberById(args)?.user ?: throw exception, event.author, event.channel)
                event.message.mentionedMembers.isNotEmpty() ->
                    chooseUserInfoOrPfp(event.message.mentionedMembers.firstOrNull()?.user ?: return, event.author, event.channel)
                args matches User.USER_TAG.toRegex() ->
                    chooseUserInfoOrPfp(event.guild.getMemberByTag(args)?.user ?: throw exception, event.author, event.channel)
                else -> {
                    val results = event.guild.searchMembers(args)
                        .takeUnless { it.isEmpty() }
                        ?.filterNotNull()
                        ?: throw exception

                    if (results.size == 1) {
                        chooseUserInfoOrPfp(results.first().user, event.author, event.channel)

                        return
                    }

                    val menu = SelectionMenu
                        .create("$name-${event.author.idLong}-search")
                        .addOptions(
                            *results.filter { !it.user.isBot }.take(10).map { SelectOption.of(it.user.asTag, it.user.id) }.toTypedArray(),
                            SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C"))
                        ).build()

                    event.channel.sendEmbed {
                        color = Immutable.SUCCESS
                        description = "Select the user you want to check information about!"
                    }.setActionRow(menu).queue()
                }
            }
        } else chooseUserInfoOrPfp(event.author, event.author, event.channel)
    }

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val value = event.selectedOptions?.firstOrNull()?.value ?: return

            event.message.delete().queue()

            if (value == "exit")
                return

            chooseUserInfoOrPfp(event.guild?.getMemberById(value)?.user ?: return, event.user, event.textChannel)
        } else throw CommandException("You did not invoke the initial command!")
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            event.message.delete().queue()

            val type = id.last()

            if (type == "exit")
                return

            val member = event.guild?.getMemberById(id[1]) ?: return

            when (type) {
                "info" -> event.channel.sendMessageEmbeds(infoEmbed(member)).queue()
                "pfp" -> event.channel.sendMessageEmbeds(pfpEmbed(member.user)).queue()
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private suspend fun chooseUserInfoOrPfp(user: User, author: User, channel: TextChannel) {
        if (user.isBot) {
            channel.sendMessageEmbeds(pfpEmbed(user)).queue()

            return
        }

        val info = Button.secondary("$name-${author.idLong}-${user.idLong}-info", "Information")
        val pfp = Button.secondary("$name-${author.idLong}-${user.idLong}-pfp", "Profile Picture")
        val exit = Button.danger("$name-${author.idLong}-exit", "Exit")

        channel.sendConfirmation("Choose the type of information that you want to check!")
            .setActionRow(info, pfp, exit)
            .queue()
    }

    private suspend fun infoEmbed(member: Member) = buildEmbed {
        val dateFormatter = DateTimeFormatter.ofPattern("E, d MMM yyyy, h:mm:ss a")
        val user = member.user

        thumbnail = user.effectiveAvatarUrl

        author {
            name = user.asTag
            iconUrl = user.effectiveAvatarUrl
        }

        HttpClient(CIO).get<InputStream>(user.effectiveAvatarUrl).use {
            val bufferedImage = ImageIO.read(it)
            val rgb = ColorThief.getColor(bufferedImage)

            color = Color(rgb[0], rgb[1], rgb[2])
        }

        field {
            title = "Username"
            description = MarkdownSanitizer.escape(user.name)
            isInline = true
        }

        field {
            title = "Nickname"
            description = member.nickname?.let { MarkdownSanitizer.escape(it) } ?: "None"
            isInline = true
        }

        field {
            title = "ID"
            description = member.id
            isInline = true
        }

        field {
            title = "Online Status"
            description = member.onlineStatus.name.replace('_', ' ').capitalizeAll()
            isInline = true

            member.activeClients.takeUnless { it.isEmpty() }?.let { ct ->
                description += ct.joinToString { it.name.capitalizeAll() }.let { " ($it)" }
            }
        }

        member.activities.let { activities ->
            fun ActivityType.humanized() = when (this) {
                ActivityType.DEFAULT -> "Playing"
                ActivityType.STREAMING -> "Streaming"
                ActivityType.LISTENING -> "Listening To"
                ActivityType.WATCHING -> "Watching"
                ActivityType.COMPETING -> "Competing"
                else -> null
            }

            field {
                title = "Custom Status"
                description = activities.firstOrNull { it.type == ActivityType.CUSTOM_STATUS }?.let { cs ->
                    val emoji = cs.emoji?.let { e ->
                        if (e.isEmoji) {
                            Emoji.fromUnicode(e.asCodepoints).name
                        } else {
                            e.asMention
                                .takeUnless { member.jda.emoteCache.getElementById(e.idLong) === null }
                        }
                    }

                    emoji?.let { "$it " }.orEmpty() + cs.name
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
            val time = member.timeCreated.format(dateFormatter).removeSuffix(" GMT")
            val ago = PrettyTime().format(Date.from(member.timeCreated.toInstant()))

            title = "Creation Date"
            description = "$time ($ago)"
            isInline = true
        }

        field {
            val time = member.timeJoined.format(dateFormatter).removeSuffix(" GMT")
            val ago = PrettyTime().format(Date.from(member.timeJoined.toInstant()))

            title = "Join Date"
            description = "$time ($ago)"
            isInline = true
        }

        field {
            title = "Color"
            description = member.color?.rgb?.and(0xffffff)?.toString(16)?.let { "#$it" } ?: "Default"
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
            title = "Booster"
            description = if (member.timeBoosted !== null) "Yes" else "No"
            isInline = true
        }

        member.permissions.intersect(KEY_PERMISSIONS).takeUnless { it.isEmpty() }?.let { keyPermissions ->
            field {
                title = "Key Permissions"
                description = keyPermissions
                    .map { it.name.replace('_', ' ').capitalizeAll().replace("Tts", "TTS") }
                    .sorted()
                    .joinToString()
            }
        }

        member.roles.filter { !it.isPublicRole }.let { roles ->
            field {
                title = "Roles" + " (${roles.size})".takeUnless { roles.isEmpty() }
                description = roles.takeUnless { it.isEmpty() }
                    ?.joinToString { it.name }
                    ?.let { MarkdownSanitizer.escape(it) }
                    ?.limitTo(1024)
                    ?: "None"
                isInline = roles.isEmpty()
            }
        }
    }

    private suspend fun pfpEmbed(user: User) = buildEmbed {
        HttpClient(CIO).get<InputStream>(user.effectiveAvatarUrl).use {
            val bufferedImage = ImageIO.read(it)
            val rgb = ColorThief.getColor(bufferedImage)

            color = Color(rgb[0], rgb[1], rgb[2])
        }

        "${user.effectiveAvatarUrl}?size=2048".let { pfp ->
            author {
                name = user.asTag
                iconUrl = pfp
            }

            description = "[Profile Picture URL]($pfp)".surroundWith("**")
            image = pfp
        }
    }

    internal companion object {
        @JvmField
        val KEY_PERMISSIONS = setOf(
            Permission.ADMINISTRATOR,
            Permission.BAN_MEMBERS,
            Permission.CREATE_INSTANT_INVITE,
            Permission.KICK_MEMBERS,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_EMOTES,
            Permission.MANAGE_PERMISSIONS,
            Permission.MANAGE_ROLES,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_THREADS,
            Permission.MANAGE_WEBHOOKS,
            Permission.MESSAGE_MANAGE,
            Permission.MESSAGE_MENTION_EVERYONE,
            Permission.MESSAGE_TTS,
            Permission.NICKNAME_MANAGE,
            Permission.PRIORITY_SPEAKER,
            Permission.VIEW_AUDIT_LOGS,
            Permission.VIEW_GUILD_INSIGHTS,
            Permission.VOICE_DEAF_OTHERS,
            Permission.VOICE_MOVE_OTHERS,
            Permission.VOICE_MUTE_OTHERS
        )
    }
}