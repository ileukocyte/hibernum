@file:JvmName("Commands")
package io.ileukocyte.hibernum.commands

import io.ileukocyte.hibernum.extensions.capitalizeAll

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
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
 * @see MessageContextCommand
 * @see UserContextCommand
 * @see UniversalContextCommand
 */
interface GenericCommand : Comparable<GenericCommand> {
    val name: String
    val description: String
    val category: CommandCategory
        get() = javaClass.`package`.name.let { CommandCategory[it.split(".").last()] }
            ?: CommandCategory.UNKNOWN

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

            if (this is MessageContextCommand) {
                inputTypes += InputType.MESSAGE_CONTEXT_MENU
            }

            if (this is UserContextCommand) {
                inputTypes += InputType.USER_CONTEXT_MENU
            }

            return inputTypes
        }

    val cooldown: Long
        get() = 0

    /**
     * A property that shows whether the command's expired interactions should
     * trigger an explicit failure response or just silent deletion of the command's
     * components (i.e. buttons and selection menus)
     */
    val eliminateStaleInteractions: Boolean
        get() = true

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
}

/**
 * A generic type that is to be implemented by classes of all the bot's commands
 * that are invoked via Discord text input
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see ClassicTextOnlyCommand
 * @see SlashOnlyCommand
 * @see SubcommandHolder
 */
interface TextCommand : GenericCommand {
    val aliases: Set<String>
        get() = emptySet()
    val usages: Set<Set<String>>
        get() = emptySet()

    /**
     * A property containing a set of data that is used for option-requiring slash commands
     *
     * **Note**: the [options] property cannot be used alongside the [SubcommandHolder.subcommands] property!
     *
     * @throws UnsupportedOperationException if the command class also implements [SubcommandHolder]
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
            .let {
                if (this !is SubcommandHolder) {
                    it.addOptions(options)
                } else {
                    it
                }
            }

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
}

/**
 * A type of command that can be used as a text command exclusively
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see SlashOnlyCommand
 */
interface ClassicTextOnlyCommand : TextCommand {
    override fun asJDASlashCommandData(): SlashCommandData? = null

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
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
     * A property containing a set of data that is used for complex slash commands
     *
     * **Note**: the [subcommands] property cannot be used alongside the [options] property!
     */
    val subcommands: Map<SubcommandData, suspend (SlashCommandInteractionEvent) -> Unit>
        get() = emptyMap()

    override val options: Set<OptionData>
        get() = throw UnsupportedOperationException("This type of slash command cannot have any options!")

    override fun asJDASlashCommandData(): SlashCommandData? =
        super.asJDASlashCommandData()?.addSubcommands(subcommands.keys)

    override suspend fun invoke(event: SlashCommandInteractionEvent) =
        throw UnsupportedOperationException("The slash command can only be invoked via its subcommands!")
}

/**
 * A type of command that can be used as a command of a Discord context menu
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see MessageContextCommand
 * @see UserContextCommand
 * @see UniversalContextCommand
 */
interface ContextCommand : GenericCommand {
    val contextName: String
    val contextTypes: Set<Command.Type>

    /**
     * @return All the [CommandData] instances of the context menu command
     */
    fun asJDAContextCommandDataInstances(): Set<CommandData> = contextTypes.map {
        Commands.context(it, contextName)
            .setGuildOnly(true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(memberPermissions))
    }.toSet()
}

/**
 * A type of command that can be used as a command of a message context menu
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see ContextCommand
 * @see UserContextCommand
 * @see UniversalContextCommand
 */
interface MessageContextCommand : ContextCommand {
    override val contextTypes: Set<Command.Type>
        get() = setOf(Command.Type.MESSAGE)

    /**
     * A function that is executed when the command is invoked as a command of a message context menu
     *
     * @param event
     * The [MessageContextInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: MessageContextInteractionEvent)
}

/**
 * A type of command that can be used as a command of a user profile context menu
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see ContextCommand
 * @see MessageContextCommand
 * @see UniversalContextCommand
 */
interface UserContextCommand : ContextCommand {
    override val contextTypes: Set<Command.Type>
        get() = setOf(Command.Type.USER)

    /**
     * A function that is executed when the command is invoked as a command of a user profile context menu
     *
     * @param event
     * The [UserContextInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: UserContextInteractionEvent)
}

/**
 * A type of command that can be used as a command of both user profile and message context menus
 *
 * @author Alexander Oksanich
 *
 * @see GenericCommand
 * @see TextCommand
 * @see ContextCommand
 * @see MessageContextCommand
 * @see UserContextCommand
 */
interface UniversalContextCommand : MessageContextCommand, UserContextCommand {
    override val contextTypes: Set<Command.Type>
        get() = setOf(Command.Type.MESSAGE, Command.Type.USER)
}