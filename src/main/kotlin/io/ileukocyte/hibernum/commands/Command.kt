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
    val type: CommandType get() = CommandType.UNIVERSAL
    val botPermissions: Set<Permission> get() = emptySet()
    val memberPermissions: Set<Permission> get() = emptySet()
    val usages: Set<String> get() = emptySet()

    @HibernumExperimental
    val options: Set<OptionData> get() = emptySet()

    /** DO NOT OVERRIDE */
    val id: Int get() = name.hashCode()

    suspend operator fun invoke(event: GuildMessageReceivedEvent, args: String?) {}

    @HibernumExperimental
    suspend operator fun invoke(event: SlashCommandEvent) {}

    @HibernumExperimental
    suspend operator fun invoke(event: ButtonClickEvent) {}

    @HibernumExperimental
    suspend operator fun invoke(event: SelectionMenuEvent) {}

    enum class CommandType {
        UNIVERSAL, TEXT_ONLY, SLASH_ONLY
    }
}