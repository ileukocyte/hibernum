package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.extensions.surroundWith
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asText

import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class HelpCommand : Command {
    override val name = "help"
    override val description = "Sends a list of all Hibernum's commands and provides the user with the documentation"
    override val usages = setOf(setOf("command name (optional)"))
    override val options =
        setOf(OptionData(OptionType.STRING, "command", "The command to provide help for"))

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (args !== null) {
            val command = CommandHandler[args.lowercase()]
            val action = command?.let { event.channel.sendMessageEmbeds(it.getHelp(event.jda)) }
                ?: throw CommandException("The specified command has not been found!")

            action.queue()
        } else {
            event.author.openPrivateChannel().queue {
                it.sendMessageEmbeds(getCommandList(event.jda, event.author, false)).queue({
                    event.message.addReaction("\u2705").queue(null) {}
                }) {
                    event.channel.sendMessageEmbeds(getCommandList(event.jda, event.author,
                        isFromSlashCommand = false,
                        isInDm = false,
                    )).queue()
                }
            }
        }
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val option = event.getOption("command")

        if (option !== null) {
            CommandHandler[option.asString]
                ?.let { event.replyEmbeds(it.getHelp(event.jda)).setEphemeral(true).queue() }
                ?: throw CommandException("The provided command name is invalid!")
        } else {
            event.replyEmbeds(getCommandList(event.jda, event.user, true, isInDm = false))
                .setEphemeral(true)
                .queue()
        }
    }

    private fun Command.getHelp(jda: JDA) = buildEmbed {
        val nameWithPrefix = "${Immutable.DEFAULT_PREFIX.takeUnless { this@getHelp is SlashOnlyCommand } ?: "/"}$name"

        color = Immutable.SUCCESS
        description = this@getHelp.fullDescription

        if (aliases.isNotEmpty()) {
            field {
                title = "Aliases"
                description = aliases.sorted().joinToString()
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

        if (usages.isNotEmpty()) {
            field {
                title = "Text Usages"
                description =
                    usages.joinToString("\n") { "$nameWithPrefix ${it.joinToString(" ") { u -> "<$u>" }}" }
            }
        }

        if (options.isNotEmpty()) {
            field {
                title = "Slash Options"
                description = "$nameWithPrefix ${options.joinToString(" ") { "<${it.name}>" }}\n\n" +
                        options.joinToString("\n") { o ->
                            "<${o.name}>${
                                " (optional)".takeUnless { o.isRequired }.orEmpty()
                            } — ${o.description.replaceFirstChar { it.lowercase() }}"
                        }
            }
        }

        author {
            name = nameWithPrefix +
                    " (text-only)".takeIf { this@getHelp is TextOnlyCommand }.orEmpty() +
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

        val inviteLink = Immutable.INVITE_LINK_FORMAT.format(jda.selfUser.id)

        appendLine("Commands in italics can only be used either as text commands or slash commands " +
                "(the ones prefixed with \"/\")!")
        appendLine()
        appendLine("**[Invite Link]($inviteLink)** \u2022 **[GitHub Repository](${Immutable.GITHUB_REPOSITORY})**")

        author {
            name = "${jda.selfUser.name} Help"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        if (!isInDm && !isFromSlashCommand)
            footer {
                text = "Requested by ${author.asTag}"
                iconUrl = author.effectiveAvatarUrl
            }

        for ((category, cmd) in CommandHandler.groupBy { it.category }.toSortedMap())
            field {
                title = "$category Commands"
                description = cmd.sortedBy { it.name }.joinToString { cmd ->
                    when (cmd) {
                        is TextOnlyCommand -> cmd.name.surroundWith('*')
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