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
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.ResponseException
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import java.net.URLEncoder

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.SelfUser
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Button

class DictionaryCommand : Command {
    override val name = "dictionary"
    override val description = "Sends the provided term's definition from Merriam-Webster Dictionary"
    override val aliases = setOf("define", "definition", "dict", "merriam-webster", "mw")
    override val options = setOf(
        OptionData(OptionType.STRING, "term", "The term to define", true))
    override val usages = setOf(setOf("term"))
    override val cooldown = 5L

    private val jsonSerializer = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(JsonFeature) { serializer = KotlinxSerializer(jsonSerializer) }
    }

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val results = searchDefinition(args ?: throw NoArgumentsException)
            .takeUnless { it.isEmpty() }
            ?: throw CommandException("No definition has been found by the query!")
        val total = results.size

        val message = event.channel
            .sendMessageEmbeds(results.first().getEmbed(event.jda.selfUser, 0, results.size))
            .let {
                if (total > 1) {
                    it.setActionRow(
                        Button.secondary("first", "First Page"),
                        Button.secondary("back", "Back"),
                        Button.secondary("next", "Next"),
                        Button.secondary("last", "Last Page"),
                        Button.danger("exit", "Exit"),
                    )
                } else it
            }.await()

        if (total > 1) {
            val resultEmbeds = results.withIndex().map { (i, r) ->
                r.getEmbed(event.jda.selfUser, i, total)
            }

            awaitButtonClick(resultEmbeds, 0, event.jda, message, event.author)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun invoke(event: SlashCommandEvent) {
        val deferred = event.deferReply().await()
        val results = searchDefinition(event.getOption("term")?.asString ?: return)

        if (results.isEmpty()) {
            deferred.editOriginalEmbeds(
                defaultEmbed(
                    "No definition has been found by the query!",
                    EmbedType.FAILURE,
                ) { text = "This message will self-delete in 5 seconds" }
            ).queue { it.delete().queueAfter(5, DurationUnit.SECONDS, {}) {} }

            return
        }

        val total = results.size

        val message = deferred
            .editOriginalEmbeds(results.first().getEmbed(event.jda.selfUser, 0, results.size))
            .let {
                if (total > 1) {
                    it.setActionRow(
                        Button.secondary("first", "First Page"),
                        Button.secondary("back", "Back"),
                        Button.secondary("next", "Next"),
                        Button.secondary("last", "Last Page"),
                        Button.danger("exit", "Exit"),
                    )
                } else it
            }.await()

        if (total > 1) {
            val resultEmbeds = results.withIndex().map { (i, r) ->
                r.getEmbed(event.jda.selfUser, i, total)
            }

            awaitButtonClick(resultEmbeds, 0, event.jda, message, event.user)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun awaitButtonClick(
        results: List<MessageEmbed>,
        page: Int,
        jda: JDA,
        message: Message,
        author: User,
    ) {
        val event = try {
            jda.awaitEvent<ButtonClickEvent>(5, DurationUnit.MINUTES) {
                it.message == message && it.user == author
            } ?: return
        } catch (_: TimeoutCancellationException) {
            message.editMessageComponents().queue()

            return
        }

        val deferred = try {
            event.deferEdit().await().retrieveOriginal().await()
        } catch (e: ErrorResponseException) {
            message
        }

        when (event.componentId) {
            "exit" -> deferred.editMessageComponents().queue()
            "first" -> {
                deferred
                    .editMessageEmbeds(results.first())
                    .queue()

                awaitButtonClick(results, 0, jda, message, author)
            }
            "back" -> {
                if (page.dec() >= 0) {
                    deferred
                        .editMessageEmbeds(results[page - 1])
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
                        .editMessageEmbeds(results[page + 1])
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
                    .queue()

                awaitButtonClick(results, results.size - 1, jda, message, author)
            }
        }
    }

    private suspend fun searchDefinition(query: String) = try {
        client.get<List<Word>>("$API_BASE_URL/en/$query")
    } catch (e: ResponseException) {
        if (e.response.status == HttpStatusCode.NotFound) {
            emptyList()
        } else {
            throw e
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
            footer { text = "Definition #${current + 1} \u2022 Total: $total" }
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
            val synonyms: Set<String>,
            val antonyms: Set<String>,
        )
    }

    private companion object {
        const val API_BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries"
    }
}