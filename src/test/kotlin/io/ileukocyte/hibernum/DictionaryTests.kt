package io.ileukocyte.hibernum

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ResponseException
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class DictionaryTests {
    companion object {
        const val API_BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries"
    }

    @Test
    fun `basic request`() {
        val client = HttpClient(CIO)

        runBlocking {
            assertEquals(HttpStatusCode.OK, client.get<HttpResponse>("$API_BASE_URL/en/test").status)
            assertEquals(
                HttpStatusCode.NotFound,
                assertThrows<ClientRequestException> {
                    client.get<HttpResponse>("$API_BASE_URL/en/shouldnotbefound")
                }.response.status,
            )
        }
    }

    @Test
    fun `response serialization`() {
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(CIO) {
            install(JsonFeature) { serializer = KotlinxSerializer(json) }
        }

        runBlocking {
            assertDoesNotThrow {
                for (definition in client.get<JsonArray>("$API_BASE_URL/en/test")) {
                    println(json.decodeFromJsonElement<Word>(definition))
                }
            }
        }
    }

    @Test
    fun `formatting output`() {
        suspend fun printDefinition(query: String) {
            val json = Json { ignoreUnknownKeys = true }
            val client = HttpClient(CIO) {
                install(JsonFeature) { serializer = KotlinxSerializer(json) }
            }

            val response = try {
                client.get<JsonArray>("$API_BASE_URL/en/$query")
            } catch (e: ResponseException) {
                println("No definiton has been found by the query!")

                return
            }

            for ((index, definition) in response.withIndex()) {
                val word = json.decodeFromJsonElement<Word>(definition)
                val output = buildString {
                    appendLine("------- Result #${index.inc()}: -------")
                    appendLine("Word: ${word.word}")
                    appendLine("Etymology: ${word.origin ?: "N/A"}")
                    appendLine("Meanings:")
                    appendLine(word.meanings.withIndex().joinToString("\n") { (index, it) ->
                        val definitions = it.definitions.withIndex().map { (index, it) ->
                            buildString {
                                appendLine("- Meaning #${index.inc()}: -")
                                appendLine("Definition: ${it.definition}")
                                appendLine("Example: ${it.example ?: "N/A"}")
                                appendLine("Synonyms: ${it.synonyms.joinToString().takeUnless(String::isEmpty) ?: "N/A"}")
                                append("Antonyms: ${it.antonyms.joinToString().takeUnless(String::isEmpty) ?: "N/A"}")
                            }
                        }

                        buildString {
                            appendLine("--- #${index.inc()}: ---")
                            appendLine("Part of Speech: ${it.partOfSpeech}")
                            appendLine("Definitions:\n${definitions.joinToString("\n\n")}")
                        }
                    })
                }

                println(output)
            }
        }

        runBlocking {
            for (query in setOf("test", "shouldnotbefound", "get", "saying", "take", "love")) {
                printDefinition(query)
                println("-----------------------------------------------------")
            }
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
            val partOfSpeech: String,
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
}