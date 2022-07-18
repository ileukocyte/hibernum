package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.extensions.replyEmbed
import io.ileukocyte.hibernum.extensions.sendEmbed

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class EightBallCommand : TextCommand {
    override val name = "eightball"
    override val description = "Asks your question of the Magic 8 Ball"
    override val aliases = setOf("8ball", "eight-ball")
    override val usages = setOf(setOf("question"))
    override val options = setOf(
        OptionData(OptionType.STRING, "question", "The question to ask of the Magic 8 Ball", true))

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        if (args === null) {
            throw NoArgumentsException
        }

        val (text, embedColor) = POSSIBLE_ANSWERS.entries.random()

        event.message.replyEmbed {
            color = embedColor
            description = text + when (embedColor) {
                Immutable.SUCCESS -> "!"
                Immutable.WARNING, Immutable.FAILURE -> "\u2026"
                else -> ""
            }
        }.queue(null) {
            event.channel.sendEmbed {
                color = embedColor
                description = text + when (embedColor) {
                    Immutable.SUCCESS -> "!"
                    Immutable.WARNING, Immutable.FAILURE -> "\u2026"
                    else -> ""
                }
            }.queue()
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val (text, embedColor) = POSSIBLE_ANSWERS.entries.random()

        event.replyEmbed {
            color = embedColor
            description = text + when (embedColor) {
                Immutable.SUCCESS -> "!"
                Immutable.WARNING, Immutable.FAILURE -> "\u2026"
                else -> ""
            }
        }.queue()
    }

    companion object {
        @JvmField
        val CERTAIN_POSITIVE_ANSWERS = setOf(
            "It is certain",
            "It is decidedly so",
            "Without a doubt",
            "Yes, definitely",
            "You may rely on it",
        ).associateWith { Immutable.SUCCESS }

        @JvmField
        val POSITIVE_ANSWERS = setOf(
            "As I see it, yes",
            "Most likely",
            "Outlook good",
            "Yes",
            "Signs point to yes",
        ).associateWith { Immutable.CONFIRMATION }

        @JvmField
        val NON_COMMITAL_ANSWERS = setOf(
            "Reply hazy, try again",
            "Ask again later",
            "Better not tell you now",
            "Cannot predict now",
            "Concentrate and ask again",
        ).associateWith { Immutable.WARNING }

        @JvmField
        val NEGATIVE_ANSWERS = setOf(
            "Don't count on it",
            "My reply is no",
            "My sources say no",
            "Outlook not so good",
            "Very doubtful",
        ).associateWith { Immutable.FAILURE }

        @JvmField
        val POSSIBLE_ANSWERS = CERTAIN_POSITIVE_ANSWERS +
                POSITIVE_ANSWERS +
                NON_COMMITAL_ANSWERS +
                NEGATIVE_ANSWERS
    }
}