package io.ileukocyte.hibernum.commands.`fun`

import com.markozajc.akiwrapper.Akiwrapper
import com.markozajc.akiwrapper.Akiwrapper.Answer
import com.markozajc.akiwrapper.core.entities.Server.GuessType
import com.markozajc.akiwrapper.core.entities.Server.Language

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildAkiwrapper
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.getProcessByEntities
import io.ileukocyte.hibernum.utils.kill
import io.ileukocyte.hibernum.utils.waiterProcess

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu

class AkinatorCommand : Command {
    private lateinit var akiwrapper: Akiwrapper
    private lateinit var type: GuessType
    private val declinedGuesses = mutableSetOf<Long>()

    private val possibleAnswers = mapOf(
        Answer.YES to setOf("yes", "y"),
        Answer.NO to setOf("no", "n"),
        Answer.DONT_KNOW to setOf("don't know", "i", "idk", "dk", "dont know"),
        Answer.PROBABLY to setOf("probably", "p"),
        Answer.PROBABLY_NOT to setOf("probably not", "pn")
    )

    private val languagesAvailableForTypes get() =
        mapOf(
            GuessType.ANIMAL to setOf(
                Language.ENGLISH,
                Language.FRENCH,
                Language.GERMAN,
                Language.ITALIAN,
                Language.JAPANESE,
                Language.SPANISH
            ),
            GuessType.MOVIE_TV_SHOW to setOf(Language.ENGLISH, Language.FRENCH),
            GuessType.CHARACTER to Language.values().toSortedSet(),
            GuessType.OBJECT to setOf(Language.ENGLISH, Language.FRENCH)
        )

    override val name = "akinator"
    override val description = "The command launches a new Akinator game session"

    override suspend fun invoke(event: SlashCommandEvent) =
        sendGuessTypeMenu(event.channel, event.user, event)

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        sendGuessTypeMenu(event.channel, event.author)

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "type" -> {
                    type = event.selectedOptions?.firstOrNull()
                        ?.value?.let { GuessType.valueOf(it) } ?: GuessType.CHARACTER
                    val availableLanguages = languagesAvailableForTypes[type] ?: Language.values().toSortedSet()
                    val menu = SelectionMenu
                        .create("$name-${id.first()}-lang")
                        .addOptions(
                            availableLanguages.map { SelectOption.of(it.name.capitalizeAll(), it.name) }
                        ).build()

                    val message = event.channel.sendConfirmation("Choose your language!")
                        .setActionRow(menu)
                        .await()

                    event.message?.delete()?.queue()

                    event.jda.awaitEvent<SelectionMenuEvent>(waiterProcess = waiterProcess {
                        channel = event.channel.idLong
                        users += event.user.idLong
                        command = this@AkinatorCommand
                    }) { it.user.idLong == event.user.idLong && it.message == message } // used to block other commands
                }
                "lang" -> {
                    val lang = event.selectedOptions?.firstOrNull()
                        ?.value?.let { Language.valueOf(it) } ?: Language.ENGLISH

                    try {
                        akiwrapper = buildAkiwrapper {
                            guessType = type
                            language = lang
                            filterProfanity = !event.textChannel.isNSFW
                        }

                        event.channel.sendEmbed {
                            color = Immutable.SUCCESS
                            description = akiwrapper.currentQuestion?.question ?: "N/A"
                            author {
                                name = "Question #${(akiwrapper.currentQuestion?.step ?: -1) + 1}"
                            }
                            footer {
                                text = buildString {
                                    append("Type in \"help\"/\"aliases\" for further help or \"exit\" for termination!")

                                    if (event.textChannel.isNSFW) append(" | NSFW mode")
                                }
                            }
                        }
                            .flatMap { event.message?.delete() }
                            .await()

                        awaitAnswer(event.channel, event.user)
                    } catch (e: Exception) {
                        event.channel.sendFailure(e.message ?: "Something went wrong!").queue()
                    }
                }
            }
        } else event.replyFailure("You did not invoke the initial command!").setEphemeral(true).queue()
    }

    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "exit" -> {
                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event
                        .replySuccess("The Akinator session has been finished!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .queue()
                }
                "stay" -> {
                    event
                        .replySuccess("Let's go on!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .await()

                    awaitAnswer(event.channel, event.user)
                }
                "guessYes", "finalGuessYes" -> {
                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event
                        .replySuccess("Great game!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .await()
                }
                "finalGuessNo" -> {
                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.channel
                        .sendSuccess("Bravo! You have defeated me!")
                        .flatMap { event.message?.delete() }
                        .await()
                }
                else -> {
                    if (id.last().startsWith("guessNo")) {
                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        id.last().removePrefix("guessNo").toLongOrNull()?.let { declinedGuesses += it }

                        val previousAnswer = possibleAnswers.keys.first { id[1] in possibleAnswers[it]!! }
                        val nextQuestion = akiwrapper.answerCurrentQuestion(previousAnswer)

                        if (nextQuestion !== null) {
                            event
                                .replySuccess("Let's go on!")
                                .setEphemeral(true)
                                .flatMap { event.message?.delete() }
                                .await()

                            event.channel.sendEmbed {
                                color = Immutable.SUCCESS
                                description = nextQuestion.question

                                footer { text = "Type in \"exit\" to finish the session!" }

                                author {
                                    name = "Question #${nextQuestion.step + 1}"
                                }
                            }.await()

                            awaitAnswer(event.channel, event.user)
                        } else {
                            akiwrapper.guesses // always empty for an unknown reason
                                .firstOrNull { it.idLong !in declinedGuesses }
                                ?.let { finalGuess ->
                                    val m = event.channel.sendEmbed {
                                        color = Immutable.SUCCESS

                                        field {
                                            title = "Name:"
                                            description = finalGuess.name
                                        }

                                        field {
                                            title = "Description:"
                                            description = finalGuess.description ?: "None"
                                        }

                                        image = finalGuess.image?.toString()
                                    }.flatMap {
                                        it.replyConfirmation("Is this your character?")
                                            .setActionRow(
                                                Button.primary("$name-${event.user.idLong}-finalGuessYes", "Yes"),
                                                Button.danger("$name-${event.user.idLong}-finalGuessNo", "No")
                                            )
                                    }.await()

                                    event.jda.awaitEvent<ButtonClickEvent>(waiterProcess = waiterProcess {
                                        channel = event.channel.idLong
                                        users += event.user.idLong
                                        command = this@AkinatorCommand
                                    }) { it.user.idLong == event.user.idLong && it.message == m } // used to block other commands
                                } ?: event.channel.sendSuccess("Bravo! You have defeated me!").queue()
                        }
                    }
                }
            }
        } else event.replyFailure("You did not invoke the initial command!").setEphemeral(true).queue()
    }

    private suspend fun awaitAnswer(messageChannel: MessageChannel, player: User) {
        try {
            val message = messageChannel.awaitMessage(player, processCommand = this, delay = 5) ?: throw CommandException()

            when (val content = message.contentRaw.lowercase()) {
                "exit" -> {
                    val m = message.replyConfirmation("Are you sure you want to exit?")
                        .setActionRow(
                            Button.danger("$name-${player.idLong}-exit", "Yes"),
                            Button.secondary("$name-${player.idLong}-stay", "No")
                        ).await()

                    messageChannel.jda.awaitEvent<ButtonClickEvent>(waiterProcess = waiterProcess {
                        channel = messageChannel.idLong
                        users += player.idLong
                        command = this@AkinatorCommand
                    }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                }
                "debug" -> {
                    if (player.isDeveloper)
                        message.reply(akiwrapper.guesses.filter { it.idLong !in declinedGuesses }
                            .joinToString("\n") { "${it.name}: ${it.probability}" }.ifEmpty { "No guesses available" }
                        ).await()

                    awaitAnswer(messageChannel, player)
                }
                "aliases", "help" -> {
                    message.replyEmbed {
                        color = Immutable.SUCCESS

                        author {
                            name = "Possible Answers"
                            iconUrl = messageChannel.jda.selfUser.effectiveAvatarUrl
                        }

                        for ((answer, variations) in possibleAnswers) {
                            field {
                                title = answer.name
                                    .replace("_", " ")
                                    .capitalizeAll()
                                    .replace("Dont", "Don't")
                                description = variations.joinToString()
                                isInline = true
                            }
                        }
                    }.await()

                    awaitAnswer(messageChannel, player)
                }
                "b", "back" -> {
                    messageChannel.sendEmbed {
                        val isFirst = akiwrapper.currentQuestion?.step != 0
                        val question =
                            if (isFirst) akiwrapper.undoAnswer() else akiwrapper.currentQuestion

                        color = Immutable.SUCCESS
                        description = question?.question ?: "N/A"

                        author {
                            name = "Question #${(question?.step ?: -1) + 1}"
                        }

                        footer {
                            text = if (isFirst)
                                "Type in \"help\"/\"aliases\" for further help or \"exit\" for termination!"
                            else "Type in \"exit\" to finish the session!"
                        }
                    }.await()

                    awaitAnswer(messageChannel, player)
                }
                else -> {
                    if (content in possibleAnswers.values.flatten()) {
                        val guess =
                            akiwrapper.guesses.firstOrNull { it.probability >= 0.8125 && it.idLong !in declinedGuesses }

                        if (guess === null) {
                            val answer = possibleAnswers.keys.first { content in possibleAnswers[it]!! }
                            val nextQuestion = akiwrapper.answerCurrentQuestion(answer)

                            if (nextQuestion !== null) {
                                messageChannel.sendEmbed {
                                    color = Immutable.SUCCESS
                                    description = nextQuestion.question

                                    footer { text = "Type in \"exit\" to finish the session!" }

                                    author {
                                        name = "Question #${nextQuestion.step + 1}"
                                    }
                                }.await()

                                awaitAnswer(messageChannel, player)
                            } else {
                                akiwrapper.guesses // always empty for an unknown reason
                                    .firstOrNull { it.idLong !in declinedGuesses }
                                    ?.let { finalGuess ->
                                        val m = message.replyEmbed {
                                            color = Immutable.SUCCESS

                                            field {
                                                title = "Name:"
                                                description = finalGuess.name
                                            }

                                            field {
                                                title = "Description:"
                                                description = finalGuess.description ?: "None"
                                            }

                                            image = finalGuess.image?.toString()
                                        }.flatMap {
                                            it.replyConfirmation("Is this your character?")
                                                .setActionRow(
                                                    Button.primary("$name-${player.idLong}-finalGuessYes", "Yes"),
                                                    Button.danger("$name-${player.idLong}-finalGuessNo", "No")
                                                )
                                        }.await()

                                        messageChannel.jda.awaitEvent<ButtonClickEvent>(waiterProcess = waiterProcess {
                                            channel = messageChannel.idLong
                                            users += player.idLong
                                            command = this@AkinatorCommand
                                        }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                                    }  ?: messageChannel.sendSuccess("Bravo! You have defeated me!").queue()
                            }
                        } else {
                            message.replyEmbed {
                                color = Immutable.SUCCESS

                                field {
                                    title = "Name:"
                                    description = guess.name
                                }

                                field {
                                    title = "Description:"
                                    description = guess.description ?: "None"
                                }

                                image = guess.image?.toString()
                            }.flatMap {
                                it.replyConfirmation("Is this your character?")
                                    .setActionRow(
                                        Button.primary("$name-${player.idLong}-guessYes", "Yes"),
                                        Button.danger("$name-${player.idLong}-$content-guessNo${guess.id}", "No")
                                    )
                            }.await()

                            messageChannel.jda.awaitEvent<ButtonClickEvent>(waiterProcess = waiterProcess {
                                channel = messageChannel.idLong
                                users += player.idLong
                                command = this@AkinatorCommand
                            }) { it.user.idLong == player.idLong && it.message == message } // used to block other commands
                        }
                    } else {
                        messageChannel.sendFailure("Incorrect answer! Use `help`/`aliases` to get documentation!")
                            .await()

                        awaitAnswer(messageChannel, player)
                    }
                }
            }
        } catch (e: Exception) {
            messageChannel.jda.getProcessByEntities(player, messageChannel)?.kill(messageChannel.jda) // just in case

            messageChannel.sendFailure(
                if (e is TimeoutCancellationException) "Time is out!" else (e.message ?: "Something went wrong!")
            ).queue()
        }
    }

    private suspend fun sendGuessTypeMenu(
        messageChannel: MessageChannel,
        player: User,
        slashCommandEventIfPresent: SlashCommandEvent? = null
    ) {
        val menu = SelectionMenu
            .create("$name-${player.idLong}-type")
            .addOptions(
                GuessType.values()
                    .filter { it != GuessType.PLACE }
                    .map {
                        SelectOption.of(
                            if (it == GuessType.MOVIE_TV_SHOW)
                                "Movie/TV Show"
                            else
                                it.name.lowercase().capitalizeAll(),
                            it.name
                        )
                    }
            ).build()
        val messageAction = slashCommandEventIfPresent
            ?.replyConfirmation("Choose your guess type!")
            ?.addActionRow(menu)
            ?: messageChannel.sendConfirmation("Choose your guess type!").setActionRow(menu)

        val message = messageAction.await()

        messageChannel.jda.awaitEvent<SelectionMenuEvent>(waiterProcess = waiterProcess {
            channel = messageChannel.idLong
            users += player.idLong
            command = this@AkinatorCommand
        }) { it.user.idLong == player.idLong && it.message == message } // used to block other commands
    }
}