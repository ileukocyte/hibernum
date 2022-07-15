package io.ileukocyte.hibernum.handlers

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.commands.developer.KillCommand
import io.ileukocyte.hibernum.commands.developer.ShutdownCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.asText
import io.ileukocyte.hibernum.utils.getProcessByEntities

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.TimeUnit

import kotlin.coroutines.CoroutineContext

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.interactions.commands.Command

import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd

private val commandContextDispatcher = newFixedThreadPool(3).asCoroutineDispatcher()

object CommandContext : CoroutineContext by commandContextDispatcher, AutoCloseable by commandContextDispatcher

object CommandHandler : MutableSet<GenericCommand> {
    private val registeredCommands = mutableSetOf<GenericCommand>()
    private val cooldowns = mutableMapOf<String, OffsetDateTime>()

    // Overridden from MutableSet
    override val size get() = registeredCommands.size
    override fun isEmpty() = registeredCommands.isEmpty()
    override fun contains(element: GenericCommand) = element in registeredCommands
    override fun containsAll(elements: Collection<GenericCommand>) = registeredCommands.containsAll(elements)
    override fun iterator() = registeredCommands.iterator()
    override fun add(element: GenericCommand) = registeredCommands.add(element)
    override fun remove(element: GenericCommand) = registeredCommands.remove(element)
    override fun addAll(elements: Collection<GenericCommand>) = registeredCommands.addAll(elements)
    override fun removeAll(elements: Collection<GenericCommand>) = registeredCommands.removeAll(elements.toSet())
    override fun retainAll(elements: Collection<GenericCommand>) = registeredCommands.retainAll(elements.toSet())
    override fun clear() = registeredCommands.clear()

    /**
     * A function that looks for a [GenericCommand] by its name provided
     *
     * @param name
     * The name of the command to search
     *
     * @return A [GenericCommand] having the same name as the provided query
     */
    fun getGenericCommand(name: String) = firstOrNull { it.name == name }

    /**
     * A function that specifically looks for a [TextCommand] by its name or alias provided
     *
     * @param name
     * The name of the command to search
     *
     * @return A [TextCommand] having the same name as the provided query
     */
    operator fun get(name: String): TextCommand? {
        fun checkPriority(actual: String, expected: TextCommand) = when (actual) {
            expected.name -> 2
            in expected.aliases -> 1
            else -> 0
        }

        return filterIsInstance<TextCommand>()
            .filter { checkPriority(name, it) > 0 }
            .maxByOrNull { checkPriority(name, it) }
    }

    /**
     * A function that specifically looks for a [ContextCommand] by its name and context type provided
     *
     * @param name
     * The name of the command to search
     * @param type
     * The JDA context type of the command to search
     *
     * @return A [ContextCommand] having the same name and type as provided
     */
    operator fun get(name: String, type: Command.Type) = filterIsInstanceAnd<ContextCommand> {
        it.contextName == name && type in it.contextTypes
    }.firstOrNull()

    // Command cooldown extensions
    private fun GenericCommand.getRemainingCooldown(userId: Long) =
        cooldowns["$name|$userId"]?.let { cooldown ->
            val time = OffsetDateTime.now().until(cooldown, ChronoUnit.SECONDS)

            if (time <= 0) {
                cooldowns -= "$name|$userId"

                0
            } else {
                time
            }
        } ?: 0

    private fun GenericCommand.getCooldownError(userId: Long) = getRemainingCooldown(userId)
        .takeUnless { it <= 0 }
        ?.let { "Wait for ${asText(it, TimeUnit.SECONDS)} before using the command again!" }

    private fun GenericCommand.applyCooldown(userId: Long) {
        cooldowns += "$name|$userId" to OffsetDateTime.now().plusSeconds(cooldown)
    }

    /**
     * A function that handles [MessageReceivedEvent] that may contain a text command
     *
     * @param event
     * [MessageReceivedEvent] occurring once the command is invoked
     *
     * @author Alexander Oksanich
     */
    internal operator fun invoke(event: MessageReceivedEvent) {
        if (!event.author.isBot && !event.author.isSystem && event.message.type == MessageType.DEFAULT && event.isFromGuild) {
            if (event.message.contentRaw.trim().startsWith(Immutable.DEFAULT_PREFIX)) {
                val args = event.message.contentRaw.split(Regex("\\s+"), 2)

                this[args.first().removePrefix(Immutable.DEFAULT_PREFIX).lowercase()]
                    ?.takeIf { it !is SlashOnlyCommand }
                    ?.let { command ->
                        CoroutineScope(CommandContext).launch {
                            val isSpecial = command is ShutdownCommand || command is KillCommand

                            if (event.jda.getProcessByEntities(event.author, event.channel) === null || isSpecial) {
                                if (command.isDeveloper && !event.author.isDeveloper) {
                                    event.channel
                                        .sendFailure("You cannot execute the command " +
                                                "since you are not a developer of the bot!")
                                        .queue()

                                    return@launch
                                }

                                try {
                                    if (!event.guild.selfMember.hasPermission(command.botPermissions)) {
                                        throw CommandException(
                                            "${event.jda.selfUser.name} is not able to execute the command " +
                                                    "since no required permissions (${command.botPermissions.joinToString { it.getName() }}) were granted!",
                                            footer = "Try contacting the server's staff!",
                                        )
                                    }

                                    if (event.member?.hasPermission(command.memberPermissions) == false) {
                                        throw CommandException("You do not have the required permission to manage messages!")
                                    }

                                    if (command.cooldown > 0) {
                                        command.getCooldownError(event.author.idLong)
                                            ?.let { throw CommandException(it, SelfDeletion(5)) }
                                            ?: command.let {
                                                it.applyCooldown(event.author.idLong)
                                                it(event, args.getOrNull(1))
                                            }
                                    } else {
                                        command(event, args.getOrNull(1))
                                    }
                                } catch (e: Exception) {
                                    when (e) {
                                        is CommandException ->
                                            event.channel.sendFailure(e.message ?: "CommandException has occurred!") {
                                                text = e.footer ?: e.selfDeletion?.let { sd ->
                                                    "This message will self-delete in ${asText(sd.delay, sd.unit)}"
                                                }
                                            }.queue({
                                                e.selfDeletion?.let { sd -> it.delete().queueAfter(sd.delay, sd.unit, {}) {} }
                                            }) { e.printStackTrace() }
                                        is InsufficientPermissionException -> {} // ignored
                                        else -> {
                                            event.channel.sendFailure("""
                                                |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                                |${e.message ?: "No message provided"}
                                                |""".trimMargin()).queue()

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

    /**
     * A function that handles [SlashCommandInteractionEvent] that contains a slash command
     *
     * @param event
     * [SlashCommandInteractionEvent] occurring once the command is invoked
     *
     * @author Alexander Oksanich
     */
    internal operator fun invoke(event: SlashCommandInteractionEvent) {
        if (event.isFromGuild) {
            this[event.name]?.takeIf { it !is ClassicTextOnlyCommand }?.let { command ->
                CoroutineScope(CommandContext).launch {
                    val isSpecial = command is ShutdownCommand || command is KillCommand

                    if (event.jda.getProcessByEntities(event.user, event.channel) === null || isSpecial) {
                        if (command.isDeveloper && !event.user.isDeveloper) {
                            event.replyFailure("You cannot execute the command " +
                                    "since you are not a developer of the bot!").queue()

                            return@launch
                        }

                        try {
                            if (event.guild?.selfMember?.hasPermission(command.botPermissions) == false) {
                                throw CommandException(
                                    "${event.jda.selfUser.name} is not able to execute the command " +
                                            "since no required permissions (${command.botPermissions.joinToString { it.getName() }}) were granted!",
                                    footer = "Try contacting the server's staff!",
                                )
                            }

                            if (command.cooldown > 0) {
                                command.getCooldownError(event.user.idLong)
                                    ?.let { event.replyFailure(it).setEphemeral(true).queue() }
                                    ?: command.let {
                                        it.applyCooldown(event.user.idLong)
                                        it(event)
                                    }
                            } else {
                                command(event)
                            }
                        } catch (e: Exception) {
                            when (e) {
                                is CommandException ->
                                    event.replyFailure(e.message ?: "CommandException has occurred!") { text = e.footer }
                                        .setEphemeral(true)
                                        .queue(null) {
                                            event.channel.sendFailure(e.message ?: "CommandException has occurred!") {
                                                text = e.footer ?: e.selfDeletion?.let { sd ->
                                                    "This message will self-delete in ${asText(sd.delay, sd.unit)}"
                                                }
                                            }.queue({
                                                e.selfDeletion?.let { sd -> it.delete().queueAfter(sd.delay, sd.unit, {}) {} }
                                            }) { e.printStackTrace() }
                                        }
                                is InsufficientPermissionException -> {} // ignored
                                else -> {
                                    val errorMessage = """
                                        |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                        |${e.message ?: "No message provided"}
                                        |""".trimMargin()

                                    event.hook
                                        .editOriginalComponents()
                                        .retainFiles(emptyList())
                                        .setContent("")
                                        .setEmbeds(defaultEmbed(errorMessage, EmbedType.FAILURE))
                                        .queue(null) {
                                            event.replyFailure(errorMessage).queue(null) {
                                                event.channel.sendFailure(errorMessage).queue()
                                            }
                                        }

                                    e.printStackTrace()
                                }
                            }
                        }
                    } else {
                        event.replyFailure("You have some other command processes running right now!")
                            .setEphemeral(true)
                            .queue()
                    }
                }
            }
        }
    }

    /**
     * A function that handles [ButtonInteractionEvent] that occurs when
     * a command's button menu is utilized by a user
     *
     * @param event
     * [ButtonInteractionEvent] occurring once the command's button menu is used
     *
     * @author Alexander Oksanich
     */
    internal operator fun invoke(event: ButtonInteractionEvent) {
        if (event.isFromGuild && event.message.author == event.jda.selfUser) {
            getGenericCommand(event.componentId.split("-").first())?.let { command ->
                CoroutineScope(CommandContext).launch {
                    if (event.message.timeCreated.isBefore(event.jda.startDate)) {
                        if (command.eliminateStaleInteractions) {
                            event.message.delete().queue(null) {}

                            event.channel.sendMessage {
                                content += event.user.asMention
                                embeds += defaultEmbed("The interaction has already expired!", EmbedType.FAILURE) {
                                    text = "This message will self-delete in 5 seconds"
                                }
                            }.queue {
                                it.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                            }
                        } else {
                            event.message.editMessageComponents().queue(null) {}
                        }

                        return@launch
                    }

                    try {
                        command(event)
                    } catch (e: Exception) {
                        when (e) {
                            is CommandException -> event.replyFailure(e.message ?: "CommandException has occurred!")
                                .setEphemeral(true)
                                .queue(null) {
                                    event.channel.sendFailure(e.message ?: "CommandException has occurred!") {
                                        text = e.footer ?: e.selfDeletion?.let { sd ->
                                            "This message will self-delete in ${asText(sd.delay, sd.unit)}"
                                        }
                                    }.queue({
                                        e.selfDeletion?.let { sd -> it.delete().queueAfter(sd.delay, sd.unit, {}) {} }
                                    }) { e.printStackTrace() }
                                }
                            is InsufficientPermissionException -> {} // ignored
                            else -> {
                                val errorMessage = """
                                        |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                        |${e.message ?: "No message provided"}
                                        |""".trimMargin()

                                event.hook
                                    .editOriginalComponents()
                                    .retainFiles(emptyList())
                                    .setContent("")
                                    .setEmbeds(defaultEmbed(errorMessage, EmbedType.FAILURE))
                                    .queue(null) {
                                        event.replyFailure(errorMessage).queue(null) {
                                            event.messageChannel.sendFailure(errorMessage).queue()
                                        }
                                    }

                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A function that handles [SelectMenuInteractionEvent] that occurs when
     * a command's selection menu is utilized by a user
     *
     * @param event
     * [SelectMenuInteractionEvent] occurring once the command's selection menu is used
     *
     * @author Alexander Oksanich
     */
    internal operator fun invoke(event: SelectMenuInteractionEvent) {
        if (event.isFromGuild && event.message.author == event.jda.selfUser) {
            getGenericCommand(event.componentId.split("-").first())?.let { command ->
                CoroutineScope(CommandContext).launch {
                    if (event.message.timeCreated.isBefore(event.jda.startDate)) {
                        if (command.eliminateStaleInteractions) {
                            event.message.delete().queue(null) {}

                            event.channel.sendMessage {
                                content += event.user.asMention
                                embeds += defaultEmbed("The interaction has already expired!", EmbedType.FAILURE) {
                                    text = "This message will self-delete in 5 seconds"
                                }
                            }.queue {
                                it.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                            }
                        } else {
                            event.message.editMessageComponents().queue(null) {}
                        }

                        return@launch
                    }

                    try {
                        command(event)
                    } catch (e: Exception) {
                        when (e) {
                            is CommandException -> event.replyFailure(e.message ?: "CommandException has occurred!")
                                .setEphemeral(true)
                                .queue(null) {
                                    event.channel.sendFailure(e.message ?: "CommandException has occurred!") {
                                        text = e.footer ?: e.selfDeletion?.let { sd ->
                                            "This message will self-delete in ${asText(sd.delay, sd.unit)}"
                                        }
                                    }.queue({
                                        e.selfDeletion?.let { sd -> it.delete().queueAfter(sd.delay, sd.unit, {}) {} }
                                    }) { e.printStackTrace() }
                                }
                            is InsufficientPermissionException -> {} // ignored
                            else -> {
                                val errorMessage = """
                                        |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                        |${e.message ?: "No message provided"}
                                        |""".trimMargin()

                                event.hook
                                    .editOriginalComponents()
                                    .retainFiles(emptyList())
                                    .setContent("")
                                    .setEmbeds(defaultEmbed(errorMessage, EmbedType.FAILURE))
                                    .queue(null) {
                                        event.replyFailure(errorMessage).queue(null) {
                                            event.messageChannel.sendFailure(errorMessage).queue()
                                        }
                                    }

                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A function that handles [ButtonInteractionEvent] that occurs when
     * a user response to a command's modal
     *
     * @param event
     * [ModalInteractionEvent] occurring once the command's modal is triggered
     *
     * @author Alexander Oksanich
     */
    internal operator fun invoke(event: ModalInteractionEvent) {
        if (event.isFromGuild) {
            getGenericCommand(event.modalId.split("-").first())?.let { command ->
                CoroutineScope(CommandContext).launch {
                    try {
                        command(event)
                    } catch (e: Exception) {
                        when (e) {
                            is CommandException -> event.replyFailure(e.message ?: "CommandException has occurred!")
                                .setEphemeral(true)
                                .queue(null) {
                                    event.messageChannel.sendFailure(e.message ?: "CommandException has occurred!") {
                                        text = e.footer ?: e.selfDeletion?.let { sd ->
                                            "This message will self-delete in ${asText(sd.delay, sd.unit)}"
                                        }
                                    }.queue({
                                        e.selfDeletion?.let { sd -> it.delete().queueAfter(sd.delay, sd.unit, {}) {} }
                                    }) { e.printStackTrace() }
                                }
                            is InsufficientPermissionException -> {} // ignored
                            else -> {
                                val errorMessage = """
                                        |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                        |${e.message ?: "No message provided"}
                                        |""".trimMargin()

                                event.hook
                                    .editOriginalComponents()
                                    .retainFiles(emptyList())
                                    .setContent("")
                                    .setEmbeds(defaultEmbed(errorMessage, EmbedType.FAILURE))
                                    .queue(null) {
                                        event.replyFailure(errorMessage).queue(null) {
                                            event.messageChannel.sendFailure(errorMessage).queue()
                                        }
                                    }

                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A function that handles [MessageContextInteractionEvent] that contains a command of a message context menu
     *
     * @param event
     * [MessageContextInteractionEvent] occurring once the command is invoked
     *
     * @author Alexander Oksanich
     */
    internal operator fun invoke(event: MessageContextInteractionEvent) {
        if (event.isFromGuild) {
            this[event.name, Command.Type.MESSAGE]?.let { cc ->
                val command = cc as MessageContextCommand

                CoroutineScope(CommandContext).launch {
                    if (event.jda.getProcessByEntities(event.user, event.messageChannel) === null) {
                        if (command.isDeveloper && !event.user.isDeveloper) {
                            event.replyFailure("You cannot execute the command since you are not a developer of the bot!").queue()

                            return@launch
                        }

                        try {
                            if (event.guild?.selfMember?.hasPermission(command.botPermissions) == false) {
                                throw CommandException(
                                    "${event.jda.selfUser.name} is not able to execute the command " +
                                            "since no required permissions (${command.botPermissions.joinToString { it.getName() }}) were granted!",
                                    footer = "Try contacting the server's staff!",
                                )
                            }

                            if (command.cooldown > 0) {
                                cc.getCooldownError(event.user.idLong)
                                    ?.let { event.replyFailure(it).setEphemeral(true).queue() }
                                    ?: cc.let {
                                        it.applyCooldown(event.user.idLong)
                                        it(event)
                                    }
                            } else {
                                cc(event)
                            }
                        } catch (e: Exception) {
                            when (e) {
                                is CommandException ->
                                    event.replyFailure(e.message ?: "CommandException has occurred!") { text = e.footer }
                                        .setEphemeral(true)
                                        .queue(null) {
                                            event.messageChannel.sendFailure(e.message ?: "CommandException has occurred!") {
                                                text = e.footer ?: e.selfDeletion?.let { sd ->
                                                    "This message will self-delete in ${asText(sd.delay, sd.unit)}"
                                                }
                                            }.queue({
                                                e.selfDeletion?.let { sd -> it.delete().queueAfter(sd.delay, sd.unit, {}) {} }
                                            }) { e.printStackTrace() }
                                        }
                                is InsufficientPermissionException -> {} // ignored
                                else -> {
                                    val errorMessage = """
                                        |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                        |${e.message ?: "No message provided"}
                                        |""".trimMargin()

                                    event.hook
                                        .editOriginalComponents()
                                        .retainFiles(emptyList())
                                        .setContent("")
                                        .setEmbeds(defaultEmbed(errorMessage, EmbedType.FAILURE))
                                        .queue(null) {
                                            event.replyFailure(errorMessage).queue(null) {
                                                event.messageChannel.sendFailure(errorMessage).queue()
                                            }
                                        }

                                    e.printStackTrace()
                                }
                            }
                        }
                    } else {
                        event.replyFailure("You have some other command processes running right now!")
                            .setEphemeral(true)
                            .queue()
                    }
                }
            }
        }
    }

    /**
     * A function that handles [UserContextInteractionEvent] that contains a command of a user context menu
     *
     * @param event
     * [UserContextInteractionEvent] occurring once the command is invoked
     *
     * @author Alexander Oksanich
     */
    internal operator fun invoke(event: UserContextInteractionEvent) {
        if (event.isFromGuild) {
            this[event.name, Command.Type.USER]?.let { cc ->
                val command = cc as UserContextCommand

                CoroutineScope(CommandContext).launch {
                    if (event.jda.getProcessByEntities(event.user, event.messageChannel) === null) {
                        if (command.isDeveloper && !event.user.isDeveloper) {
                            event.replyFailure("You cannot execute the command since you are not a developer of the bot!").queue()

                            return@launch
                        }

                        try {
                            if (event.guild?.selfMember?.hasPermission(command.botPermissions) == false) {
                                throw CommandException(
                                    "${event.jda.selfUser.name} is not able to execute the command " +
                                            "since no required permissions (${command.botPermissions.joinToString { it.getName() }}) were granted!",
                                    footer = "Try contacting the server's staff!",
                                )
                            }

                            if (command.cooldown > 0) {
                                cc.getCooldownError(event.user.idLong)
                                    ?.let { event.replyFailure(it).setEphemeral(true).queue() }
                                    ?: cc.let {
                                        it.applyCooldown(event.user.idLong)
                                        it(event)
                                    }
                            } else {
                                cc(event)
                            }
                        } catch (e: Exception) {
                            when (e) {
                                is CommandException ->
                                    event.replyFailure(e.message ?: "CommandException has occurred!") { text = e.footer }
                                        .setEphemeral(true)
                                        .queue(null) {
                                            event.messageChannel.sendFailure(e.message ?: "CommandException has occurred!") {
                                                text = e.footer ?: e.selfDeletion?.let { sd ->
                                                    "This message will self-delete in ${asText(sd.delay, sd.unit)}"
                                                }
                                            }.queue({
                                                e.selfDeletion?.let { sd -> it.delete().queueAfter(sd.delay, sd.unit, {}) {} }
                                            }) { e.printStackTrace() }
                                        }
                                is InsufficientPermissionException -> {} // ignored
                                else -> {
                                    val errorMessage = """
                                        |${e::class.simpleName ?: "An unknown exception"} has occurred:
                                        |${e.message ?: "No message provided"}
                                        |""".trimMargin()

                                    event.hook
                                        .editOriginalComponents()
                                        .retainFiles(emptyList())
                                        .setContent("")
                                        .setEmbeds(defaultEmbed(errorMessage, EmbedType.FAILURE))
                                        .queue(null) {
                                            event.replyFailure(errorMessage).queue(null) {
                                                event.messageChannel.sendFailure(errorMessage).queue()
                                            }
                                        }

                                    e.printStackTrace()
                                }
                            }
                        }
                    } else {
                        event.replyFailure("You have some other command processes running right now!")
                            .setEphemeral(true)
                            .queue()
                    }
                }
            }
        }
    }
}