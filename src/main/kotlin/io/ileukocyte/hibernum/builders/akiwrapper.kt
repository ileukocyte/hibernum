package io.ileukocyte.hibernum.builders

import com.github.markozajc.akiwrapper.AkiwrapperBuilder
import com.github.markozajc.akiwrapper.core.entities.Server
import com.github.markozajc.akiwrapper.core.entities.Server.GuessType
import com.github.markozajc.akiwrapper.core.entities.Server.Language

class KAkiwrapperBuilder {
    var guessType = GuessType.CHARACTER
    var language = Language.ENGLISH
    var filterProfanity = true
    var server: Server? = null

    @PublishedApi
    internal operator fun invoke() = AkiwrapperBuilder()
        .setGuessType(guessType)
        .setLanguage(language)
        .setFilterProfanity(filterProfanity)
        .also {
            if (server !== null) {
                it.server = server
            }
        }.build()
}

inline fun buildAkiwrapper(block: KAkiwrapperBuilder.() -> Unit) = KAkiwrapperBuilder().apply(block)()