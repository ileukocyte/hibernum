package io.ileukocyte.hibernum.commands.moderation

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class RemoveTimeoutCommand : SlashOnlyCommand {
    override val name = "removetimeout"
    override val description = "Removes a timeout from the specified member"
    override val options = setOf(
        OptionData(OptionType.USER, "member", "The server member to remove a timeout from", true),
        OptionData(OptionType.STRING, "reason", "The reason for removing a timeout from the member"),
    )
    override val memberPermissions = setOf(Permission.MODERATE_MEMBERS)
    override val botPermissions = setOf(Permission.MODERATE_MEMBERS)

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val member = event.getOption("member")?.asMember?.takeIf { it.isTimedOut }
            ?: throw CommandException("The member is not timed out at the moment!")
        val reason = event.getOption("reason")?.asString?.let { ": $it" }.orEmpty()

        if (event.guild?.selfMember?.canInteract(member) == false) {
            throw CommandException("${event.jda.selfUser.name} is not able " +
                    "to remove the timeout from ${member.user.asMention}!")
        }

        try {
            member.removeTimeout().reason("Timeout removed by ${event.user.asTag}" + reason).await()

            try {
                event.replySuccess("The timeout has been successfully removed from ${member.user.asMention}!").await()
            } catch (_: ErrorResponseException) {
                event.channel.sendSuccess("The timeout has been successfully removed from ${member.user.asMention}!").queue()
            }
        } catch (e: ErrorResponseException) {
            throw CommandException("Something went wrong: ${e.message}".limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH))
        }
    }
}