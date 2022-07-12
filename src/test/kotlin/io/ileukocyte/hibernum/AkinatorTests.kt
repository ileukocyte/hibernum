package io.ileukocyte.hibernum

import com.github.markozajc.akiwrapper.AkiwrapperBuilder
import com.github.markozajc.akiwrapper.core.entities.Server

import org.junit.jupiter.api.Test

import kotlin.test.assertNotNull

class AkinatorTests {
    @Test
    fun `running through languages and guess types`() {
        println(Server.Language.values().map { it.name })
        println(Server.GuessType.values().map { it.name })
    }

    @Test
    fun `initiating a wrapper and choosing the guess type`() {
        // TODO: write a Kotlin-style builder for Akiwrapper
        val wrapper = AkiwrapperBuilder().apply {
            guessType = Server.GuessType.OBJECT
            setFilterProfanity(false)
            language = Server.Language.ENGLISH
        }.build()

        wrapper.currentQuestion?.apply { assertNotNull(this) }.let { println(it?.question ?: "heck.") }
    }

    @Test
    fun `using other languages with other guess types`() {
        var counter = 0

        for (type in Server.GuessType.values()) {
            for (lang in Server.Language.values()) {
                println("Attempt #${++counter}:")
                println("Guess type: ${type.name}")
                println("Language: ${lang.name}")

                try {
                    val wrapper = AkiwrapperBuilder().apply {
                        guessType = type
                        language = lang
                    }.build()

                    println(wrapper.currentQuestion?.question ?: "NULL QUESTION")
                } catch (t: Throwable) {
                    println("${t::class.qualifiedName}: ${t.message}")
                }
            }
        }

        val wrapper = AkiwrapperBuilder().apply {
            guessType = Server.GuessType.OBJECT
            setFilterProfanity(false)
            language = Server.Language.ENGLISH
        }.build()

        wrapper.currentQuestion?.apply { assertNotNull(this) }.let { println(it?.question ?: "heck.") }
    }
}