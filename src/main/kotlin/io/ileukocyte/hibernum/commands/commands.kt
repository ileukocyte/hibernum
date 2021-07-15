@file:Suppress("UNNECESSARY_SAFE_CALL")

package io.ileukocyte.hibernum.commands

import io.ileukocyte.hibernum.annotations.HibernumExperimental

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.build.OptionData

@Suppress("UNUSED")
interface Command {
    val name: String
    val description: String
    val category: CommandCategory get() = javaClass.`package`.name?.let {
        val name = it.split(".").last()
        CommandCategory[name] ?: CommandCategory.Unknown
    } ?: CommandCategory.Unknown
    val isDeveloper: Boolean get() = category == CommandCategory.Developer
    val botPermissions: Set<Permission> get() = emptySet()
    val memberPermissions: Set<Permission> get() = emptySet()

    // may be implemented via Command#options soon
    val usages: Set<String> get() = emptySet()

    /**
     * A property containing a set of data that is used for option-requiring slash commands
     */
    @HibernumExperimental
    val options: Set<OptionData> get() = emptySet()

    /** DO NOT OVERRIDE */
    val id: Int get() = name.hashCode()

    /**
     * The function that is executed when the command is invoked as a classic text command
     *
     * @param event
     * [GuildMessageReceivedEvent] occurring once the command is invoked
     * @param args
     * The arguments that may be attached to the command request
     */
    suspend operator fun invoke(event: GuildMessageReceivedEvent, args: String?) {}

    /**
     * EXPERIMENTAL: The function that is executed when the command is invoked as a slash command
     *
     * @param event
     * The [SlashCommandEvent] occurring once the command is invoked
     */
    @HibernumExperimental
    suspend operator fun invoke(event: SlashCommandEvent) {}

    /**
     * EXPERIMENTAL: The function that is executed when the command's button menu is utilized by a user
     *
     * @param event
     * The [ButtonClickEvent] occurring once the command is invoked
     */
    @HibernumExperimental
    suspend operator fun invoke(event: ButtonClickEvent) {}

    /**
     * EXPERIMENTAL: The function that is executed when the command's selection menu is utilized by a user
     *
     * @param event
     * The [SelectionMenuEvent] occurring once the command is invoked
     */
    @HibernumExperimental
    suspend operator fun invoke(event: SelectionMenuEvent) {}
}

interface UniversalCommand : Command {
    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?)
    override suspend fun invoke(event: SlashCommandEvent)
}

interface TextOnlyCommand : Command {
    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?)
}

interface SlashOnlyCommand : Command {
    override suspend fun invoke(event: SlashCommandEvent)
}