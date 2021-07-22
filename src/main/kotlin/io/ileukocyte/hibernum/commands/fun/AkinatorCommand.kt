package io.ileukocyte.hibernum.commands.`fun`

import com.markozajc.akiwrapper.Akiwrapper
import com.markozajc.akiwrapper.Akiwrapper.Answer
import com.markozajc.akiwrapper.core.entities.Guess
import com.markozajc.akiwrapper.core.entities.Server.GuessType
import com.markozajc.akiwrapper.core.entities.Server.Language

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildAkiwrapper
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SelfDeletion
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu

class AkinatorCommand : Command {
    override val name = "akinator"
    override val description = "Launches a new Akinator game session"
    override val aliases = setOf("aki")
    override val options by lazy {
        val choices = GuessType.values()
            .filter { it != GuessType.PLACE }
            .map {
                Choice(
                    if (it == GuessType.MOVIE_TV_SHOW)
                        "Movie/TV Show"
                    else
                        it.name.lowercase().capitalizeAll(),
                    it.name
                )
            }

        val option =
            OptionData(OptionType.STRING, "type", "Akinator's guess type (\"Character\" is the default one)")
                .addChoices(choices)

        setOf(option)
    }

    private val akiwrappers = mutableMapOf<Long, Akiwrapper>()
    private val declinedGuesses = mutableMapOf<Long, MutableSet<Long>>()
    private val types = mutableMapOf<Long, GuessType>()

    private val probabilityThreshold = 0.8125

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

    override suspend fun invoke(event: SlashCommandEvent) =
        sendLanguagesMenu(event, event.getOption("type")?.asString, true)

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) =
        sendGuessTypeMenu(event.channel.idLong, event.author.idLong, event)

    override suspend fun invoke(event: SelectionMenuEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")
        val optionValue = event.selectedOptions?.firstOrNull()?.value

        if (event.user.id == id.first()) {
            if (optionValue == "exit") {
                event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                event.replySuccess("The Akinator session has been finished!")
                    .setEphemeral(true)
                    .flatMap { event.message?.delete() }
                    .queue()

                return
            }

            when (id.last()) {
                "type" -> {
                    event.message?.delete()?.await()

                    sendLanguagesMenu(event, optionValue, false)
                }
                "lang" -> {
                    if (optionValue == "return") {
                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        event.message?.delete()?.await()

                        sendGuessTypeMenu(event.channel.idLong, event.user.idLong, event)

                        return
                    }

                    val lang = optionValue?.let { Language.valueOf(it) } ?: Language.ENGLISH

                    try {
                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        event.message?.delete()?.await()

                        val akiwrapper = buildAkiwrapper {
                            guessType = types[event.user.idLong] ?: GuessType.CHARACTER
                            language = lang
                            filterProfanity = !event.textChannel.isNSFW
                        }

                        declinedGuesses += event.user.idLong to mutableSetOf()
                        akiwrappers += event.user.idLong to akiwrapper

                        val invoker = event.channel.sendEmbed {
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
                        }.await()

                        awaitAnswer(event.channel, event.user, akiwrapper, invoker)
                    } catch (e: Exception) {
                        declinedGuesses -= event.user.idLong
                        types -= event.user.idLong
                        akiwrappers -= event.user.idLong

                        throw CommandException(e.message ?: "Something went wrong!", SelfDeletion(30))
                    }
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val akiwrapper = akiwrappers[event.user.idLong] ?: return

            when (id.last()) {
                "exit" -> {
                    declinedGuesses -= event.user.idLong
                    types -= event.user.idLong
                    akiwrappers -= event.user.idLong

                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.replySuccess("The Akinator session has been finished!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .queue()
                }
                "stay" -> {
                    event.replySuccess("Let's go on!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .await()

                    awaitAnswer(event.channel, event.user, akiwrapper)
                }
                "guessYes", "finalGuessYes" -> {
                    declinedGuesses -= event.user.idLong
                    types -= event.user.idLong
                    akiwrappers -= event.user.idLong

                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.replySuccess("Great! Guessed right one more time!")
                        .setEphemeral(true)
                        .flatMap { event.message?.delete() }
                        .await()
                }
                "finalGuessNo" -> {
                    declinedGuesses -= event.user.idLong
                    types -= event.user.idLong
                    akiwrappers -= event.user.idLong

                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.channel.sendSuccess("Bravo! You have defeated me!")
                        .flatMap { event.message?.delete() }
                        .await()
                }
                else -> {
                    if (id.last().startsWith("guessNo")) {
                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        id.last().removePrefix("guessNo").toLongOrNull()?.let { g ->
                            declinedGuesses[event.user.idLong]?.let { it += g }
                        }

                        val previousAnswer = possibleAnswers.keys.first { id[1] in possibleAnswers[it]!! }
                        val nextQuestion = akiwrapper.answerCurrentQuestion(previousAnswer)

                        if (nextQuestion !== null) {
                            event.replySuccess("Let's go on!")
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

                            awaitAnswer(event.channel, event.user, akiwrapper)
                        } else {
                            akiwrapper.guesses // always empty for an unknown reason
                                .firstOrNull { declinedGuesses[event.user.idLong]?.contains(it.idLong) == false }
                                ?.let { finalGuess ->
                                    val m = guessMessage(finalGuess, true, event.user.idLong, event.channel, event.message?.idLong)

                                    event.jda.awaitEvent<ButtonClickEvent>(15, DurationUnit.MINUTES, waiterProcess = waiterProcess {
                                        channel = event.channel.idLong
                                        users += event.user.idLong
                                        command = this@AkinatorCommand
                                        invoker = m.idLong
                                    }) { it.user.idLong == event.user.idLong && it.message == m } // used to block other commands
                                } ?: event.channel.let { channel ->
                                    declinedGuesses -= event.user.idLong
                                    types -= event.user.idLong
                                    akiwrappers -= event.user.idLong

                                    channel.sendSuccess("Bravo! You have defeated me!").queue()
                                }
                        }
                    }
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun awaitAnswer(
        messageChannel: MessageChannel,
        player: User,
        akiwrapper: Akiwrapper,
        invokingMessage: Message? = null
    ) {
        try {
            val message = messageChannel.awaitMessage(player, this, invokingMessage, delay = 5)
                ?: return

            when (val content = message.contentRaw.lowercase()) {
                "exit" -> {
                    val m = message.replyConfirmation("Are you sure you want to exit?")
                        .setActionRow(
                            Button.danger("$name-${player.idLong}-exit", "Yes"),
                            Button.secondary("$name-${player.idLong}-stay", "No")
                        ).await()

                    messageChannel.jda.awaitEvent<ButtonClickEvent>(15, DurationUnit.MINUTES, waiterProcess = waiterProcess {
                        channel = messageChannel.idLong
                        users += player.idLong
                        command = this@AkinatorCommand
                        invoker = m.idLong
                    }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                }
                "debug" -> {
                    if (player.isDeveloper)
                        message.reply(akiwrapper.guesses.filter { declinedGuesses[player.idLong]?.contains(it.idLong) == false }
                            .joinToString("\n") { "${it.name} (${it.id}): ${it.probability}" }
                            .ifEmpty { "No guesses available" }
                            .let { "$it\n\nCurrent server: ${akiwrapper.server.url}" }
                            .let { s ->
                                declinedGuesses[player.idLong]?.takeUnless { it.isEmpty() }
                                    ?.let { "$s\nDeclined guesses: $it" }
                                    ?: s
                            }
                        ).await()

                    awaitAnswer(messageChannel, player, akiwrapper)
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

                    awaitAnswer(messageChannel, player, akiwrapper)
                }
                "b", "back" -> {
                    val invoker = message.replyEmbed {
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

                    awaitAnswer(messageChannel, player, akiwrapper, invoker)
                }
                else -> {
                    if (content in possibleAnswers.values.flatten()) {
                        val guess = akiwrapper.guesses
                            .firstOrNull { it.probability >= probabilityThreshold
                                    && declinedGuesses[player.idLong]?.contains(it.idLong) == false }

                        if (guess === null) {
                            val answer = possibleAnswers.keys.first { content in possibleAnswers[it]!! }
                            val nextQuestion = akiwrapper.answerCurrentQuestion(answer)

                            if (nextQuestion !== null) {
                                val invoker = message.replyEmbed {
                                    color = Immutable.SUCCESS
                                    description = nextQuestion.question

                                    footer { text = "Type in \"exit\" to finish the session!" }

                                    author {
                                        name = "Question #${nextQuestion.step + 1}"
                                    }
                                }.await()

                                awaitAnswer(messageChannel, player, akiwrapper, invoker)
                            } else {
                                akiwrapper.guesses // always empty for an unknown reason
                                    .firstOrNull { declinedGuesses[player.idLong]?.contains(it.idLong) == false }
                                    ?.let { finalGuess ->
                                        val m = guessMessage(finalGuess, true, player.idLong, message.channel, message.idLong)

                                        messageChannel.jda.awaitEvent<ButtonClickEvent>(15, DurationUnit.MINUTES, waiterProcess = waiterProcess {
                                            channel = messageChannel.idLong
                                            users += player.idLong
                                            command = this@AkinatorCommand
                                            invoker = m.idLong
                                        }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                                    } ?: messageChannel.let { channel ->
                                        declinedGuesses -= player.idLong
                                        types -= player.idLong
                                        akiwrappers -= player.idLong

                                        channel.sendSuccess("Bravo! You have defeated me!").queue()
                                    }
                            }
                        } else {
                            val m = guessMessage(guess, false, player.idLong, message.channel, content = content)

                            messageChannel.jda.awaitEvent<ButtonClickEvent>(15, DurationUnit.MINUTES, waiterProcess = waiterProcess {
                                channel = messageChannel.idLong
                                users += player.idLong
                                command = this@AkinatorCommand
                                invoker = m.idLong
                            }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                        }
                    } else {
                        val incorrect = message.replyFailure(
                            "Incorrect answer! Use `help`/`aliases` to get documentation!",
                            "This message will self-delete in 5 seconds"
                        ).await()

                        incorrect.delete().queueAfter(5, DurationUnit.SECONDS, {}) {}

                        awaitAnswer(messageChannel, player, akiwrapper)
                    }
                }
            }
        } catch (e: Exception) {
            declinedGuesses -= player.idLong
            types -= player.idLong
            akiwrappers -= player.idLong

            messageChannel.jda.getProcessByEntities(player, messageChannel)?.kill(messageChannel.jda) // just in case

            throw CommandException(
                if (e is TimeoutCancellationException) "Time is out!" else (e.message ?: "Something went wrong!"),
                SelfDeletion(30)
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun <E : Event> sendGuessTypeMenu(channelId: Long, playerId: Long, event: E) {
        event.jda.getProcessByEntitiesIds(playerId, channelId)?.kill(event.jda) // just in case

        if (event.jda.getUserProcesses(playerId).any { it.command is AkinatorCommand && it.channel != channelId })
            throw CommandException("You have another Akinator command running somewhere else! Finish the process first!")

        val menu = SelectionMenu
            .create("$name-$playerId-type")
            .addOptions(
                *GuessType.values()
                    .filter { it != GuessType.PLACE }
                    .map {
                        SelectOption.of(
                            if (it == GuessType.MOVIE_TV_SHOW)
                                "Movie/TV Show"
                            else
                                it.name.lowercase().capitalizeAll(),
                            it.name
                        )
                    }.toTypedArray(),
                SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C"))
            ).build()

        val restAction = when (event) {
            is GuildMessageReceivedEvent ->
                event.channel.sendMessage {
                    embeds += defaultEmbed("Choose your guess type!", EmbedType.CONFIRMATION)
                    actionRows += ActionRow.of(menu)
                }
            is SlashCommandEvent ->
                event.reply {
                    embeds += defaultEmbed("Choose your guess type!", EmbedType.CONFIRMATION)
                    actionRows += ActionRow.of(menu)
                }
            is SelectionMenuEvent ->
                event.channel.sendMessage {
                    embeds += defaultEmbed("Choose your guess type!", EmbedType.CONFIRMATION)
                    actionRows += ActionRow.of(menu)
                }
            else -> null // must never occur
        }

        val message = restAction?.await() as Message

        event.jda.awaitEvent<SelectionMenuEvent>(15, DurationUnit.MINUTES, waiterProcess = waiterProcess {
            channel = channelId
            users += playerId
            command = this@AkinatorCommand
            invoker = message.idLong
        }) { it.user.idLong == playerId && it.message == message } // used to block other commands
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun sendLanguagesMenu(
        event: GenericInteractionCreateEvent,
        optionValue: String?,
        isFromSlashCommand: Boolean
    ) {
        if (isFromSlashCommand) {
            if (event.user.processes.any { it.command is AkinatorCommand && it.channel != event.messageChannel.idLong })
                throw CommandException("You have another Akinator command running somewhere else! Finish the process first!")
        }

        val type = optionValue?.let { GuessType.valueOf(it) } ?: GuessType.CHARACTER

        types += event.user.idLong to type

        val availableLanguages = languagesAvailableForTypes[type] ?: Language.values().toSortedSet()

        val menu = SelectionMenu
            .create("$name-${event.user.idLong}-lang")
            .addOptions(
                *availableLanguages.map { SelectOption.of(it.name.capitalizeAll(), it.name) }.toTypedArray(),
                SelectOption.of("Return", "return").withEmoji(Emoji.fromUnicode("\u25C0\uFE0F")),
                SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C"))
            ).build()

        val message = event.let {
            if (!isFromSlashCommand)
                it.messageChannel.sendMessage {
                    embeds += defaultEmbed("Choose your language!", EmbedType.CONFIRMATION)
                    actionRows += ActionRow.of(menu)
                }
            else
                it.reply {
                    embeds += defaultEmbed("Choose your language!", EmbedType.CONFIRMATION)
                    actionRows += ActionRow.of(menu)
                }
        }.await()

        event.jda.awaitEvent<SelectionMenuEvent>(15, DurationUnit.MINUTES, waiterProcess = waiterProcess {
            channel = event.messageChannel.idLong
            users += event.user.idLong
            command = this@AkinatorCommand
            invoker = (message as? Message)?.idLong ?: (message as? InteractionHook)?.retrieveOriginal()?.await()?.idLong
        }) { it.user.idLong == event.user.idLong && it.message == message } // used to block other commands
    }

    private suspend fun guessMessage(
        guess: Guess,
        isFinal: Boolean,
        playerId: Long,
        messageChannel: MessageChannel,
        messageIdForReply: Long? = null,
        content: String? = null
    ): Message {
        val prefix = "g".let { if (isFinal) "final" + it.uppercase() else it } + "uess"
        val buttons = setOf(
            Button.primary("$name-$playerId-${prefix}Yes", "Yes"),
            Button.danger(buildString {
                append("$name-$playerId-")

                if (!isFinal) append("$content-")

                append("${prefix}No")

                if (!isFinal) append(guess.id)
            }, "No"))
        val embed = buildEmbed {
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
        }

        val action = messageIdForReply?.let { messageChannel.retrieveMessageById(it).await().replyEmbeds(embed) }
            ?: messageChannel.sendMessageEmbeds(embed)

        return action.flatMap { it.replyConfirmation("Is this your character?").setActionRow(buttons) }.await()
    }
}