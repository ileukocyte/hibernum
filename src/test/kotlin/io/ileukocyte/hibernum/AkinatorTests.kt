package io.ileukocyte.hibernum

import com.github.markozajc.akiwrapper.core.entities.Server

import io.ileukocyte.hibernum.builders.buildAkiwrapper

import org.junit.jupiter.api.Test

class AkinatorTests {
    @Test
    fun `using different languages with different guess types`() {
        var counter = 0

        for (type in Server.GuessType.values()) {
            for (lang in Server.Language.values()) {
                println("Attempt #${++counter}:")
                println("Guess type: ${type.name}")
                println("Language: ${lang.name}")

                try {
                    val wrapper = buildAkiwrapper {
                        guessType = type
                        language = lang
                    }

                    println(wrapper.question?.question ?: "NULL QUESTION")
                } catch (t: Throwable) {
                    println("${t::class.qualifiedName}: ${t.message}")
                }
            }
        }
    }
}