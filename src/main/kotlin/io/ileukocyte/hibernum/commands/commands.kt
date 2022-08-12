@file:JvmName("Commands")
package io.ileukocyte.hibernum.commands

import io.ileukocyte.hibernum.commands.TextCommand.ClassicTextUsage
import io.ileukocyte.hibernum.commands.TextCommand.ClassicTextUsageGroup
import io.ileukocyte.hibernum.extensions.capitalizeAll

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

/**
 * A generic type that is to be implemented by classes of all the bot's commands
 *
 * @author Alexander Oksanich
 *
 * @see TextCommand
 * @see ClassicTextOnlyCommand
 * @see SlashOnlyCommand
 * @see SubcommandHolder
 * @see ContextCommand
 * @see MessageContextOnlyCommand
 * @see UserContextOnlyCommand
 */
interface GenericCommand : Comparable<GenericCommand> {
    /**
     * **Note**: the property is always mandatory to override unless the command is a context-menu one;
     * therefore, do not forget to override it yourself in case the command is not context menu only!
     */
    val name: String
    val description: String
    val category: CommandCategory
        get() = javaClass.`package`.name.let { CommandCategory[it.split(".").last()] }
            ?: CommandCategory.UNKNOWN

    /**
     * Used in case the command's actual name cannot be used to identify the command's interactions
     *
     * **Note**: the interaction name must not contain any hyphens!
     */
    val interactionName: String
        get() = name

    /**
     * Used for the help command if the main description is too long for slash command limits
     */
    val fullDescription: String
        get() = description

    /**
     * A property that shows whether the command can only be used by a developer of the bot
     */
    val isDeveloper: Boolean
        get() = category == CommandCategory.DEVELOPER

    /**
     * Used for a few commands that can be used in spite of some other command processes
     * running in the background
     */
    val neglectProcessBlock: Boolean
        get() = isDeveloper

    val inputTypes: Set<InputType>
        get() {
            val inputTypes = mutableSetOf<InputType>()

            if (this is TextCommand) {
                if (this !is SlashOnlyCommand) {
                    inputTypes += InputType.CLASSIC_TEXT_INPUT
                }

                if (this !is ClassicTextOnlyCommand) {
                    inputTypes += InputType.SLASH_COMMAND_MENU
                }
            }

            if (this is ContextCommand) {
                if (this !is UserContextOnlyCommand) {
                    inputTypes += InputType.MESSAGE_CONTEXT_MENU
                }

                if (this !is MessageContextOnlyCommand) {
                    inputTypes += InputType.USER_CONTEXT_MENU
                }
            }

            return inputTypes
        }

    val cooldown: Long
        get() = 0

    /**
     * A property that shows whether the command's expired interactions should
     * trigger an explicit failure response, silent deletion of the command's
     * components (i.e. buttons and selection menus), or normal execution of the command
     */
    val staleInteractionHandling: StaleInteractionHandling
        get() = StaleInteractionHandling.DELETE_ORIGINAL

    val botPermissions: Set<Permission>
        get() = emptySet()
    val memberPermissions: Set<Permission>
        get() = emptySet()

    /**
     * A function that is executed when the command's button menu is utilized by a user
     *
     * @param event
     * The [ButtonInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: ButtonInteractionEvent) {}

    /**
     * A function that is executed when the command's selection menu is utilized by a user
     *
     * @param event
     * The [SelectMenuInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: SelectMenuInteractionEvent) {}

    /**
     * A function that is executed when a user responds to the command's modal
     *
     * @param event
     * The [ModalInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: ModalInteractionEvent) {}

    override fun compareTo(other: GenericCommand) = name.compareTo(other.name)

    enum class InputType {
        CLASSIC_TEXT_INPUT,
        SLASH_COMMAND_MENU,
        MESSAGE_CONTEXT_MENU,
        USER_CONTEXT_MENU;

        override fun toString() = name.replace('_', ' ').capitalizeAll()

        companion object {
            operator fun get(key: String) =
                InputType.values().firstOrNull { it.name.equals(key, ignoreCase = true) }
        }
    }

    enum class StaleInteractionHandling {
        DELETE_ORIGINAL,
        REMOVE_COMPONENTS,
        EXECUTE_COMMAND,
    }
}

/**
 * A generic type that is to be implemented by classes of all the bot's commands
 * that are invoked via Discord text input (either message content or slash command menu)
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see ClassicTextOnlyCommand
 * @see SlashOnlyCommand
 * @see SubcommandHolder
 */
interface TextCommand : GenericCommand {
    /**
     * A property containing a set of alternative classic text command names
     */
    val aliases: Set<String>
        get() = emptySet()

    /**
     * A property containing a set of possible classic text usage groups that is
     * used for providing help for the command
     */
    val usages: Set<ClassicTextUsageGroup>
        get() = emptySet()

    /**
     * A property containing a set of data that is used for option-requiring slash commands
     *
     * **Note**: the [options] property cannot be used alongside the [subcommands][SubcommandHolder.subcommands] property!
     *
     * @throws UnsupportedOperationException if the command class also implements
     * either [SubcommandHolder] or [ClassicTextOnlyCommand]
     */
    val options: Set<OptionData>
        get() = emptySet()

    /**
     * @return A [SlashCommandData] instance of the slash command
     */
    fun asJDASlashCommandData(): SlashCommandData? =
        Commands.slash(name, "(Developer-only) ".takeIf { isDeveloper }.orEmpty() + description)
            .setGuildOnly(true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(memberPermissions))
            .applyIf(this !is SubcommandHolder) { addOptions(this@TextCommand.options) }

    /**
     * A function that is executed when the command is invoked as a classic text command
     *
     * @param event
     * [MessageReceivedEvent] occurring once the command is invoked
     * @param args
     * The arguments that may be attached to the command request
     */
    suspend operator fun invoke(event: MessageReceivedEvent, args: String?)

    /**
     * A function that is executed when the command is invoked as a slash command
     *
     * @param event
     * The [SlashCommandInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: SlashCommandInteractionEvent)

    /**
     * A function that is executed when the slash command's option with auto-completion enabled is triggered
     *
     * @param event
     * The [CommandAutoCompleteInteractionEvent] occurring once the option is triggered
     */
    suspend operator fun invoke(event: CommandAutoCompleteInteractionEvent) {}

    data class ClassicTextUsage(
        val option: String,
        val isOptional: Boolean = false,
        val applyDefaultAffixes: Boolean = true,
    ) {
        override fun toString() = option
    }

    @JvmInline
    value class ClassicTextUsageGroup(val group: Collection<ClassicTextUsage>)

    fun String.toClassicTextUsage(
        isOptional: Boolean = false,
        applyDefaultAffixes: Boolean = true,
    ) = ClassicTextUsage(this, isOptional, applyDefaultAffixes)
}

fun usageGroupOf(vararg usages: ClassicTextUsage) = ClassicTextUsageGroup(usages.toList())

/**
 * A type of command that can be used as a classic text command exclusively
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see SlashOnlyCommand
 */
interface ClassicTextOnlyCommand : TextCommand {
    override val options get() =
        throw UnsupportedOperationException("This type of text command cannot have any options!")

    override fun asJDASlashCommandData(): SlashCommandData? = null

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        throw UnsupportedOperationException("The command cannot be invoked as a slash command!")

    override suspend fun invoke(event: CommandAutoCompleteInteractionEvent) =
        throw UnsupportedOperationException("The command cannot be invoked as a slash command!")
}

/**
 * A type of command that can be used as a slash command exclusively
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see ClassicTextOnlyCommand
 * @see SubcommandHolder
 */
interface SlashOnlyCommand : TextCommand {
    override suspend fun invoke(event: MessageReceivedEvent, args: String?) =
        throw UnsupportedOperationException("The command cannot be invoked as a classic text command!")
}

/**
 * A type of slash command that has its subcommands
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see SlashOnlyCommand
 */
interface SubcommandHolder : TextCommand {
    /**
     * A property containing a map of slash subcommands' data and the functions the triggered subcommands invoke
     *
     * **Note**: the [subcommands] property cannot be used alongside the [options] property!
     */
    val subcommands: Map<SubcommandData, suspend (SlashCommandInteractionEvent) -> Unit>
        get() = emptyMap()

    override val options get() =
        throw UnsupportedOperationException("This type of slash command cannot have any options!")

    override fun asJDASlashCommandData(): SlashCommandData? =
        super.asJDASlashCommandData()?.addSubcommands(subcommands.keys)

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        throw UnsupportedOperationException("The slash command can only be invoked via its subcommands!")
}

/**
 * A type of command that can be used as a command of a Discord context menu
 * (either a message or user profile context menu)
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see MessageContextOnlyCommand
 * @see UserContextOnlyCommand
 */
interface ContextCommand : GenericCommand {
    override val name: String
        get() = contextName

    val contextName: String
    val contextTypes: Set<ContextType>
        get() = setOf(ContextType.MESSAGE, ContextType.USER)

    /**
     * @return All the [CommandData] instances of the context menu command
     */
    fun asJDAContextCommandDataInstances(): Set<CommandData> = contextTypes.map {
        Commands.context(it.jdaContextType, contextName)
            .setGuildOnly(true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(memberPermissions))
    }.toSet()

    /**
     * A function that is executed when the command is invoked as a command of a message context menu
     *
     * @param event
     * The [MessageContextInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: MessageContextInteractionEvent)

    /**
     * A function that is executed when the command is invoked as a command of a user profile context menu
     *
     * @param event
     * The [UserContextInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: UserContextInteractionEvent)

    enum class ContextType(val jdaContextType: Command.Type) {
        MESSAGE(Command.Type.MESSAGE),
        USER(Command.Type.USER);

        companion object {
            operator fun get(jdaContextType: Command.Type) = values()
                .first { it.jdaContextType == jdaContextType }
        }
    }
}

/**
 * A type of command that can be used as a command of a message context menu
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see ContextCommand
 * @see UserContextOnlyCommand
 */
interface MessageContextOnlyCommand : ContextCommand {
    override val contextTypes: Set<ContextCommand.ContextType>
        get() = setOf(ContextCommand.ContextType.MESSAGE)

    override suspend fun invoke(event: UserContextInteractionEvent) =
        throw UnsupportedOperationException("The command cannot be invoked as a user profile context menu command!")
}

/**
 * A type of command that can be used as a command of a user profile context menu
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see ContextCommand
 * @see MessageContextOnlyCommand
 */
interface UserContextOnlyCommand : ContextCommand {
    override val contextTypes: Set<ContextCommand.ContextType>
        get() = setOf(ContextCommand.ContextType.USER)

    override suspend fun invoke(event: MessageContextInteractionEvent) =
        throw UnsupportedOperationException("The command cannot be invoked as a message context menu command!")
}