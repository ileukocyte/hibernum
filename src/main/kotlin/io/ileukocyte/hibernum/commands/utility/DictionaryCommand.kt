package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.EmbedType
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.defaultEmbed
import io.ileukocyte.hibernum.extensions.limitTo
import io.ileukocyte.hibernum.utils.awaitEvent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class DictionaryCommand : Command {
    override val name = "dictionary"
    override val description = "Sends the provided term's definition from Merriam-Webster Dictionary"
    override val aliases = setOf("define", "definition", "dict", "merriam-webster", "mw")
    override val options = setOf(
        OptionData(OptionType.STRING, "term", "The term to define", true))
    override val usages = setOf(setOf("term"))
    override val cooldown = 5L
    override val eliminateStaleInteractions = false

    private val jsonSerializer = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonSerializer)
        }
    }

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val results = searchDefinition(args ?: throw NoArgumentsException)
            .takeUnless { it.isEmpty() }
            ?: throw CommandException("No definition has been found by the query!")
        val total = results.size

        val message = event.channel
            .sendMessageEmbeds(results.first().getEmbed(event.jda.selfUser, 0, results.size))
            .let {
                if (total > 1) {
                    it.setActionRow(pageButtons(0, total))
                } else it
            }.await()

        if (total > 1) {
            val resultEmbeds = results.withIndex().map { (i, r) ->
                r.getEmbed(event.jda.selfUser, i, total)
            }

            awaitButtonClick(resultEmbeds, 0, event.jda, message, event.author)
        }
    }

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val deferred = event.deferReply().await()
        val results = searchDefinition(event.getOption("term")?.asString ?: return)

        if (results.isEmpty()) {
            deferred.editOriginalEmbeds(
                defaultEmbed(
                    "No definition has been found by the query!",
                    EmbedType.FAILURE,
                ) { text = "This message will self-delete in 5 seconds" }
            ).queue { it.delete().queueAfter(5, TimeUnit.SECONDS, {}) {} }

            return
        }

        val total = results.size

        val message = deferred
            .editOriginalEmbeds(results.first().getEmbed(event.jda.selfUser, 0, results.size))
            .let {
                if (total > 1) {
                    it.setActionRow(pageButtons(0, total))
                } else it
            }.await()

        if (total > 1) {
            val resultEmbeds = results.withIndex().map { (i, r) ->
                r.getEmbed(event.jda.selfUser, i, total)
            }

            awaitButtonClick(resultEmbeds, 0, event.jda, message, event.user)
        }
    }

    private suspend fun awaitButtonClick(
        results: List<MessageEmbed>,
        page: Int,
        jda: JDA,
        message: Message,
        author: User,
    ) {
        val event = try {
            jda.awaitEvent<ButtonInteractionEvent>(5, TimeUnit.MINUTES) {
                it.message == message && it.user == author
            } ?: return
        } catch (_: TimeoutCancellationException) {
            message.editMessageComponents().queue()

            return
        }

        val deferred = try {
            event.deferEdit().await().retrieveOriginal().await()
        } catch (_: ErrorResponseException) {
            message
        }

        when (event.componentId.removePrefix("$name-")) {
            "exit" -> deferred.editMessageComponents().queue()
            "first" -> {
                deferred
                    .editMessageEmbeds(results.first())
                    .setActionRow(pageButtons(0, results.size))
                    .queue()

                awaitButtonClick(results, 0, jda, message, author)
            }
            "back" -> {
                if (page.dec() >= 0) {
                    deferred
                        .editMessageEmbeds(results[page.dec()])
                        .setActionRow(pageButtons(page.dec(), results.size))
                        .queue()
                }

                awaitButtonClick(
                    results,
                    page.dec().takeIf { it >= 0 } ?: 0,
                    jda,
                    message,
                    author,
                )
            }
            "next" -> {
                if (page.inc() < results.size) {
                    deferred
                        .editMessageEmbeds(results[page.inc()])
                        .setActionRow(pageButtons(page.inc(), results.size))
                        .queue()
                }

                awaitButtonClick(
                    results,
                    page.inc().takeIf { it < results.size } ?: results.size.dec(),
                    jda,
                    message,
                    author,
                )
            }
            "last" -> {
                deferred
                    .editMessageEmbeds(results.last())
                    .setActionRow(pageButtons(results.size.dec(), results.size))
                    .queue()

                awaitButtonClick(results, results.size.dec(), jda, message, author)
            }
        }
    }

    private suspend fun searchDefinition(query: String) = client
        .get("$API_BASE_URL/en/$query")
        .let {
            when (it.status) {
                HttpStatusCode.OK -> it.body<List<Word>>()
                HttpStatusCode.NotFound -> emptyList()
                else -> throw IllegalStateException()
            }
        }

    private fun Word.getEmbed(bot: SelfUser, current: Int, total: Int) = buildEmbed {
        color = Immutable.SUCCESS

        author {
            name = word
            url = "https://www.merriam-webster.com/dictionary/${URLEncoder.encode(word, "UTF-8")}"
            iconUrl = bot.effectiveAvatarUrl
        }

        val meanings = meanings
            .map { it.definitions.map { d -> it.partOfSpeech to d } }
            .flatten()

        for ((index, value) in meanings.withIndex()) {
            val (partOfSpeech, def) = value

            with(def) {
                append("**${index + 1}.** ")

                partOfSpeech?.let { append("($it) ") }

                appendLine(definition.replaceFirstChar { it.uppercase() })

                example?.let { appendLine("> **Example**: $it") }

                synonyms.takeUnless { it.isEmpty() }?.let {
                    appendLine("> **Synonyms**: ${it.joinToString()}")
                }

                antonyms.takeUnless { it.isEmpty() }?.let {
                    appendLine("> **Antonyms**: ${it.joinToString()}")
                }
            }
        }

        description = description?.limitTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)

        origin?.let {
            field {
                title = "Etymology"
                description = it.replaceFirstChar(Char::uppercase)
            }
        }

        if (total > 1) {
            footer { text = "Definition #${current.inc()} \u2022 Total: $total" }
        }
    }

    @Serializable
    private data class Word(
        val word: String,
        val origin: String? = null,
        val meanings: Set<Meaning>,
    ) {
        @Serializable
        data class Meaning(
            val partOfSpeech: String? = null,
            val definitions: Set<Definition>,
        )

        @Serializable
        data class Definition(
            val definition: String,
            val example: String? = null,
            val synonyms: Set<String> = emptySet(),
            val antonyms: Set<String> = emptySet(),
        )
    }

    private companion object {
        const val API_BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries"
    }

    private fun pageButtons(page: Int, size: Int) = setOf(
        Button.secondary("$name-first", "First Page")
            .let { if (page == 0) it.asDisabled() else it },
        Button.secondary("$name-back", "Back")
            .let { if (page == 0) it.asDisabled() else it },
        Button.secondary("$name-next", "Next")
            .let { if (page == size.dec()) it.asDisabled() else it },
        Button.secondary("$name-last", "Last Page")
            .let { if (page == size.dec()) it.asDisabled() else it },
        Button.danger("$name-exit", "Exit"),
    )
}