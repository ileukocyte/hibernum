package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.defaultUsageGroupOf
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class GuessNumberCommand : TextCommand {
    override val name = "guess"
    override val description = "Launches a game where the user is supposed to guess some number within the specified range"
    override val aliases = setOf("guess-number")
    override val options = setOf(
        OptionData(OptionType.INTEGER, "min", "The beginning of the range (minimum: 0)", true)
            .setMinValue(0)
            .setMaxValue(499_995),
        OptionData(OptionType.INTEGER, "max", "The maximal value of the range (maximum: 500,000)", true)
            .setMinValue(5)
            .setMaxValue(500_000),
    )
    override val usages = setOf(defaultUsageGroupOf("min", "max"))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val (min, max) = args?.split("\\s+".toRegex())
            ?.filter { it.toIntOrNull() !== null }
            ?.take(2)
            ?.takeUnless { it.size != 2 }
            ?.map { it.toInt() }
            ?: throw NoArgumentsException

        if (min >= max) {
            throw CommandException("The minimal value cannot be greater than the maximum value!")
        }

        if (min !in 0..500_000) {
            throw CommandException("The specified values are out of the possible range of 0 through 500,000!")
        }

        if ((max - min) < 5) {
            throw CommandException("The difference between the maximal value and the minimal one " +
                    "must equal to or be greater than 5!")
        }

        val range = min..max
        val random = range.random()

        val staticProcessId = generateStaticProcessId(event.jda)

        val message = event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = "Now try to guess the number between " +
                    "${min.toDecimalFormat("#,###")} and ${max.toDecimalFormat("#,###")}!"

            author { name = "Attempt #1" }
            footer { text = "Type in \"exit\" to finish the session!" }
        }.await()

        awaitMessage(
            number = random,
            channel = event.channel,
            message = message,
            user = event.author,
            processId = staticProcessId,
        )
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val (min, max) = event.getOptionsByType(OptionType.INTEGER).take(2)
            .mapNotNull { it.asString.toIntOrNull() }
            .takeUnless { it.size != 2 }
            ?: throw CommandException("You have provided invalid arguments!")

        if (min >= max) {
            throw CommandException("The minimal value cannot be greater than the maximum value!")
        }

        if ((max - min) < 5) {
            throw CommandException("The difference between the maximal value and the minimal one " +
                    "must equal to or be greater than 5!")
        }

        val range = min..max
        val random = range.random()

        val staticProcessId = generateStaticProcessId(event.jda)

        val message = event.replyEmbed {
            color = Immutable.SUCCESS
            description = "Now try to guess the number between " +
                    "${min.toDecimalFormat("#,###")} and ${max.toDecimalFormat("#,###")}!"

            author { name = "Attempt #1" }
            footer { text = "Type in \"exit\" to finish the session!" }
        }.await()

        awaitMessage(
            number = random,
            channel = event.channel,
            message = message.retrieveOriginal().await(),
            user = event.user,
            processId = staticProcessId,
        )
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "exit" -> {
                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.editComponents().setSuccessEmbed("The game session has been finished!") {
                        text = "This message will self-delete in 5 seconds"
                    }.queue({
                        it.deleteOriginal().queueAfter(5, TimeUnit.SECONDS, null) {}
                    }) { _ ->
                        event.channel.sendSuccess("The game session has been finished!") {
                            text = "This message will self-delete in 5 seconds"
                        }.queue({
                            it.delete().queueAfter(5, TimeUnit.SECONDS, null) {}
                        }) {}
                    }
                }
                "stay" -> {
                    val message = try {
                        event.editComponents()
                            .setSuccessEmbed("The game session has been resumed!")
                            .await()
                            .retrieveOriginal()
                            .await()
                    } catch (_: ErrorResponseException) {
                        event.channel.sendSuccess("The game session has been resumed!").await()
                    }

                    awaitMessage(
                        id[1].toInt(),
                        id[2].toInt(),
                        event.channel,
                        message,
                        event.user,
                        id[3].toInt(),
                    )
                }
            }
        }
    }

    private suspend fun awaitMessage(
        _attempt: Int = 1,
        number: Int,
        channel: MessageChannel,
        message: Message,
        user: User,
        processId: Int,
    ) {
        var attempt = _attempt

        val received = try {
            channel.awaitMessage(user, this, message, processId = processId) {
                it.message.contentRaw.isInt || it.message.contentRaw.lowercase() == "exit"
            } ?: return
        } catch (_: TimeoutCancellationException) {
            channel.jda.getProcessByEntities(user, channel)?.kill(channel.jda)

            message.editMessageEmbeds(
                defaultEmbed("Time is out!", EmbedType.FAILURE)
            ).queue(null) {
                channel.sendFailure("Time is out!").queue()
            }

            return
        }

        when (val content = received.contentRaw.lowercase()) {
            "exit" -> {
                val m = received.replyConfirmation("Are you sure you want to exit?")
                    .setActionRow(
                        Button.danger("$interactionName-${user.idLong}-exit", "Yes"),
                        Button.secondary("$interactionName-${user.idLong}-$attempt-$number-$processId-stay", "No"),
                    ).await()

                channel.jda.awaitEvent<ButtonInteractionEvent>(15, TimeUnit.MINUTES, waiterProcess = waiterProcess {
                    this.channel = channel.idLong
                    users += user.idLong
                    command = this@GuessNumberCommand
                    invoker = m.idLong
                    id = processId
                }) { it.user.idLong == user.idLong && it.message == m } // used to block other commands
            }
            else -> {
                val asInt = content.toInt()

                val (desc, embedType) = when {
                    asInt > number -> {
                        attempt++

                        "The answer is greater than the expected number!" to EmbedType.FAILURE
                    }
                    asInt < number -> {
                        attempt++

                        "The answer is lesser than the expected number!" to EmbedType.FAILURE
                    }
                    else ->
                        "It took you $attempt ${"attempt".singularOrPlural(attempt)} to guess the number!" to EmbedType.SUCCESS
                }

                if (embedType == EmbedType.SUCCESS) {
                    received.replySuccess(desc).queue()
                } else {
                    val m = received.replyEmbed {
                        color = embedType.color
                        description = desc

                        author { name = "Attempt #${attempt.toDecimalFormat("#,###")}" }
                        footer { text = "Type in \"exit\" to finish the session!" }
                    }.await()

                    awaitMessage(attempt, number, channel, m, user, processId)
                }
            }
        }
    }
}