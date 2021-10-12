package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.information.UserCommand.Companion.KEY_PERMISSIONS
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.searchRoles
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.utils.getImageBytes

import java.time.format.DateTimeFormatter
import java.util.Date

import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import net.dv8tion.jda.api.utils.MarkdownSanitizer

import org.ocpsoft.prettytime.PrettyTime

class RoleCommand : Command {
    override val name = "role"
    override val description = "Sends the available information about the specified role"
    override val aliases = setOf("roleinfo", "role-info")
    override val usages = setOf(
        setOf("role name (optional)"),
        setOf("role mention (optional)"),
        setOf("role ID (optional)"),
    )
    override val options = setOf(
        OptionData(OptionType.ROLE, "role", "The role to check information about", true))
    override val cooldown = 3L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (args === null) throw NoArgumentsException

        val exception = CommandException("No users have been found by the query!")

        when {
            args matches Regex("\\d{17,20}") -> {
                val role = event.guild.getRoleById(args) ?: throw exception

                event.channel.sendMessageEmbeds(infoEmbed(role))
                    .let {
                        role.color?.let { c ->
                            it.addFile(
                                c.getImageBytes(150, 150),
                                "${c.rgb.and(0xffffff).toString(16)}.png",
                            )
                        } ?: it
                    }.queue()
            }
            event.message.mentionedRoles.isNotEmpty() -> {
                val role = event.message.mentionedRoles.first()

                event.channel.sendMessageEmbeds(infoEmbed(role))
                    .let {
                        role.color?.let { c ->
                            it.addFile(
                                c.getImageBytes(150, 150),
                                "${c.rgb.and(0xffffff).toString(16)}.png",
                            )
                        } ?: it
                    }.queue()
            }
            else -> {
                val results = event.guild.searchRoles(args)
                    .takeUnless { it.isEmpty() }
                    ?.filterNotNull()
                    ?: throw exception

                if (results.size == 1) {
                    val role = results.first()

                    event.channel.sendMessageEmbeds(infoEmbed(role))
                        .let {
                            role.color?.let { c ->
                                it.addFile(
                                    c.getImageBytes(150, 150),
                                    "${c.rgb.and(0xffffff).toString(16)}.png",
                                )
                            } ?: it
                        }.queue()

                    return
                }

                val menu = SelectionMenu
                    .create("$name-${event.author.idLong}-search")
                    .addOptions(
                        *results.take(10).map { SelectOption.of(it.name, it.id) }.toTypedArray(),
                        SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C")),
                    ).build()

                event.channel.sendEmbed {
                    color = Immutable.SUCCESS
                    description = "Select the role you want to check information about!"
                }.setActionRow(menu).queue()
            }
        }
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val deferred = event.deferReply().await()
        val role = event.getOption("role")?.asRole ?: return

        deferred.editOriginalEmbeds(infoEmbed(role))
            .let {
                role.color?.let { c ->
                    it.addFile(
                        c.getImageBytes(150, 150),
                        "${c.rgb.and(0xffffff).toString(16)}.png",
                    )
                } ?: it
            }.queue()
    }

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val value = event.selectedOptions?.firstOrNull()?.value ?: return

            if (value == "exit") {
                event.message.delete().queue()

                return
            }

            val deferred = event.deferEdit().await()
            val role = event.guild?.getRoleById(value) ?: return

            deferred.editOriginalEmbeds(infoEmbed(role))
                .setActionRows()
                .let {
                    role.color?.let { c ->
                        it.addFile(
                            c.getImageBytes(150, 150),
                            "${c.rgb.and(0xffffff).toString(16)}.png",
                        )
                    } ?: it
                }.queue()
        } else throw CommandException("You did not invoke the initial command!")
    }

    private suspend fun infoEmbed(role: Role) = buildEmbed {
        if (!role.guild.isLoaded) role.guild.loadMembers().await()

        color = role.color

        val color = role.color?.rgb?.and(0xffffff)?.toString(16)

        thumbnail = color?.let { "attachment://$it.png" }

        field {
            title = "Name"
            description = MarkdownSanitizer.escape(role.name)
            isInline = true
        }

        field {
            title = "ID"
            description = role.id
            isInline = true
        }

        field {
            title = "Members"
            description = "${role.guild.getMembersWithRoles(role).size}"
            isInline = true
        }

        field {
            title = "Position"
            description = "#${role.guild.roleCache.indexOf(role).inc()}"
            isInline = true
        }

        field {
            title = "Mentionable"
            description = if (role.isMentionable) "Yes" else "No"
            isInline = true
        }

        field {
            title = "Hoisted"
            description = if (role.isHoisted) "Yes" else "No"
            isInline = true
        }

        field {
            title = "Color"
            description = color?.let { "#$it" } ?: "Default"
            isInline = true
        }

        field {
            val time = role.timeCreated
                .format(DateTimeFormatter.ofPattern("E, d MMM yyyy, h:mm:ss a"))
                .removeSuffix(" GMT")
            val ago = PrettyTime().format(Date.from(role.timeCreated.toInstant()))

            title = "Creation Date"
            description = "$time ($ago)"
            isInline = true
        }

        role.permissions.intersect(KEY_PERMISSIONS).takeUnless { it.isEmpty() }?.let { keyPermissions ->
            field {
                title = "Key Permissions"
                description = keyPermissions
                    .map { it.getName() }
                    .sorted()
                    .joinToString()
            }
        }
    }
}