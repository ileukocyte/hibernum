package io.ileukocyte.hibernum.commands

@Suppress("UNUSED")
enum class CommandCategory {
    Developer,
    Fun,
    General,
    Music,
    Beta,
    Unknown;

    companion object {
        operator fun get(key: String) = values().firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}