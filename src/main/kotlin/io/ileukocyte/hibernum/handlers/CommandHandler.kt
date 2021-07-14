package io.ileukocyte.hibernum.handlers

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.annotations.HibernumExperimental
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.isDeveloper
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.utils.getProcessByEntities

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.interactions.commands.build.CommandData

import java.util.concurrent.Executors.newFixedThreadPool

private val commandContextDispatcher = newFixedThreadPool(3).asCoroutineDispatcher()

object CommandContext : CoroutineContext by commandContextDispatcher, AutoCloseable by commandContextDispatcher

object CommandHandler : MutableSet<Command> {
    private val registeredCommands = mutableSetOf<Command>()

    val asSlashCommands get() =
        filter { !it.isNonSlashOnly }.map { CommandData(it.name, it.description).addOptions(it.options) }

    // Stuff overriden from MutableSet
    override val size get() = registeredCommands.size
    override fun isEmpty() = registeredCommands.isEmpty()
    override fun contains(element: Command) = element in registeredCommands
    override fun containsAll(elements: Collection<Command>) = registeredCommands.containsAll(elements)
    override fun iterator() = registeredCommands.iterator()
    override fun add(element: Command) = registeredCommands.add(element)
    override fun remove(element: Command) = registeredCommands.remove(element)
    override fun addAll(elements: Collection<Command>) = registeredCommands.addAll(elements)
    override fun removeAll(elements: Collection<Command>) = registeredCommands.removeAll(elements)
    override fun retainAll(elements: Collection<Command>) = registeredCommands.retainAll(elements)
    override fun clear() = registeredCommands.clear()

    operator fun get(name: String) = registeredCommands
        .firstOrNull { it.name.equals(name, ignoreCase = true) }

    operator fun get(id: Int) = registeredCommands
        .firstOrNull { it.id == id }

    @HibernumExperimental
    operator fun invoke(event: SlashCommandEvent) {
        if (event.isFromGuild) {
            this[event.name]?.takeIf { !it.isNonSlashOnly }?.let { command ->
                CoroutineScope(CommandContext).launch {
                    if (event.jda.getProcessByEntities(event.user, event.channel) === null) {
                        if (command.isDeveloper && !event.user.isDeveloper) {
                            event.replyFailure("You cannot execute the command!").queue()
                        } else {
                            try {
                                command(event)
                            } catch (e: Exception) {
                                when (e) {
                                    is CommandException -> event.replyFailure(
                                        e.message ?: "CommandException has occurred!"
                                    )
                                        .queue()
                                    is InsufficientPermissionException -> {
                                    } // ignored
                                    else -> {
                                        event.replyFailure(
                                            """
                                           |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                           |${e.message ?: "No message provided"}
                                           |""".trimMargin()
                                        ).queue()
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    } else
                        event.replyFailure("You have some other processes running right now!")
                            .setEphemeral(true)
                            .queue()
                }
            }
        }
    }

    @HibernumExperimental
    operator fun invoke(event: ButtonClickEvent) {
        if (event.isFromGuild) {
            this[event.componentId.split("-").first()]//?.takeIf { it.requiresButtonClick }
            ?.let { command ->
                CoroutineScope(CommandContext).launch {
                    try {
                        command(event)
                    } catch (e: Exception) {
                        when (e) {
                            is CommandException -> event.replyFailure(e.message ?: "CommandException has occurred!")
                                .queue()
                            is InsufficientPermissionException -> {} // ignored
                            else -> {
                                event.replyFailure(
                                    """
                                           |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                           |${e.message ?: "No message provided"}
                                           |""".trimMargin()
                                ).queue()
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    @HibernumExperimental
    operator fun invoke(event: SelectionMenuEvent) {
        if (event.isFromGuild) {
            this[event.componentId.split("-").first()]?.let {
                    command ->
                CoroutineScope(CommandContext).launch {
                    try {
                        command(event)
                    } catch (e: Exception) {
                        when (e) {
                            is CommandException -> event.replyFailure(e.message ?: "CommandException has occurred!")
                                .queue()
                            is InsufficientPermissionException -> {
                            } // ignored
                            else -> {
                                event.replyFailure(
                                    """
                                           |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                           |${e.message ?: "No message provided"}
                                           |""".trimMargin()
                                ).queue()
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    operator fun invoke(event: GuildMessageReceivedEvent) {
        if (!event.author.isBot && !event.author.isSystem && event.message.type == MessageType.DEFAULT && event.message.isFromGuild) {
            if (event.message.contentRaw.trim().startsWith(Immutable.DEFAULT_PREFIX)) {
                val args = event.message.contentRaw.split("\\s+".toRegex(), 2)
                this[args.first().removePrefix(Immutable.DEFAULT_PREFIX).lowercase()]
                    ?.takeIf { it.isNonSlashOnly || it.isSlashAndSupportTextMode }
                    ?.let { command ->
                        CoroutineScope(CommandContext).launch {
                            if (event.jda.getProcessByEntities(event.author, event.channel) === null) {
                                if (command.isDeveloper && !event.author.isDeveloper) {
                                    event.channel.sendFailure("You cannot execute the command!").queue()
                                } else {
                                    try {
                                        command(event, args.getOrNull(1))
                                    } catch (e: Exception) {
                                        when (e) {
                                            is CommandException -> {
                                                event.channel.sendFailure(e.message ?: "CommandException has occurred!")
                                                    .queue()
                                            }
                                            is InsufficientPermissionException -> {
                                            } // ignored
                                            else -> {
                                                event.channel.sendFailure(
                                                    """
                                           |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                           |${e.message ?: "No message provided"}
                                           |""".trimMargin()
                                                ).queue()
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}