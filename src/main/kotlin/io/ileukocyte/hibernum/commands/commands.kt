@file:[JvmName("Commands") Suppress("UNNECESSARY_SAFE_CALL")]

package io.ileukocyte.hibernum.commands

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

/**
 * A generic type that is to be implemented by all the bot's command classes
 *
 * @author Alexander Oksanich
 *
 * @see TextOnlyCommand
 * @see SlashOnlyCommand
 */
@Suppress("UNUSED")
interface Command : Comparable<Command> {
    val name: String
    val description: String
    val aliases: Set<String> get() = emptySet()
    val category: CommandCategory get() =
        javaClass.`package`.name?.let { CommandCategory[it.split(".").last()] } ?: CommandCategory.UNKNOWN

    /**
     * Used for the help command if the main description is too long for slash command limits
     */
    val fullDescription: String get() = description
    /**
     * A property that shows whether or not the command can only be used by a developer of the bot
     */
    val isDeveloper: Boolean get() = category == CommandCategory.DEVELOPER
    val cooldown: Long get() = 0
    val botPermissions: Set<Permission> get() = emptySet()
    val memberPermissions: Set<Permission> get() = emptySet()
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

    /** **DO NOT OVERRIDE!** */
    val id: Int get() = name.hashCode()

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

    override fun compareTo(other: Command) = name.compareTo(other.name)
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