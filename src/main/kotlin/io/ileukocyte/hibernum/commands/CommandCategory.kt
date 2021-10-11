package io.ileukocyte.hibernum.commands

import io.ileukocyte.hibernum.extensions.capitalizeAll

@Suppress("UNUSED")
enum class CommandCategory {
    DEVELOPER,
    FUN,
    GENERAL,
    INFORMATION,
    MODERATION,
    MUSIC,
    UTILITY,
    BETA,
    UNKNOWN;

    override fun toString() = name.capitalizeAll()

    companion object {
        operator fun get(key: String) = values().firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}