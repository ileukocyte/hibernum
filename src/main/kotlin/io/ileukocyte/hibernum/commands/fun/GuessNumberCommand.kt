package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.getProcessByEntities
import io.ileukocyte.hibernum.utils.kill
import io.ileukocyte.hibernum.utils.waiterProcess

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Button

import java.text.DecimalFormat

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

class GuessNumberCommand : Command {
    override val name = "guess"
    override val description = "Launches a game where the user is supposed to guess some number within the specified range"
    override val aliases = setOf("guessnum", "guessnumber", "guess-number")
    override val options = setOf(
        OptionData(OptionType.INTEGER, "min", "The beginning of the range (minimum: 1)", true),
        OptionData(OptionType.INTEGER, "max", "The maximal value of the range (maximum: 500,000)", true)
    )
    override val usages = setOf("min> <max")

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val (min, max) = args?.split(" ")?.filter { it.toIntOrNull() !== null }?.take(2)
            ?.takeUnless { it.size != 2 }
            ?.map { it.toInt() }
            ?: throw NoArgumentsException

        if (min >= max)
            throw CommandException("The minimal value cannot be greater than the maximum value!")

        if (min < 1 || max > 500_000)
            throw CommandException("The specified values are out of the possible range of 1 through 500,000!")

        if ((max - min) < 5)
            throw CommandException("The difference between the maximal value and the minimal one must equal to or be greater than 5!")

        val range = min..max
        val random = range.random()

        val message = event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = "Now try to guess the number between ${DecimalFormat("#,###").format(min)} and ${
                DecimalFormat("#,###").format(max)
            }!"

            author { name = "Attempt #1" }
            footer { text = "Type in \"exit\" to finish the session!" }
        }.await()

        awaitMessage(number = random, channel = event.channel, message = message, user = event.author)
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val (min, max) = event.getOptionsByType(OptionType.INTEGER).take(2)
            .mapNotNull { it.asString.toIntOrNull() }
            .takeUnless { it.size != 2 }
            ?: throw CommandException("You have provided invalid arguments!")

        if (min >= max)
            throw CommandException("The minimal value cannot be greater than the maximum value!")

        if (min < 1 || max > 500_000)
            throw CommandException("The specified values are out of the possible range of 1 through 500,000!")

        if ((max - min) < 5)
            throw CommandException("The difference between the maximal value and the minimal one must equal to or be greater than 5!")

        val range = min..max
        val random = range.random()

        event.replyEmbed {
            color = Immutable.SUCCESS
            description = "Now try to guess the number between ${DecimalFormat("#,###").format(min)} and ${
                DecimalFormat("#,###").format(max)
            }!"

            author { name = "Attempt #1" }
            footer { text = "Type in \"exit\" to finish the session!" }
        }.await()

        awaitMessage(number = random, channel = event.channel, message = null, user = event.user)
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "exit" -> {
                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.replySuccess("The game session has been finished!")
                        .setEphemeral(true)
                        .flatMap { event.message.delete() }
                        .queue()
                }
                "stay" -> {
                    event.replySuccess("Let's go on!")
                        .setEphemeral(true)
                        .flatMap { event.message.delete() }
                        .await()

                    awaitMessage(id[1].toInt(), id[2].toInt(), event.channel, null, event.user)
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun awaitMessage(_attempt: Int = 1, number: Int, channel: MessageChannel, message: Message?, user: User) {
        var attempt = _attempt

        val received = channel.awaitMessage(user, this, message, delay = 5)
            ?: return

        when (val content = received.contentRaw.lowercase()) {
            "exit" -> {
                val m = received.replyConfirmation("Are you sure you want to exit?")
                    .setActionRow(
                        Button.danger("$name-${user.idLong}-exit", "Yes"),
                        Button.secondary("$name-${user.idLong}-$attempt-$number-stay", "No")
                    ).await()

                channel.jda.awaitEvent<ButtonClickEvent>(15, DurationUnit.MINUTES, waiterProcess = waiterProcess {
                    this.channel = channel.idLong
                    users += user.idLong
                    command = this@GuessNumberCommand
                    invoker = m.idLong
                }) { it.user.idLong == user.idLong && it.message == m } // used to block other commands
            }
            else -> {
                val asInt = content.toIntOrNull()

                if (asInt !== null) {
                    val (desc, embedType) = when {
                        asInt > number -> {
                            attempt++
                            "The answer is greater than the expected number!" to EmbedType.FAILURE
                        }
                        asInt < number -> {
                            attempt++
                            "The answer is lesser than the expected number!" to EmbedType.FAILURE
                        }
                        else -> "It took you $attempt ${"attempt".singularOrPlural(attempt)} to guess the number!" to EmbedType.SUCCESS
                    }

                    if (embedType == EmbedType.SUCCESS) {
                        received.replySuccess(desc).queue()
                    } else {
                        val m = received.replyEmbed {
                            color = embedType.color
                            description = desc

                            author { name = "Attempt #$attempt" }
                            footer { text = "Type in \"exit\" to finish the session!" }
                        }.await()

                        awaitMessage(attempt, number, channel, m, user)
                    }
                } else {
                    val incorrect = received.replyFailure(
                        "You have provided an invalid argument!",
                        "This message will self-delete in 5 seconds"
                    ).await()

                    incorrect.delete().queueAfter(5, DurationUnit.SECONDS, {}) {}

                    awaitMessage(attempt, number, channel, message, user)
                }
            }
        }
    }
}