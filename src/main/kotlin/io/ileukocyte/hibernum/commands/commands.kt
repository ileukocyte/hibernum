@file:[JvmName("Commands") Suppress("UNNECESSARY_SAFE_CALL")]

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
import net.dv8tion.jda.api.interactions.commands.Command as JDACommand
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

/**
 * A generic type that is to be implemented by classes of all the bot's commands
 *
 * @author Alexander Oksanich
 *
 * @see Command
 * @see TextOnlyCommand
 * @see SlashOnlyCommand
 * @see ContextCommand
 * @see MessageContextCommand
 * @see UserContextCommand
 */
interface GenericCommand : Comparable<GenericCommand> {
    val name: String
    val category: CommandCategory get() =
        javaClass.`package`.name?.let { CommandCategory[it.split(".").last()] } ?: CommandCategory.UNKNOWN

    /**
     * A property that shows whether or not the command can only be used by a developer of the bot
     */
    val isDeveloper: Boolean get() = category == CommandCategory.DEVELOPER

    val inputTypes: Set<InputType> get() {
        val inputTypes = mutableSetOf<InputType>()

        if (this is Command) {
            if (this !is SlashOnlyCommand) {
                inputTypes += InputType.TEXT
            }

            if (this !is TextOnlyCommand) {
                inputTypes += InputType.SLASH
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

    val cooldown: Long get() = 0
    val eliminateStaleInteractions: Boolean get() = true

    val botPermissions: Set<Permission> get() = emptySet()
    val memberPermissions: Set<Permission> get() = emptySet()

    /** **DO NOT OVERRIDE!** */
    val id: Int get() = name.hashCode()

    override fun compareTo(other: GenericCommand) = name.compareTo(other.name)

    enum class InputType {
        TEXT,
        SLASH,
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
 * @see TextOnlyCommand
 * @see SlashOnlyCommand
 */
interface Command : GenericCommand {
    val description: String
    val aliases: Set<String> get() = emptySet()

    /**
     * Used for the help command if the main description is too long for slash command limits
     */
    val fullDescription: String get() = description

    val usages: Set<Set<String>> get() = emptySet()

    /**
     * A property containing a set of data that is used for option-requiring slash commands
     */
    val options: Set<OptionData> get() = emptySet()

    /**
     * A [SlashCommandData] instance of the slash command
     */
    val asSlashCommand: SlashCommandData? get() =
        Commands.slash(name, "(Developer-only) ".takeIf { isDeveloper }.orEmpty() + description)
            .addOptions(options)
            .setGuildOnly(true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(memberPermissions))

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
}

/**
 * A type of command that can be used as a text command exclusively
 *
 * @author Alexander Oksanich
 *
 * @see Command
 * @see SlashOnlyCommand
 */
interface TextOnlyCommand : Command {
    override val asSlashCommand: SlashCommandData? get() = null

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        // must be left empty
    }
}

/**
 * A type of command that can be used as a slash command exclusively
 *
 * @author Alexander Oksanich
 *
 * @see Command
 * @see TextOnlyCommand
 */
interface SlashOnlyCommand : Command {
    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        // must be left empty
    }
}

/**
 * A type of command that can be used as a command of a Discord context menu
 *
 * @author Alexander Oksanich
 *
 * @see Command
 * @see MessageContextCommand
 * @see UserContextCommand
 */
interface ContextCommand : GenericCommand {
    val contextName: String
    val contextTypes: Set<JDACommand.Type>

    /**
     * All the [CommandData] instances of the context menu command
     */
    val asContextCommands: Set<CommandData> get() = contextTypes.map {
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
 * @see Command
 * @see ContextCommand
 * @see UserContextCommand
 */
interface MessageContextCommand : ContextCommand {
    override val contextTypes: Set<JDACommand.Type> get() =
        setOf(JDACommand.Type.MESSAGE)

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
 * @see Command
 * @see ContextCommand
 * @see MessageContextCommand
 */
interface UserContextCommand : ContextCommand {
    override val contextTypes: Set<JDACommand.Type> get() =
        setOf(JDACommand.Type.USER)

    /**
     * A function that is executed when the command is invoked as a command of a user profile context menu
     *
     * @param event
     * The [UserContextInteractionEvent] occurring once the command is invoked
     */
    suspend operator fun invoke(event: UserContextInteractionEvent)
}