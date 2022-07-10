package io.ileukocyte.hibernum.commands.moderation

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class KickCommand : SlashOnlyCommand {
    override val name = "kick"
    override val description = "Kicks the specified member from the server"
    override val options = setOf(
        OptionData(OptionType.USER, "member", "The server member to kick", true),
        OptionData(OptionType.STRING, "reason", "The reason for kicking the member"),
    )
    override val memberPermissions = setOf(Permission.KICK_MEMBERS)
    override val botPermissions = setOf(Permission.KICK_MEMBERS)

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val member = event.getOption("member")?.asMember ?: return
        val reason = event.getOption("reason")?.asString?.let { ": $it" }.orEmpty()

        if (event.member?.idLong == member.idLong) {
            throw CommandException(
                "You cannot kick yourself via the command!",
                footer = "Try leaving the server yourself instead…",
            )
        }

        if (event.member?.canInteract(member) == false) {
            throw CommandException("You have no permissions required to kick ${member.user.asMention}!")
        }

        if (event.guild?.selfMember?.canInteract(member) == false) {
            throw CommandException("${event.jda.selfUser.name} has no permissions required to kick ${member.user.asMention}!")
        }

        val buttons = setOf(
            Button.danger("$name-kick", "Yes"),
            Button.secondary("$name-exit", "No"),
        )

        val hook = event
            .replyConfirmation("Are you sure you want to kick ${member.user.asMention} from the server?")
            .addActionRow(buttons)
            .await()
            .retrieveOriginal()
            .await()

        try {
            val buttonEvent = event.jda.awaitEvent<ButtonInteractionEvent>(
                15,
                TimeUnit.MINUTES,
                waiterProcess {
                    users += event.user.idLong
                    channel = event.channel.idLong
                    command = this@KickCommand
                    invoker = hook.idLong
                },
            ) {
                val cmdName = it.componentId.split("-").first()

                it.message.idLong == hook.idLong && it.user.idLong == event.user.idLong && cmdName == name
            } ?: return

            val deferred = buttonEvent.deferEdit().await()
            val args = buttonEvent.componentId.split("-")

            when (args.last()) {
                "kick" -> {
                    member.kick("Kicked by ${event.user.asTag}" + reason).queue({
                        val embed = defaultEmbed(
                            "${member.user.asTag} has been successfully kicked from the server!",
                            EmbedType.SUCCESS,
                        )

                        deferred.editOriginalComponents().setEmbeds(embed).queue(null) {
                            event.channel.sendMessageEmbeds(embed).queue()
                        }
                    }) {
                        val embed = defaultEmbed(
                            "Something went wrong: ${it.message}".limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH),
                            EmbedType.FAILURE,
                        )

                        deferred.editOriginalComponents().setEmbeds(embed).queue(null) {
                            event.channel.sendMessageEmbeds(embed).queue()
                        }
                    }
                }
                "exit" -> deferred.deleteOriginal().queue({
                    event.replySuccess("Successfully canceled!").setEphemeral(true).queue()
                }) {}
            }
        } catch (_: TimeoutCancellationException) {
            val embed = defaultEmbed("Time is out!", EmbedType.FAILURE) {
                text = "This message will self-delete in 5 seconds"
            }

            hook.editMessageComponents().setEmbeds(embed).queue({
                it.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
            }) {
                event.channel.sendMessageEmbeds(embed).queue { m ->
                    m.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                }
            }
        }
    }
}