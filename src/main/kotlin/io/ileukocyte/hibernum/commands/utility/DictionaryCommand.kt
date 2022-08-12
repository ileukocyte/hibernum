package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleComponentHandling
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextCommand
import io.ileukocyte.hibernum.commands.usageGroupOf
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import kotlin.math.min

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
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class DictionaryCommand : TextCommand {
    override val name = "dictionary"
    override val description = "Sends the provided term's definition from Merriam-Webster Dictionary"
    override val aliases = setOf("define", "definition", "dict", "merriam-webster", "mw")
    override val options = setOf(
        OptionData(OptionType.STRING, "term", "The term to define", true))
    override val usages = setOf(usageGroupOf("term".toClassicTextUsage()))
    override val cooldown = 5L
    override val staleComponentHandling = StaleComponentHandling.REMOVE_COMPONENTS

    private val jsonSerializer = Json { ignoreUnknownKeys = true }
    private val client = Immutable.HTTP_CLIENT.config {
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
            .applyIf(total > 1) { setComponents(pageButtons(0, total)) }
            .await()

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
            deferred.setFailureEmbed("No definition has been found by the query!") {
                text = "This message will self-delete in 5 seconds"
            }.queue { it.delete().queueAfter(5, TimeUnit.SECONDS, {}) {} }

            return
        }

        val total = results.size

        val message = deferred
            .editOriginalEmbeds(results.first().getEmbed(event.jda.selfUser, 0, results.size))
            .applyIf(total > 1) { setComponents(pageButtons(0, total)) }
            .await()

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

        when (event.componentId.removePrefix("$interactionName-")) {
            "exit" -> deferred.editMessageComponents().queue()
            "first" -> {
                deferred
                    .editMessageEmbeds(results.first())
                    .setComponents(pageButtons(0, results.size))
                    .queue()

                awaitButtonClick(results, 0, jda, message, author)
            }
            "back" -> {
                if (page.dec() >= 0) {
                    deferred
                        .editMessageEmbeds(results[min(page.dec(), results.lastIndex)])
                        .setComponents(pageButtons(min(page.dec(), results.lastIndex), results.size))
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
                        .setComponents(pageButtons(page.inc(), results.size))
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
                    .setComponents(pageButtons(results.size.dec(), results.size))
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
                append("**${index.inc()}.** ")

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
            footer { text = "Definition #${current.inc()} \u2022 Total: ${total.toDecimalFormat("#,###")}" }
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
        ActionRow.of(
            Button.secondary("$interactionName-first", "First Page")
                .applyIf(page == 0) { asDisabled() },
            Button.secondary("$interactionName-back", "Back")
                .applyIf(page == 0) { asDisabled() },
            Button.secondary("$interactionName-next", "Next")
                .applyIf(page == size.dec()) { asDisabled() },
            Button.secondary("$interactionName-last", "Last Page")
                .applyIf(page == size.dec()) { asDisabled() },
        ),
        ActionRow.of(Button.danger("$interactionName-exit", "Close")),
    )
}