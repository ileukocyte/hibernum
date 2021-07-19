package io.ileukocyte.hibernum.audio

import io.ileukocyte.hibernum.extensions.capitalizeAll

enum class LoopMode {
    SONG, QUEUE, DISABLED;

    override fun toString() = name.capitalizeAll()
}