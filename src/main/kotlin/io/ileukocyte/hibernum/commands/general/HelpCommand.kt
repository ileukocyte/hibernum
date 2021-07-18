package io.ileukocyte.hibernum.commands.general

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandCategory
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.utils.asText

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

class HelpCommand : Command {
    override val name = "help"
    override val description = "Sends a list of all Hibernum's commands and provides their documentation"
    override val usages = setOf("command name (optional)")
    override val options by lazy {
        val commands = CommandHandler
            .filter { it !is HelpCommand }
            .sorted()
            .map { Choice(it.name, it.name) }

        setOf(OptionData(OptionType.STRING, "command", "The command to provide help for")
            .addChoices(commands))
    }

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        if (args !== null) {
            val command = CommandHandler[args.lowercase()]

            if (command is HelpCommand) {
                invoke(event, null)
                return
            }

            val action = command?.let { event.channel.sendMessageEmbeds(it.commandHelp(event.jda)) }
                ?: event.channel.sendFailure("The specified command has not been found!")

            action.queue()
        } else {
            event.author.openPrivateChannel().queue {
                it.sendMessageEmbeds(commandList(event.jda, event.author, false)).queue({
                    event.message.addReaction("\u2705").queue(null) {}
                }) {
                    event.channel.sendMessageEmbeds(commandList(event.jda, event.author,
                        isFromSlashCommand = false,
                        isInDm = false
                    )).queue()
                }
            }
        }
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val option = event.getOption("command")

        if (option !== null) {
            CommandHandler[option.asString]
                ?.let { event.replyEmbeds(it.commandHelp(event.jda)).queue() }
        } else {
            event.replyEmbeds(commandList(event.jda, event.user, true, isInDm = false))
                .setEphemeral(true)
                .queue()
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun Command.commandHelp(jda: JDA) = buildEmbed {
        val nameWithPrefix = "${Immutable.DEFAULT_PREFIX}$name"

        color = Immutable.SUCCESS
        description = this@commandHelp.description

        if (aliases.isNotEmpty())
            field {
                title = "Aliases"
                description = aliases.sorted().joinToString()
            }

        field {
            title = "Category"
            description = category.toString()
        }

        if (cooldown > 0)
            field {
                title = "Cooldown"
                description = asText(cooldown, DurationUnit.SECONDS)
            }

        if (usages.isNotEmpty())
            field {
                title = "Text Usages"
                description = usages.joinToString("\n") { "$nameWithPrefix <$it>" }
            }

        if (options.isNotEmpty())
            field {
                title = "Slash Usages"
                description = "$nameWithPrefix ${
                    options.joinToString(" ") {
                        "<${it.name}${" (optional)".takeUnless { _ -> it.isRequired }.orEmpty()}>"
                    }
                }"
            }

        author {
            name = nameWithPrefix +
                    " (text-only)".takeIf { this@commandHelp is TextOnlyCommand }.orEmpty() +
                    " (slash-only)".takeIf { this@commandHelp is SlashOnlyCommand }.orEmpty()
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }
    }

    private fun commandList(jda: JDA, author: User, isFromSlashCommand: Boolean, isInDm: Boolean = true) =
        buildEmbed {
            val categories = mutableMapOf<CommandCategory, MutableSet<Command>>()

            color = Immutable.SUCCESS

            description = buildString {
                val inviteLink = "https://discord.com/api/oauth2/authorize?client_id=${jda.selfUser.idLong}" +
                        "&permissions=${Immutable.INVITE_PERMISSIONS}" +
                        "&scope=applications.commands%20bot"

                if (!isFromSlashCommand)
                    appendLine("*Try using ${jda.selfUser.name}'s slash command menu via typing \"/\" on a server!*\n")

                appendLine("**[Invite Link]($inviteLink)** • **[GitHub Repository](${Immutable.GITHUB_REPOSITORY})**")
            }

            author {
                name = "${jda.selfUser.name} Help"
                iconUrl = jda.selfUser.effectiveAvatarUrl
            }

            if (!isInDm && !isFromSlashCommand)
                footer {
                    text = "Requested by ${author.asTag}"
                    iconUrl = author.effectiveAvatarUrl
                }

            for (command in CommandHandler) {
                val category = command.category
                val edit = categories[category] ?: mutableSetOf()
                edit += command
                categories += category to edit
            }

            val commandFields = mutableSetOf<Pair<String, String>>()

            for ((category, commands) in categories)
                commandFields += "$category Commands:" to commands.sorted().joinToString { it.name }

            for ((name, value) in commandFields.sortedBy { it.first })
                field {
                    title = name
                    description = value
                }
        }
}