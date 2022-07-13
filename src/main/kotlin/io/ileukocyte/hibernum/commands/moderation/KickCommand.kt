package io.ileukocyte.hibernum.commands.moderation

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.UserContextCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.waiterProcess

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle

class KickCommand : SlashOnlyCommand, UserContextCommand {
    override val name = "kick"
    override val contextName = "Kick Member"
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

    override suspend fun invoke(event: UserContextInteractionEvent) {
        val member = event.targetMember ?: return

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

        val input = TextInput
            .create("reason", "Specify the Reason if You Want to:", TextInputStyle.SHORT)
            .setRequired(false)
            .build()
        val modal = Modal
            .create("$name-${event.target.idLong}", "Kick Reason")
            .addActionRow(input)
            .build()

        try {
            event.replyModal(modal).await()
        } catch (e: Exception) {
            event.messageChannel.sendFailure("Something unknown went wrong!").queue()

            e.printStackTrace()
        }
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val member = event.guild?.getMemberById(event.modalId.split("-").last()) ?: return
        val reason = event.getValue("reason")?.asString?.let { ": $it" }.orEmpty()

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
                    channel = event.messageChannel.idLong
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
                            event.messageChannel.sendMessageEmbeds(embed).queue()
                        }
                    }) {
                        val embed = defaultEmbed(
                            "Something went wrong: ${it.message}".limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH),
                            EmbedType.FAILURE,
                        )

                        deferred.editOriginalComponents().setEmbeds(embed).queue(null) {
                            event.messageChannel.sendMessageEmbeds(embed).queue()
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
                event.messageChannel.sendMessageEmbeds(embed).queue { m ->
                    m.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                }
            }
        }
    }
}