package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.sendEmbed
import io.ileukocyte.hibernum.extensions.surroundWith

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class RollCommand : TextCommand {
    override val name = "roll"
    override val description = "Rolls the dice of the provided parameters"
    override val fullDescription = "$description\n\nAn example of classic text use is " +
            "${Immutable.DEFAULT_PREFIX}$name 7d27".surroundWith('`')
    override val aliases = setOf("dice")
    override val options = setOf(
        OptionData(OptionType.INTEGER, "sides", "A number of sides per die", true)
            .setMinValue(2)
            .setMaxValue(250),
        OptionData(OptionType.INTEGER, "dice", "A number of dice (1 is default)")
            .setMinValue(1)
            .setMaxValue(25),
    )
    override val usages = setOf(
        setOf("number of dice (optional, 1–25)>d<number of sides (2–250)"))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val input = args?.let { "(?:\\d+)?d(\\d+)".toRegex().find(it)?.value }
            ?.split("d")
            ?.mapNotNull { it.toIntOrNull() }
            ?: throw CommandException("The proper argument format is <number of dice>d<number of sides>!")

        val (dice, sides) = if (input.size == 1) {
            1 to input.first()
        } else {
            input.let { it.first() to it.last() }
        }

        if (dice !in 1..25) {
            throw CommandException("The number of dice must be within the range of 1 through 25!")
        }

        if (sides !in 2..250) {
            throw CommandException("The number of sides must be within the range of 2 through 250!")
        }

        val diceRolled = (1..dice)
            .map { (1..sides).random() }
        val sum = diceRolled.sum()

        event.channel.sendEmbed {
            color = Immutable.SUCCESS
            description = diceRolled.joinToString().let {
                if (diceRolled.size > 1) {
                    "[$it]"
                } else {
                    it
                }
            }

            if (diceRolled.size > 1) {
                field {
                    title = "Total Result"
                    description = "$sum"
                }
            }

            author {
                name = "${dice.takeUnless { it == 1 }?.toString().orEmpty()}d$sides Dice Roll"
                iconUrl = event.jda.selfUser.effectiveAvatarUrl
            }
        }.queue()
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val dice = event.getOption("dice")?.asInt ?: 1
        val sides = event.getOption("sides")?.asInt ?: return

        val diceRolled = (1..dice)
            .map { (1..sides).random() }
        val sum = diceRolled.sum()

        event.replyEmbed {
            color = Immutable.SUCCESS
            description = diceRolled.joinToString().let {
                if (diceRolled.size > 1) {
                    "[$it]"
                } else {
                    it
                }
            }

            if (diceRolled.size > 1) {
                field {
                    title = "Total Result"
                    description = "$sum"
                }
            }

            author {
                name = "${dice.takeUnless { it == 1 }?.toString().orEmpty()}d$sides Dice Roll"
                iconUrl = event.jda.selfUser.effectiveAvatarUrl
            }
        }.queue()
    }
}