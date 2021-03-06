package io.ileukocyte.hibernum.commands.moderation

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SubcommandHolder
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.UserContextOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle

class TimeoutCommand : SlashOnlyCommand, SubcommandHolder, UserContextOnlyCommand {
    override val name = "timeout"
    override val contextName = "Timeout Member"
    override val description = "Times the specified member out or removes a timeout from them"
    override val subcommands = mapOf(
        SubcommandData("add", "Times the specified member out for the specified period")
            .addOption(OptionType.USER, "member", "The server member to timeout", true)
            .addOption(OptionType.STRING, "period", "The time to time the member out for " +
                    "(up to ${Member.MAX_TIME_OUT_LENGTH} days) (e.g. 5h34m55s)", true)
            .addOption(OptionType.STRING, "reason", "The reason for timing the member out") to ::add,
        SubcommandData("remove", "Removes a timeout from the specified member")
            .addOption(OptionType.USER, "member", "The server member to remove a timeout from", true)
            .addOption(OptionType.STRING, "reason", "The reason for removing a timeout from the member") to ::remove,
    )
    override val memberPermissions = setOf(Permission.MODERATE_MEMBERS)
    override val botPermissions = setOf(Permission.MODERATE_MEMBERS)

    private suspend fun add(event: SlashCommandInteractionEvent) {
        val member = event.getOption("member")?.asMember ?: return
        val time = event.getOption("period")?.asString ?: return
        val reason = event.getOption("reason")?.asString?.let { ": $it" }.orEmpty()

        val selfMember = event.guild?.selfMember ?: return

        if (!DURATION_REGEX.containsMatchIn(time)) {
            throw CommandException("The provided time format is invalid!")
        }

        if (event.member?.idLong == member.idLong) {
            throw CommandException("You cannot time yourself out via the command!")
        }

        if (event.member?.canInteract(member) == false) {
            throw CommandException("You have no permissions required to time ${member.user.asMention} out!")
        }

        if (!selfMember.canInteract(member) || member.hasPermission(Permission.ADMINISTRATOR)) {
            throw CommandException("${event.jda.selfUser.name} is not able to time ${member.user.asMention} out!")
        }

        val millis = durationToMillis(time).takeUnless {
            it > TimeUnit.DAYS.toMillis(Member.MAX_TIME_OUT_LENGTH.toLong())
        } ?: throw CommandException("The timeout period cannot exceed ${Member.MAX_TIME_OUT_LENGTH} days!")

        val buttons = setOf(
            Button.danger("$name-timeout", "Yes"),
            Button.secondary("$name-exit", "No"),
        )

        val hook = event
            .replyConfirmation("Are you sure you want to time ${member.user.asMention} out?")
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
                    command = this@TimeoutCommand
                    invoker = hook.idLong
                },
            ) {
                val cmdName = it.componentId.split("-").first()

                it.message.idLong == hook.idLong && it.user.idLong == event.user.idLong && cmdName == name
            } ?: return

            val deferred = buttonEvent.deferEdit().await()
            val args = buttonEvent.componentId.split("-")

            when (args.last()) {
                "timeout" -> member.timeoutFor(millis, TimeUnit.MILLISECONDS)
                    .reason("Timed out by ${event.user.asTag}" + reason)
                    .queue({
                        val embed = defaultEmbed(
                            "${member.user.asMention} has been successfully timed out for ${asText(millis)}!",
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
                "exit" -> deferred.deleteOriginal().queue(null) {}
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

    private suspend fun remove(event: SlashCommandInteractionEvent) {
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

    override suspend fun invoke(event: UserContextInteractionEvent) {
        val member = event.targetMember ?: return
        val selfMember = event.guild?.selfMember ?: return

        if (event.member?.idLong == member.idLong) {
            throw CommandException("You cannot time yourself out via the command!")
        }

        if (event.member?.canInteract(member) == false) {
            throw CommandException("You have no permissions required to time ${member.user.asMention} out!")
        }

        if (!selfMember.canInteract(member) || member.hasPermission(Permission.ADMINISTRATOR)) {
            throw CommandException("${event.jda.selfUser.name} is not able to time ${member.user.asMention} out!")
        }

        val period = TextInput
            .create("period", "Specify the Timeout Period (e.g. 5h34m55s):", TextInputStyle.SHORT)
            .build()
        val modal = Modal
            .create("$name-${event.target.idLong}-time", "Member Timeout")
            .addActionRow(period)
            .build()

        try {
            event.replyModal(modal).await()
        } catch (e: Exception) {
            event.messageChannel.sendFailure("Something unknown went wrong!").queue()

            e.printStackTrace()
        }
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val id = event.modalId.removePrefix("$name-").split("-")

        val member = event.guild?.getMemberById(id.first())
            ?: throw CommandException("The member is not available for the bot!")

        when (id.last()) {
            "time" -> {
                val time = event.getValue("period")?.asString?.takeIf {
                    DURATION_REGEX.containsMatchIn(it)
                } ?: throw CommandException("The provided time format is invalid!")

                val millis = durationToMillis(time).takeUnless {
                    it > TimeUnit.DAYS.toMillis(Member.MAX_TIME_OUT_LENGTH.toLong())
                } ?: throw CommandException("The timeout period cannot exceed ${Member.MAX_TIME_OUT_LENGTH} days!")

                val buttons = setOf(
                    Button.success("$name-reason", "Yes"),
                    Button.secondary("$name-timeout", "No"),
                )

                val hook = event
                    .replyConfirmation("Would you like to specify the timeout reason?")
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
                            command = this@TimeoutCommand
                            invoker = hook.idLong
                        },
                    ) {
                        val cmdName = it.componentId.split("-").first()

                        it.message.idLong == hook.idLong && it.user.idLong == event.user.idLong && cmdName == name
                    } ?: return

                    val args = buttonEvent.componentId.split("-")

                    when (args.last()) {
                        "reason" -> {
                            val reason = TextInput
                                .create("reason", "Specify the Reason if You Want to:", TextInputStyle.PARAGRAPH)
                                .setRequired(false)
                                .setMaxLength(512 - "Timed out by ${event.user.asTag}: ".length)
                                .build()
                            val modal = Modal
                                .create("$name-${member.idLong}-$millis-reason", "Member Timeout")
                                .addActionRow(reason)
                                .build()

                            try {
                                buttonEvent.replyModal(modal).await()
                            } catch (e: Exception) {
                                event.messageChannel.sendFailure("Something unknown went wrong!").queue()

                                e.printStackTrace()
                            }
                        }
                        "timeout" -> {
                            val deferred = buttonEvent.deferEdit().await()

                            member.timeoutFor(millis, TimeUnit.MILLISECONDS)
                                .reason("Timed out by ${event.user.asTag}")
                                .queue({
                                    val embed = defaultEmbed(
                                        "${member.user.asMention} has been successfully timed out for ${asText(millis)}!",
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
            "reason" -> {
                val time = id[1].toLongOrNull() ?: return
                val reason = event.getValue("reason")?.asString?.let { ": $it" }.orEmpty()

                member.timeoutFor(time, TimeUnit.MILLISECONDS)
                    .reason("Timed out by ${event.user.asTag}" + reason)
                    .queue({
                        val embed = defaultEmbed(
                            "${member.user.asMention} has been successfully timed out for ${asText(time)}!",
                            EmbedType.SUCCESS,
                        )

                        event.editComponents().setEmbeds(embed).queue(null) {
                            event.messageChannel.sendMessageEmbeds(embed).queue()
                        }
                    }) {
                        val embed = defaultEmbed(
                            "Something went wrong: ${it.message}".limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH),
                            EmbedType.FAILURE,
                        )

                        event.editComponents().setEmbeds(embed).queue(null) {
                            event.messageChannel.sendMessageEmbeds(embed).queue()
                        }
                    }
            }
        }
    }
}