package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.capitalizeAll
import io.ileukocyte.hibernum.extensions.getSearchPriority
import io.ileukocyte.hibernum.extensions.surroundWith
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asText

import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.GenericAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class HelpCommand : TextCommand {
    override val name = "help"
    override val description = "Sends a list of all the bot's commands and provides the user with the documentation"
    override val usages = setOf(setOf("command name".toClassicTextUsage(true)))
    override val options = setOf(
        OptionData(OptionType.STRING, "command", "The command to provide help for")
            .setAutoComplete(true))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (args !== null) {
            CommandHandler[args.lowercase()]?.let {
                event.channel.sendMessageEmbeds(it.getHelp(event.jda)).queue()
            } ?: throw CommandException("The provided command name is invalid!")
        } else {
            val inviteLink = Immutable.INVITE_LINK_FORMAT.format(
                event.jda.selfUser.id,
                event.jda.retrieveApplicationInfo().await().permissionsRaw,
            )

            val buttons = setOf(
                Button.link(inviteLink, "Invite Link"),
                Button.link(Immutable.GITHUB_REPOSITORY, "GitHub Repository"),
            )

            try {
                val dm = event.author.openPrivateChannel().await()

                dm.sendMessageEmbeds(getCommandList(event.jda, event.author, false))
                    .setActionRow(buttons)
                    .await()

                event.message.addReaction(Emoji.fromUnicode("\u2705")).queue(null) {}
            } catch (_: ErrorResponseException) {
                event.channel.sendMessageEmbeds(getCommandList(event.jda, event.author,
                    isFromSlashCommand = false,
                    isInDm = false,
                )).setActionRow(buttons).queue()
            }
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val option = event.getOption("command")?.asString?.lowercase()

        if (option !== null) {
            CommandHandler[option]?.let {
                event.replyEmbeds(it.getHelp(event.jda)).setEphemeral(true).queue()
            } ?: throw CommandException("The provided command name is invalid!")
        } else {
            val inviteLink = Immutable.INVITE_LINK_FORMAT.format(
                event.jda.selfUser.id,
                event.jda.retrieveApplicationInfo().await().permissionsRaw,
            )

            val buttons = setOf(
                Button.link(inviteLink, "Invite Link"),
                Button.link(Immutable.GITHUB_REPOSITORY, "GitHub Repository"),
            )

            event.replyEmbeds(getCommandList(event.jda, event.user, true, isInDm = false))
                .addActionRow(buttons)
                .setEphemeral(true)
                .queue()
        }
    }

    override suspend fun invoke(event: GenericAutoCompleteInteractionEvent) {
        val interaction = event.interaction as CommandAutoCompleteInteraction

        interaction.getOption("command") { option ->
            val query = option.asString

            if (query.isNotEmpty()) {
                searchCommandNames(query).takeUnless { it.isEmpty() }?.let {
                    val commands = it.mapNotNull { name -> CommandHandler[name] }.distinct()
                        .take(OptionData.MAX_CHOICES)

                    event.replyChoiceStrings(commands.map { c -> c.name }).queue()
                }
            } else {
                event.replyChoiceStrings().queue()
            }
        }
    }

    private fun searchCommandNames(query: String) = CommandHandler.map {
        if (it is TextCommand) {
            it.aliases + it.name
        } else {
            setOf(it.name)
        }
    }
        .flatten()
        .filter { getSearchPriority(query, it) > 0 }
        .sortedByDescending { getSearchPriority(query, it) }

    private fun GenericCommand.getHelp(jda: JDA) = buildEmbed {
        val nameWithPrefix = "${Immutable.DEFAULT_PREFIX.takeUnless { this@getHelp is SlashOnlyCommand } ?: "/"}$name"

        color = Immutable.SUCCESS
        description = this@getHelp.fullDescription

        if (this@getHelp is TextCommand) {
            if (aliases.isNotEmpty()) {
                field {
                    title = "Classic Text Aliases"
                    description = aliases.sorted().joinToString { "${Immutable.DEFAULT_PREFIX}$it" }
                }
            }

            if (this@getHelp is SubcommandHolder) {
                for (subcommand in subcommands.keys) {
                    field {
                        title = "${subcommand.name.capitalizeAll()} Subcommand"
                        description = "${subcommand.description}\n\n/$name ${subcommand.name} ${
                            subcommand.options.joinToString(" ") { "<${it.name}>" }
                        }\n\n" + subcommand.options.joinToString("\n") { o ->
                            val description by lazy {
                                if (o.description.split("\\s+".toRegex(), 2)
                                        .first()
                                        .substring(1)
                                        .any { !it.isLowerCase() }
                                ) {
                                    o.description
                                } else {
                                    o.description.replaceFirstChar { it.lowercase() }
                                }
                            }

                            "<${o.name}>${
                                " (optional)".takeUnless { o.isRequired }.orEmpty()
                            } — $description"
                        }
                    }
                }
            }

            if (usages.isNotEmpty()) {
                field {
                    title = "Classic Text Usages"
                    description = usages.joinToString("\n") { group ->
                        "$nameWithPrefix ${group.joinToString(" ") { usage ->
                            usage.toString()
                                .applyIf(usage.isOptional) { "$this (optional)" }
                                .applyIf(usage.applyDefaultAffixes) { surroundWith("<", ">") }
                        }}"
                    }
                }
            }

            if (this@getHelp !is SubcommandHolder && options.isNotEmpty()) {
                field {
                    title = "Slash Options"
                    description = "/$name ${options.joinToString(" ") { "<${it.name}>" }}\n\n" +
                            options.joinToString("\n") { o ->
                                val description by lazy {
                                    if (o.description.split("\\s+".toRegex(), 2)
                                            .first()
                                            .substring(1)
                                            .any { !it.isLowerCase() }
                                    ) {
                                        o.description
                                    } else {
                                        o.description.replaceFirstChar { it.lowercase() }
                                    }
                                }

                                "<${o.name}>${
                                    " (optional)".takeUnless { o.isRequired }.orEmpty()
                                } — $description"
                            }
                }
            }
        }

        field {
            title = "Category"
            description = category.toString()
        }

        if (cooldown > 0) {
            field {
                title = "Cooldown"
                description = asText(cooldown, TimeUnit.SECONDS)
            }
        }

        field {
            title = "Input Types"
            description = inputTypes.joinToString("\n") {
                val contextInputTypes = setOf(
                    GenericCommand.InputType.MESSAGE_CONTEXT_MENU,
                    GenericCommand.InputType.USER_CONTEXT_MENU,
                )

                if (this@getHelp is ContextCommand && it in contextInputTypes) {
                    "$it (\"$contextName\")"
                } else {
                    "$it"
                }
            }
        }

        author {
            name = nameWithPrefix +
                    " (text-only)".takeIf { this@getHelp is ClassicTextOnlyCommand }.orEmpty() +
                    " (slash-only)".takeIf { this@getHelp is SlashOnlyCommand }.orEmpty()
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }
    }

    private fun getCommandList(
        jda: JDA,
        author: User,
        isFromSlashCommand: Boolean,
        isInDm: Boolean = true,
    ) = buildEmbed {
        color = Immutable.SUCCESS

        appendLine("Commands in italics can only be used either as classic text commands " +
                "(the ones prefixed with \"${Immutable.DEFAULT_PREFIX}\") " +
                "or slash commands (the ones prefixed with \"/\")!")
        appendLine("The rest of commands can be used either way!")

        author {
            name = "${jda.selfUser.name} Help"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        if (!isInDm && !isFromSlashCommand) {
            footer {
                text = "Requested by ${author.asTag}"
                iconUrl = author.effectiveAvatarUrl
            }
        }

        for ((category, cmd) in CommandHandler.groupBy { it.category }.toSortedMap())
            field {
                title = "$category Commands"
                description = cmd.sortedBy { it.name }.joinToString { cmd ->
                    when (cmd) {
                        is ClassicTextOnlyCommand -> "*${Immutable.DEFAULT_PREFIX}${cmd.name}*"
                        is SlashOnlyCommand -> "*/${cmd.name}*"
                        else -> cmd.name
                    }
                }
            }

        if (!isFromSlashCommand) {
            footer { text = "Try using ${jda.selfUser.name}'s slash command menu via typing \"/\" on a server!" }
        }
    }
}