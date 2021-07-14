package io.ileukocyte.hibernum

import io.ileukocyte.hibernum.utils.*

import org.junit.jupiter.api.Test

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

class TimeTests {
    @OptIn(ExperimentalTime::class)
    @Test
    fun parsingToText() {
        assert(asText(3911359019L) == asTextJava(3911359019L))
        assert(asText(173194L, DurationUnit.SECONDS) == asTextJava(173194L, DurationUnit.SECONDS))
    }
}