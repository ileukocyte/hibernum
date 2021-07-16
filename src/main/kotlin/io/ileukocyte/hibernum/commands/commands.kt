@file:JvmName("Commands")
@file:Suppress("UNNECESSARY_SAFE_CALL")

package io.ileukocyte.hibernum.commands

import io.ileukocyte.hibernum.annotations.HibernumExperimental

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.build.OptionData

/**
 * A generic type that is to be implemented by all the bot's command classes
 *
 * @author Alexander Oksanich
 *
 * @see TextOnlyCommand
 * @see SlashOnlyCommand
 */
@Suppress("UNUSED")
interface Command {
    val name: String
    val description: String
    val aliases: Set<String> get() = emptySet()
    val category: CommandCategory get() =
        javaClass.`package`.name?.let { CommandCategory[it.split(".").last()] } ?: CommandCategory.UNKNOWN

    /**
     * A property that shows whether or not the command can only be used by a developer of the bot
     */
    val isDeveloper: Boolean get() = category == CommandCategory.DEVELOPER
    val cooldown: Long get() = 0
    val botPermissions: Set<Permission> get() = emptySet()
    val memberPermissions: Set<Permission> get() = emptySet()

    // may be implemented via Command#options soon
    val usages: Set<String> get() = emptySet()

    /**
     * EXPERIMENTAL: A property containing a set of data that is used for option-requiring slash commands
     */
    @HibernumExperimental
    val options: Set<OptionData> get() = emptySet()

    /** **DO NOT OVERRIDE!** */
    val id: Int get() = name.hashCode()

    /**
     * A function that is executed when the command is invoked as a classic text command
     *
     * @param event
     * [GuildMessageReceivedEvent] occurring once the command is invoked
     * @param args
     * The arguments that may be attached to the command request
     */
    suspend operator fun invoke(event: GuildMessageReceivedEvent, args: String?)

    /**
     * EXPERIMENTAL: A function that is executed when the command is invoked as a slash command
     *
     * @param event
     * The [SlashCommandEvent] occurring once the command is invoked
     */
    @HibernumExperimental
    suspend operator fun invoke(event: SlashCommandEvent)

    /**
     * EXPERIMENTAL: A function that is executed when the command's button menu is utilized by a user
     *
     * @param event
     * The [ButtonClickEvent] occurring once the command is invoked
     */
    @HibernumExperimental
    suspend operator fun invoke(event: ButtonClickEvent) {}

    /**
     * EXPERIMENTAL: A function that is executed when the command's selection menu is utilized by a user
     *
     * @param event
     * The [SelectionMenuEvent] occurring once the command is invoked
     */
    @HibernumExperimental
    suspend operator fun invoke(event: SelectionMenuEvent) {}
}

/**
 * A type of command that can be used exclusively as a text command
 *
 * @author Alexander Oksanich
 *
 * @see Command
 * @see SlashOnlyCommand
 */
interface TextOnlyCommand : Command {
    override suspend fun invoke(event: SlashCommandEvent) {}
}

/**
 * A type of command that can be used exclusively as a text command
 *
 * @author Alexander Oksanich
 *
 * @see Command
 * @see TextOnlyCommand
 */
interface SlashOnlyCommand : Command {
    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {}
}