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
     * A property that provides the main name of the command
     *
     * **Note**: the property is always mandatory to override unless the command is a context-menu one;
     * therefore, do not forget to override it yourself in case the command is not context menu only!
     *
     * **Note**: make sure to override the [interactionName] property in case the main name contains
     * any hyphens and the command uses any Discord components!
     *
     * @see interactionName
     * @see [ContextCommand.contextName]
     */
    val name: String

    /**
     * A property that provides the main description of the command
     *
     * @see fullDescription
     */
    val description: String

    /**
     * A property that provides the functional category of the command
     * (depends on the name of a command package unless manually overridden)
     */
    val category: CommandCategory
        get() = javaClass.`package`.name.let { CommandCategory[it.split(".").last()] }
            ?: CommandCategory.UNKNOWN

    /**
     * Used in case the command's actual name cannot be used to identify the command's interactions
     *
     * **Note**: the interaction name must not contain any hyphens!
     *
     * It is also highly recommended to override the property in favor of
     * something more compact and concise in case the command is context menu only!
     *
     * @see name
     * @see [ContextCommand.contextName]
     */
    val interactionName: String
        get() = name

    /**
     * Used for the command's help message if the main description is too long for slash command limits
     *
     * @see description
     */
    val fullDescription: String
        get() = description

    /**
     * A property that shows whether the command can only be used by a developer of the bot
     *
     * @see neglectProcessBlock
     */
    val isDeveloper: Boolean
        get() = category == CommandCategory.DEVELOPER

    /**
     * Used for a few commands that can be used in spite of any other
     * command processes running in the background
     *
     * @see isDeveloper
     */
    val neglectProcessBlock: Boolean
        get() = isDeveloper

    /**
     * A property that provides all the ways the current command can be used by a user
     *
     * @see InputType
     */
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

    /**
     * A property that provides the minimum length of time that a user will need to wait after using the command
     * before it can be used again
     */
    val cooldown: Long
        get() = 0

    /**
     * A property that shows whether the command's expired components should
     * trigger an explicit failure response, silent deletion of the command's
     * components (i.e. buttons and selection menus), or normal execution of the command
     *
     * **Note**: A component is considered expired within the project if it was sent by the bot
     * before the latest reboot
     *
     * @see StaleComponentHandling
     */
    val staleComponentHandling: StaleComponentHandling
        get() = StaleComponentHandling.DELETE_ORIGINAL

    /**
     * A property that provides a set of the permissions that the bot is required to have
     * in order to execute the command
     */
    val botPermissions: Set<Permission>
        get() = emptySet()

    /**
     * A property that provides a set of the permissions that the user is required to have
     * in order to run the command
     */
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

    /**
     * A type that represents all the ways a command can be used by a user
     *
     * @see inputTypes
     */
    enum class InputType {
        /**
         * A normal Discord message starting with the pre-defined bot prefix
         */
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

    /**
     * A type that represents the type of reaction triggered by a command's expired components being used
     *
     * **Note**: A component is considered expired within the project if it was sent by the bot
     * before the latest reboot
     *
     * @see staleComponentHandling
     */
    enum class StaleComponentHandling {
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
     *
     * @see ClassicTextUsage
     * @see ClassicTextUsageGroup
     * @see toClassicTextUsage
     * @see defaultUsageGroupOf
     * @see usageGroupOf
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

    /**
     * A type that represents a possible classic text usage and is
     * used for providing help for a text command
     *
     * @param option
     * The option's name
     * @param isOptional
     * Whether the option should be marked as optional
     * @param applyDefaultAffixes
     * Whether the option should be surrounded by the default affixes (i.e. brackets)
     * in the command's help message
     *
     * @see usages
     * @see ClassicTextUsageGroup
     * @see toClassicTextUsage
     * @see defaultUsageGroupOf
     * @see usageGroupOf
     */
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
 * A shortcut function for [usageGroupOf] that automatically converts the provided strings
 * to [ClassicTextUsage] instances with the default parameters
 */
fun defaultUsageGroupOf(vararg usages: String) = ClassicTextUsageGroup(usages.map { ClassicTextUsage(it) })

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

    /**
     * A property that provides the context-menu name of the command
     *
     * **Note**: it is also highly recommended to override the [interactionName] in favor of
     * something more compact and concise in case the command is context menu only!
     *
     * @see name
     * @see interactionName
     */
    val contextName: String

    /**
     * A property that provides a set of context menus a user is able to invoke the command from
     *
     * @see ContextType
     */
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

    /**
     * A type that represents context menus a user is able to invoke a command from
     *
     * @see contextTypes
     */
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