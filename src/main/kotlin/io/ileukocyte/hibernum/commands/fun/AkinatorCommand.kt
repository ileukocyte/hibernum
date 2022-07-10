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
import io.ileukocyte.hibernum.handlers.CommandContext
import io.ileukocyte.hibernum.utils.*

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption

class AkinatorCommand : Command {
    override val name = "akinator"
    override val description = "Launches a new Akinator game session"
    override val aliases = setOf("aki")
    override val options by lazy {
        val choices = GuessType.values()
            .filter { it != GuessType.PLACE }
            .map {
                Choice(
                    if (it == GuessType.MOVIE_TV_SHOW) {
                        "Movie/TV Show"
                    } else {
                        it.name.lowercase().capitalizeAll()
                    },
                    it.name,
                )
            }

        val option =
            OptionData(OptionType.STRING, "type", "Akinator's guess type (\"Character\" is the default one)")
                .addChoices(choices)

        setOf(option)
    }

    /*private*/ companion object {
        const val PROBABILITY_THRESHOLD = 0.8125

        @JvmField
        val AKIWRAPPERS = ConcurrentHashMap<Long, Akiwrapper>()
        @JvmField
        val DECLINED_GUESSES = ConcurrentHashMap<Long, MutableSet<Long>>()
        @JvmField
        val GUESS_TYPES = ConcurrentHashMap<Long, GuessType>()

        @JvmField
        val POSSIBLE_ANSWERS = mapOf(
            Answer.YES to setOf("yes", "y"),
            Answer.NO to setOf("no", "n"),
            Answer.DONT_KNOW to setOf("don't know", "i", "idk", "dk", "dont know"),
            Answer.PROBABLY to setOf("probably", "p"),
            Answer.PROBABLY_NOT to setOf("probably not", "pn"),
        )

        @JvmField
        val LANGUAGES_AVAILABLE_FOR_TYPES = mapOf(
            GuessType.ANIMAL to sortedSetOf(
                Language.ENGLISH,
                Language.FRENCH,
                Language.GERMAN,
                Language.ITALIAN,
                Language.JAPANESE,
                Language.SPANISH,
            ),
            GuessType.MOVIE_TV_SHOW to sortedSetOf(Language.ENGLISH, Language.FRENCH),
            GuessType.CHARACTER to Language.values().toSortedSet(),
            GuessType.OBJECT to sortedSetOf(Language.ENGLISH, Language.FRENCH),
        )
    }

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val staticProcessId = (1..9999).filter {
            it !in event.jda.processes.map { p -> p.id.toInt() }
        }.random()

        sendGuessTypeMenu(event.channel.idLong, event.author.idLong, event, staticProcessId)
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val staticProcessId = (1..9999).filter {
            it !in event.jda.processes.map { p -> p.id.toInt() }
        }.random()

        sendLanguagesMenu(event, event.getOption("type")?.asString, staticProcessId)
    }

    override suspend fun invoke(event: SelectMenuInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")
        val optionValue = event.selectedOptions.firstOrNull()?.value

        if (event.user.id == id.first()) {
            if (optionValue == "exit") {
                event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                DECLINED_GUESSES -= event.user.idLong
                GUESS_TYPES -= event.user.idLong

                event.replySuccess("The Akinator session has been finished!")
                    .setEphemeral(true)
                    .flatMap { event.message.delete() }
                    .queue()

                return
            }

            val processId = id[1].toInt()

            when (id.last()) {
                "type" ->
                    sendLanguagesMenu(event, optionValue, processId)
                "lang" -> {
                    if (optionValue == "return") {
                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        sendGuessTypeMenu(event.channel.idLong, event.user.idLong, event, processId)

                        return
                    }

                    val lang = optionValue?.let { Language.valueOf(it) } ?: Language.ENGLISH
                    val deferred = event.editComponents()
                        .setEmbeds(buildEmbed {
                            color = Immutable.SUCCESS
                            description = "Waiting for Akinator's response\u2026"
                        })
                        .await()

                    try {
                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        CoroutineScope(CommandContext).launch {
                            event.jda.awaitEvent<MessageReceivedEvent>(waiterProcess = waiterProcess {
                                channel = event.channel.idLong
                                users += event.user.idLong
                                command = this@AkinatorCommand
                                this@waiterProcess.id = processId
                            }) { false } // used to block other commands
                        }

                        val enableNsfwMode = try {
                            !event.textChannel.isNSFW
                        } catch (_: IllegalStateException) {
                            true
                        }

                        val akiwrapper = buildAkiwrapper {
                            guessType = GUESS_TYPES[event.user.idLong] ?: GuessType.CHARACTER
                            language = lang
                            filterProfanity = enableNsfwMode
                        }

                        AKIWRAPPERS += event.user.idLong to akiwrapper
                        DECLINED_GUESSES += event.user.idLong to mutableSetOf()

                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        val embed = buildEmbed {
                            color = Immutable.SUCCESS
                            description = akiwrapper.currentQuestion?.question ?: "N/A"

                            author { name = "Question #${akiwrapper.currentQuestion?.step?.inc() ?: -1}" }

                            footer {
                                text = buildString {
                                    append("Type in \"help\"/\"aliases\" for further help or \"exit\" for termination!")

                                    if (!enableNsfwMode) {
                                        append(" | NSFW mode")
                                    }
                                }
                            }
                        }

                        val invoker = deferred.editOriginalEmbeds(embed).await()

                        awaitAnswer(event.channel, event.user, akiwrapper, invoker, processId)
                    } catch (e: Exception) {
                        deferred.deleteOriginal().queue()

                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        AKIWRAPPERS -= event.user.idLong
                        DECLINED_GUESSES -= event.user.idLong
                        GUESS_TYPES -= event.user.idLong

                        throw CommandException(e.message ?: "Something went wrong!", SelfDeletion(60))
                    }
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val akiwrapper = AKIWRAPPERS[event.user.idLong] ?: return
            val processId = id[1].toInt()

            when (id.last()) {
                "exit" -> {
                    DECLINED_GUESSES -= event.user.idLong
                    GUESS_TYPES -= event.user.idLong
                    AKIWRAPPERS -= event.user.idLong

                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.replySuccess("The Akinator session has been finished!")
                        .setEphemeral(true)
                        .flatMap { event.message.delete() }
                        .queue()
                }
                "stay" -> {
                    event.replySuccess("Let's go on!")
                        .setEphemeral(true)
                        .flatMap { event.message.delete() }
                        .await()

                    awaitAnswer(event.channel, event.user, akiwrapper, processId = processId)
                }
                "guessYes", "finalGuessYes" -> {
                    AKIWRAPPERS -= event.user.idLong
                    DECLINED_GUESSES -= event.user.idLong
                    GUESS_TYPES -= event.user.idLong

                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.replySuccess("Great! Guessed right one more time!")
                        .setEphemeral(true)
                        .flatMap { event.message.delete() }
                        .await()
                }
                "finalGuessNo" -> {
                    AKIWRAPPERS -= event.user.idLong
                    DECLINED_GUESSES -= event.user.idLong
                    GUESS_TYPES -= event.user.idLong

                    event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                    event.channel.sendSuccess("Bravo! You have defeated me!")
                        .flatMap { event.message.delete() }
                        .await()
                }
                else -> {
                    if (id.last().startsWith("guessNo")) {
                        event.jda.getProcessByEntities(event.user, event.channel)?.kill(event.jda)

                        id.last().removePrefix("guessNo").toLongOrNull()?.let { g ->
                            DECLINED_GUESSES[event.user.idLong]?.let { it += g }
                        }

                        val previousAnswer = POSSIBLE_ANSWERS.keys.first { id[1] in POSSIBLE_ANSWERS[it]!! }
                        val nextQuestion = akiwrapper.answerCurrentQuestion(previousAnswer)

                        if (nextQuestion !== null) {
                            event.replySuccess("Let's go on!")
                                .setEphemeral(true)
                                .flatMap { event.message.delete() }
                                .await()

                            event.channel.sendEmbed {
                                color = Immutable.SUCCESS
                                description = nextQuestion.question

                                footer { text = "Type in \"exit\" to finish the session!" }

                                author { name = "Question #${nextQuestion.step + 1}" }
                            }.await()

                            awaitAnswer(event.channel, event.user, akiwrapper, processId = processId)
                        } else {
                            akiwrapper.guesses // always empty for an unknown reason
                                .firstOrNull { DECLINED_GUESSES[event.user.idLong]?.contains(it.idLong) == false }
                                ?.let { finalGuess ->
                                    val m = guessMessage(finalGuess, true, event.user.idLong, event.channel, event.message.idLong)

                                    event.jda.awaitEvent<ButtonInteractionEvent>(15, TimeUnit.MINUTES, waiterProcess = waiterProcess {
                                        channel = event.channel.idLong
                                        users += event.user.idLong
                                        command = this@AkinatorCommand
                                        invoker = m.idLong
                                        this@waiterProcess.id = processId
                                    }) { it.user.idLong == event.user.idLong && it.message == m } // used to block other commands
                                } ?: event.channel.let { channel ->
                                    AKIWRAPPERS -= event.user.idLong
                                    DECLINED_GUESSES -= event.user.idLong
                                    GUESS_TYPES -= event.user.idLong

                                    channel.sendSuccess("Bravo! You have defeated me!").queue()
                                }
                        }
                    }
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private suspend fun awaitAnswer(
        channel: MessageChannel,
        player: User,
        akiwrapper: Akiwrapper,
        invokingMessage: Message? = null,
        processId: Int,
    ) {
        try {
            val message = channel.awaitMessage(
                player,
                this,
                invokingMessage,
                delay = 5,
                processId = processId,
            ) ?: return

            when (val content = message.contentRaw.lowercase()) {
                "exit" -> {
                    val m = message.replyConfirmation("Are you sure you want to exit?")
                        .setActionRow(
                            Button.danger("$name-${player.idLong}-$processId-exit", "Yes"),
                            Button.secondary("$name-${player.idLong}-$processId-stay", "No"),
                        ).await()

                    channel.jda.awaitEvent<ButtonInteractionEvent>(waiterProcess = waiterProcess {
                        this.channel = channel.idLong
                        users += player.idLong
                        command = this@AkinatorCommand
                        invoker = m.idLong
                        id = processId
                    }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                }
                "debug" -> {
                    val reply = if (player.isDeveloper) {
                        message.reply(akiwrapper.guesses
                            .filter { DECLINED_GUESSES[player.idLong]?.contains(it.idLong) == false }
                            .joinToString("\n") { "${it.name} (${it.id}): ${it.probability}" }
                            .ifEmpty { "No guesses available" }
                            .let { "$it\n\nCurrent server: ${akiwrapper.server.url}" }
                            .let { s ->
                                DECLINED_GUESSES[player.idLong]?.takeUnless { it.isEmpty() }
                                    ?.let { "$s\nDeclined guesses: $it" }
                                    ?: s
                            }
                        )
                    } else {
                        message
                            .replyFailure("Debugging is only available for the bot's developers!")
                    }

                    reply.queue()

                    awaitAnswer(channel, player, akiwrapper, invokingMessage, processId)
                }
                "aliases", "help" -> {
                    message.replyEmbed {
                        color = Immutable.SUCCESS

                        author {
                            name = "Possible Answers"
                            iconUrl = channel.jda.selfUser.effectiveAvatarUrl
                        }

                        for ((answer, variations) in POSSIBLE_ANSWERS) {
                            field {
                                title = answer.name
                                    .replace('_', ' ')
                                    .capitalizeAll()
                                    .replace("Dont", "Don't")
                                description = variations.joinToString()
                                isInline = true
                            }
                        }
                    }.await()

                    awaitAnswer(channel, player, akiwrapper, invokingMessage, processId)
                }
                "b", "back" -> {
                    val invoker = message.replyEmbed {
                        val (question, embedColor) = if (akiwrapper.currentQuestion?.step != 0) {
                            akiwrapper.undoAnswer() to Immutable.SUCCESS
                        } else {
                            akiwrapper.currentQuestion to Immutable.FAILURE
                        }

                        color = embedColor
                        description = question?.question ?: "N/A"

                        author { name = "Question #${question?.step?.inc() ?: 0}" }

                        footer {
                            text = "Type in \"exit\" to finish the session!"
                                .takeUnless { akiwrapper.currentQuestion?.step == 0 }
                                ?: "Type in \"help\"/\"aliases\" for further help or \"exit\" for termination!"
                        }
                    }.await()

                    awaitAnswer(channel, player, akiwrapper, invoker, processId)
                }
                else -> {
                    if (content in POSSIBLE_ANSWERS.values.flatten()) {
                        val guess = akiwrapper.guesses
                            .firstOrNull { it.probability >= PROBABILITY_THRESHOLD
                                    && DECLINED_GUESSES[player.idLong]?.contains(it.idLong) == false }

                        if (guess === null) {
                            val answer = POSSIBLE_ANSWERS.keys.first { content in POSSIBLE_ANSWERS[it]!! }
                            val nextQuestion = akiwrapper.answerCurrentQuestion(answer)

                            if (nextQuestion !== null) {
                                val invoker = message.replyEmbed {
                                    color = Immutable.SUCCESS
                                    description = nextQuestion.question

                                    author { name = "Question #${nextQuestion.step + 1}" }
                                    footer { text = "Type in \"exit\" to finish the session!" }
                                }.await()

                                awaitAnswer(channel, player, akiwrapper, invoker, processId)
                            } else {
                                akiwrapper.guesses // always empty for an unknown reason
                                    .firstOrNull { DECLINED_GUESSES[player.idLong]?.contains(it.idLong) == false }
                                    ?.let { finalGuess ->
                                        val m = guessMessage(finalGuess, true, player.idLong, message.channel, message.idLong)

                                        channel.jda.awaitEvent<ButtonInteractionEvent>(waiterProcess = waiterProcess {
                                            this.channel = channel.idLong
                                            users += player.idLong
                                            command = this@AkinatorCommand
                                            invoker = m.idLong
                                            id = processId
                                        }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                                    } ?: channel.let {
                                        AKIWRAPPERS -= player.idLong
                                        DECLINED_GUESSES -= player.idLong
                                        GUESS_TYPES -= player.idLong

                                        it.sendSuccess("Bravo! You have defeated me!").queue()
                                    }
                            }
                        } else {
                            val m = guessMessage(guess, false, player.idLong, message.channel, content = content)

                            channel.jda.awaitEvent<ButtonInteractionEvent>(waiterProcess = waiterProcess {
                                this.channel = channel.idLong
                                users += player.idLong
                                command = this@AkinatorCommand
                                invoker = m.idLong
                                id = processId
                            }) { it.user.idLong == player.idLong && it.message == m } // used to block other commands
                        }
                    } else {
                        val incorrect =
                            message.replyFailure("Incorrect answer! Use `help`/`aliases` to get documentation!") {
                                text = "This message will self-delete in 5 seconds"
                            }.await()

                        incorrect.delete().queueAfter(5, TimeUnit.SECONDS, {}) {}

                        awaitAnswer(channel, player, akiwrapper, invokingMessage, processId)
                    }
                }
            }
        } catch (e: Exception) {
            AKIWRAPPERS -= player.idLong
            DECLINED_GUESSES -= player.idLong
            GUESS_TYPES -= player.idLong

            channel.jda.getProcessByEntities(player, channel)?.kill(channel.jda) // just in case

            throw CommandException(
                if (e is TimeoutCancellationException) {
                    "Time is out!"
                } else {
                    e.message ?: "Something went wrong!"
                },
                SelfDeletion(30),
            )
        }
    }

    private suspend fun <E : Event> sendGuessTypeMenu(channelId: Long, playerId: Long, event: E, processId: Int) {
        event.jda.getProcessByEntitiesIds(playerId, channelId)?.kill(event.jda) // just in case

        if (event.jda.getUserProcesses(playerId).any { it.command is AkinatorCommand && it.channel != channelId })
            throw CommandException("You have another Akinator command running somewhere else! Finish the process first!")

        val menu = SelectMenu
            .create("$name-$playerId-$processId-type")
            .addOptions(
                *GuessType.values()
                    .filter { it != GuessType.PLACE }
                    .map {
                        SelectOption.of(
                            if (it == GuessType.MOVIE_TV_SHOW) {
                                "Movie/TV Show"
                            } else {
                                it.name.lowercase().capitalizeAll()
                            },
                            it.name,
                        )
                    }.toTypedArray(),
                SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C")),
            ).build()

        val message = when (event) {
            is MessageReceivedEvent ->
                event.message.replyConfirmation("Choose your guess type!")
                    .setActionRow(menu)
                    .await()
            is SlashCommandInteractionEvent ->
                event.reply {
                    embeds += defaultEmbed("Choose your guess type!", EmbedType.CONFIRMATION)
                    actionRows += ActionRow.of(menu)
                }.await().retrieveOriginal().await()
            is SelectMenuInteractionEvent ->
                event.deferEdit()
                    .await()
                    .editOriginalEmbeds(defaultEmbed("Choose your guess type!", EmbedType.CONFIRMATION))
                    .setActionRow(menu)
                    .await()
            else -> null // must never occur
        } as Message

        event.jda.awaitEvent<SelectMenuInteractionEvent>(waiterProcess = waiterProcess {
            channel = channelId
            users += playerId
            command = this@AkinatorCommand
            invoker = message.idLong
            id = processId
        }) { it.user.idLong == playerId && it.message == message } // used to block other commands
    }

    private suspend fun sendLanguagesMenu(
        callback: IReplyCallback,
        optionValue: String?,
        processId: Int,
    ) {
        if (callback is SlashCommandInteractionEvent) {
            if (callback.user.processes.any { it.command is AkinatorCommand && it.channel != callback.messageChannel.idLong })
                throw CommandException("You have another Akinator command running somewhere else! Finish the process first!")
        }

        val type = optionValue?.let { GuessType.valueOf(it) } ?: GuessType.CHARACTER

        GUESS_TYPES += callback.user.idLong to type

        val availableLanguages = LANGUAGES_AVAILABLE_FOR_TYPES[type] ?: Language.values().toSortedSet()

        val menu = SelectMenu
            .create("$name-${callback.user.idLong}-$processId-lang")
            .addOptions(
                *availableLanguages.map { SelectOption.of(it.name.capitalizeAll(), it.name) }.toTypedArray(),
                SelectOption.of("Return", "return").withEmoji(Emoji.fromUnicode("\u25C0\uFE0F")),
                SelectOption.of("Exit", "exit").withEmoji(Emoji.fromUnicode("\u274C")),
            ).build()

        val message = if (callback is SelectMenuInteractionEvent) {
            callback.deferEdit().await()
                .editOriginalEmbeds(defaultEmbed("Choose your language!", EmbedType.CONFIRMATION))
                .setActionRow(menu)
                .await()
        } else {
            callback.reply {
                embeds += defaultEmbed("Choose your language!", EmbedType.CONFIRMATION)
                actionRows += ActionRow.of(menu)
            }.await().retrieveOriginal().await()
        }

        callback.jda.awaitEvent<SelectMenuInteractionEvent>(waiterProcess = waiterProcess {
            channel = callback.messageChannel.idLong
            users += callback.user.idLong
            command = this@AkinatorCommand
            invoker = message.idLong
            id = processId
        }) { it.user.idLong == callback.user.idLong && it.message == message } // used to block other commands
    }

    private suspend fun guessMessage(
        guess: Guess,
        isFinal: Boolean,
        playerId: Long,
        messageChannel: MessageChannel,
        messageIdForReply: Long? = null,
        content: String? = null,
    ): Message {
        val prefix = "g".let { if (isFinal) "final" + it.uppercase() else it } + "uess"
        val buttons = setOf(
            Button.primary("$name-$playerId-${prefix}Yes", "Yes"),
            Button.danger(buildString {
                append("$name-$playerId-")

                if (!isFinal) {
                    append("$content-")
                }

                append("${prefix}No")

                if (!isFinal) {
                    append(guess.id)
                }
            }, "No"),
        )
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

        return action
            .flatMap { it.replyConfirmation("Is this your character?").setActionRow(buttons) }
            .await()
    }
}