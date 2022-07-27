package io.ileukocyte.hibernum.commands.information

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.information.UserCommand.Companion.FEATURED_PERMISSIONS
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.getImageBytes

import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.utils.FileUpload

class RoleCommand : TextCommand {
    override val name = "role"
    override val description = "Sends the available information about the specified role"
    override val aliases = setOf("role-info")
    override val usages = setOf(
        setOf("role name".toClassicTextUsage()),
        setOf("role mention".toClassicTextUsage()),
        setOf("role ID".toClassicTextUsage()),
    )
    override val options = setOf(
        OptionData(OptionType.ROLE, "role", "The role to check information about", true))
    override val cooldown = 3L

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (args === null) {
            throw NoArgumentsException
        }

        val exception = CommandException("No users have been found by the query!")

        when {
            args matches Regex("\\d{17,20}") -> {
                val role = event.guild.getRoleById(args) ?: throw exception

                val file = role.color?.let {
                    FileUpload.fromData(
                        it.getImageBytes(150, 150),
                        "${"%02x%02x%02x".format(it.red, it.green, it.blue)}.png",
                    )
                }

                event.channel.sendMessageEmbeds(infoEmbed(role))
                    .setFiles(setOfNotNull(file))
                    .queue(null) { file?.close() }
            }
            event.message.mentions.rolesBag.isNotEmpty() -> {
                val role = event.message.mentions.rolesBag.first()

                val file = role.color?.let {
                    FileUpload.fromData(
                        it.getImageBytes(150, 150),
                        "${"%02x%02x%02x".format(it.red, it.green, it.blue)}.png",
                    )
                }

                event.channel.sendMessageEmbeds(infoEmbed(role))
                    .setFiles(setOfNotNull(file))
                    .queue(null) { file?.close() }
            }
            else -> {
                val results = event.guild.searchRoles(args)
                    .takeUnless { it.isEmpty() }
                    ?.filterNotNull()
                    ?: throw exception

                if (results.size == 1) {
                    val role = results.first()

                    val file = role.color?.let {
                        FileUpload.fromData(
                            it.getImageBytes(150, 150),
                            "${"%02x%02x%02x".format(it.red, it.green, it.blue)}.png",
                        )
                    }

                    event.channel.sendMessageEmbeds(infoEmbed(role))
                        .setFiles(setOfNotNull(file))
                        .queue(null) { file?.close() }

                    return
                }

                val menu = SelectMenu
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

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val deferred = event.deferReply().await()
        val role = event.getOption("role")?.asRole ?: return

        val file = role.color?.let {
            FileUpload.fromData(
                it.getImageBytes(150, 150),
                "${"%02x%02x%02x".format(it.red, it.green, it.blue)}.png",
            )
        }

        deferred.editOriginalEmbeds(infoEmbed(role))
            .setFiles(setOfNotNull(file))
            .queue(null) {
                file?.close()
            }
    }

    override suspend fun invoke(event: SelectMenuInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val value = event.selectedOptions.firstOrNull()?.value ?: return

            if (value == "exit") {
                event.message.delete().queue()

                return
            }

            val deferred = event.deferEdit().await()
            val role = event.guild?.getRoleById(value) ?: return

            val file = role.color?.let {
                FileUpload.fromData(
                    it.getImageBytes(150, 150),
                    "${"%02x%02x%02x".format(it.red, it.green, it.blue)}.png",
                )
            }

            deferred.editOriginalEmbeds(infoEmbed(role))
                .setComponents(emptyList())
                .setFiles(setOfNotNull(file))
                .queue()
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun infoEmbed(role: Role) = buildEmbed {
        role.guild.takeUnless { it.isLoaded }?.loadMembers()?.await()

        color = role.color

        val color = role.color?.let { "%02x%02x%02x".format(it.red, it.green, it.blue) }

        thumbnail = color?.let { "attachment://$it.png" }
        image = role.icon?.iconUrl?.let { "$it?size=2048" }

        field {
            title = "Name"
            description = role.name.escapeMarkdown()
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
            description = role.isMentionable.asWord
            isInline = true
        }

        field {
            title = "Hoisted"
            description = role.isHoisted.asWord
            isInline = true
        }

        role.tags.let { tags ->
            field {
                title = "Bot Role"
                description = tags.isBot.asWord
                isInline = true
            }

            field {
                title = "Integration Role"
                description = tags.isIntegration.asWord
                isInline = true
            }

            field {
                title = "Booster's Role"
                description = tags.isBoost.asWord
                isInline = true
            }
        }

        field {
            title = "Color"
            description = color?.let { "#$it" } ?: "Default"
            isInline = true
        }

        field {
            val timestamp = role.timeCreated.toEpochSecond()

            title = "Creation Date"
            description = "<t:$timestamp:F> (<t:$timestamp:R>)"
            isInline = true
        }

        role.icon?.emoji?.let {
            field {
                title = "Role Icon Emoji"
                description = it
                isInline = true
            }
        }

        role.permissions.intersect(FEATURED_PERMISSIONS).takeUnless { it.isEmpty() }?.let { permissions ->
            field {
                title = "Featured Permissions"
                description = permissions
                    .sortedBy { it.getName() }
                    .joinToString { it.getName() }
            }
        }
    }
}