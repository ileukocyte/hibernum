package io.ileukocyte.hibernum.handlers

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.annotations.HibernumExperimental
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.commands.developer.ShutdownCommand
import io.ileukocyte.hibernum.extensions.isDeveloper
import io.ileukocyte.hibernum.extensions.replyFailure
import io.ileukocyte.hibernum.extensions.sendFailure
import io.ileukocyte.hibernum.utils.asText
import io.ileukocyte.hibernum.utils.getProcessByEntities

import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
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

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors.newFixedThreadPool

private val commandContextDispatcher = newFixedThreadPool(3).asCoroutineDispatcher()

object CommandContext : CoroutineContext by commandContextDispatcher, AutoCloseable by commandContextDispatcher

object CommandHandler : MutableSet<Command> {
    private val registeredCommands = mutableSetOf<Command>()
    private val cooldowns = mutableMapOf<String, OffsetDateTime>()

    /**
     * A property that returns non-text-only commands registered by [CommandHandler] as a set of JDA's [CommandData] instances
     */
    val asSlashCommands get() = filter { it !is TextOnlyCommand }
        .map { it.asSlashCommand }
        .toSet()

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

    operator fun get(name: String): Command? {
        fun checkPriority(actual: String, expected: Command) = when (actual) {
            expected.name -> 2
            in expected.aliases -> 1
            else -> 0
        }

        return registeredCommands
            .filter { checkPriority(name, it) > 0 }
            .maxByOrNull { checkPriority(name, it) }
    }

    operator fun get(id: Int) = registeredCommands
        .firstOrNull { it.id == id }

    // Command cooldown extensions
    private fun Command.getRemainingCooldown(userId: Long) =
        cooldowns["$name|$userId"]?.let { cooldown ->
            val time = OffsetDateTime.now().until(cooldown, ChronoUnit.SECONDS)

            if (time <= 0) {
                cooldowns -= "$name|$userId"
                0
            } else time
        } ?: 0

    @OptIn(ExperimentalTime::class)
    private fun Command.getCooldownError(userId: Long) = getRemainingCooldown(userId)
        .takeUnless { it <= 0 }
        ?.let { "Wait for ${asText(it, DurationUnit.SECONDS)} before using the command again!" }

    private fun Command.applyCooldown(userId: Long) {
        cooldowns += "$name|$userId" to OffsetDateTime.now().plusSeconds(cooldown)
    }

    /**
     * A function that handles [GuildMessageReceivedEvent] that may contain a text command
     *
     * @param event
     * [GuildMessageReceivedEvent] occurring once the command is invoked
     *
     * @author Alexander Oksanich
     */
    @OptIn(ExperimentalTime::class)
    internal operator fun invoke(event: GuildMessageReceivedEvent) {
        if (!event.author.isBot && !event.author.isSystem && event.message.type == MessageType.DEFAULT) {
            if (event.message.contentRaw.trim().startsWith(Immutable.DEFAULT_PREFIX)) {
                val args = event.message.contentRaw.split("\\s+".toRegex(), 2)
                this[args.first().removePrefix(Immutable.DEFAULT_PREFIX).lowercase()]
                    ?.takeIf { it !is SlashOnlyCommand }
                    ?.let { command ->
                        CoroutineScope(CommandContext).launch {
                            if (event.jda.getProcessByEntities(event.author, event.channel) === null || command is ShutdownCommand) {
                                if (command.isDeveloper && !event.author.isDeveloper) {
                                    event.channel.sendFailure("You cannot execute the command!").queue()
                                } else {
                                    try {
                                        if (command.cooldown > 0) {
                                            command.getCooldownError(event.author.idLong)
                                                ?.let { event.channel.sendFailure(it).queue() }
                                                ?: command.let {
                                                    it.applyCooldown(event.author.idLong)
                                                    it(event, args.getOrNull(1))
                                                }
                                        } else command(event, args.getOrNull(1))
                                    } catch (e: Exception) {
                                        when (e) {
                                            is CommandException ->
                                                event.channel.sendFailure(
                                                    e.message ?: "CommandException has occurred!",
                                                    e.footer ?: e.selfDeletion?.let { sd -> "This message will self-delete in ${asText(sd.delay, sd.unit)}" }
                                                ).queue({
                                                    e.selfDeletion?.let { sd ->
                                                        it.delete().queueAfter(sd.delay, sd.unit, {}) {}
                                                    }
                                                }) { e.printStackTrace() }
                                            is InsufficientPermissionException -> { } // ignored
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

    /**
     * EXPERIMENTAL: A function that handles [SlashCommandEvent] that contains a slash command
     *
     * @param event
     * [SlashCommandEvent] occurring once the command is invoked
     *
     * @author Alexander Oksanich
     */
    @OptIn(ExperimentalTime::class)
    @HibernumExperimental
    internal operator fun invoke(event: SlashCommandEvent) {
        if (event.isFromGuild) {
            this[event.name]?.takeIf { it !is TextOnlyCommand }?.let { command ->
                CoroutineScope(CommandContext).launch {
                    if (event.jda.getProcessByEntities(event.user, event.channel) === null || command is ShutdownCommand) {
                        if (command.isDeveloper && !event.user.isDeveloper) {
                            event.replyFailure("You cannot execute the command!").queue()
                        } else {
                            try {
                                if (command.cooldown > 0) {
                                    command.getCooldownError(event.user.idLong)
                                        ?.let { event.replyFailure(it).setEphemeral(true).queue() }
                                        ?: command.let {
                                            it.applyCooldown(event.user.idLong)
                                            it(event)
                                        }
                                } else command(event)
                            } catch (e: Exception) {
                                when (e) {
                                    is CommandException ->
                                        event.replyFailure(e.message ?: "CommandException has occurred!", e.footer)
                                            .setEphemeral(true)
                                            .queue({}) {
                                                event.channel.sendFailure(
                                                    e.message ?: "CommandException has occurred!",
                                                    e.footer ?: e.selfDeletion?.let { sd -> "This message will self-delete in ${asText(sd.delay, sd.unit)}" }
                                                ).queue({
                                                    e.selfDeletion?.let { sd ->
                                                        it.delete().queueAfter(sd.delay, sd.unit, {}) {}
                                                    }
                                                }) { e.printStackTrace() }
                                            }
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
                    } else
                        event.replyFailure("You have some other processes running right now!")
                            .setEphemeral(true)
                            .queue()
                }
            }
        }
    }

    /**
     * EXPERIMENTAL: A function that handles [ButtonClickEvent] that occurs
     * when a command's button menu is utilized by a user
     *
     * @param event
     * [ButtonClickEvent] occurring once the command's button menu is used
     *
     * @author Alexander Oksanich
     */
    @OptIn(ExperimentalTime::class)
    @HibernumExperimental
    internal operator fun invoke(event: ButtonClickEvent) {
        if (event.isFromGuild && event.message?.author == event.jda.selfUser) {
            this[event.componentId.split("-").first()]
            ?.let { command ->
                CoroutineScope(CommandContext).launch {
                    try {
                        command(event)
                    } catch (e: Exception) {
                        when (e) {
                            is CommandException -> event.replyFailure(e.message ?: "CommandException has occurred!")
                                .setEphemeral(true)
                                .queue({}) {
                                    event.channel.sendFailure(
                                        e.message ?: "CommandException has occurred!",
                                        e.footer ?: e.selfDeletion?.let { sd -> "This message will self-delete in ${asText(sd.delay, sd.unit)}" }
                                    ).queue({
                                        e.selfDeletion?.let { sd ->
                                            it.delete().queueAfter(sd.delay, sd.unit, {}) {}
                                        }
                                    }) { e.printStackTrace() }
                                }
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

    /**
     * EXPERIMENTAL: A function that handles [SelectionMenuEvent] that occurs
     * when a command's selection menu is utilized by a user
     *
     * @param event
     * [SelectionMenuEvent] occurring once the command's selection menu is used
     *
     * @author Alexander Oksanich
     */
    @OptIn(ExperimentalTime::class)
    @HibernumExperimental
    internal operator fun invoke(event: SelectionMenuEvent) {
        if (event.isFromGuild && event.message?.author == event.jda.selfUser) {
            this[event.componentId.split("-").first()]?.let {
                    command ->
                CoroutineScope(CommandContext).launch {
                    try {
                        command(event)
                    } catch (e: Exception) {
                        when (e) {
                            is CommandException -> event.replyFailure(e.message ?: "CommandException has occurred!")
                                .setEphemeral(true)
                                .queue({}) {
                                    event.channel.sendFailure(
                                        e.message ?: "CommandException has occurred!",
                                        e.footer ?: e.selfDeletion?.let { sd -> "This message will self-delete in ${asText(sd.delay, sd.unit)}" }
                                    ).queue({
                                        e.selfDeletion?.let { sd ->
                                            it.delete().queueAfter(sd.delay, sd.unit, {}) {}
                                        }
                                    }) { e.printStackTrace() }
                                }
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
}