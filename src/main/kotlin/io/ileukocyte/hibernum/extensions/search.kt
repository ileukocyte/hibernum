package io.ileukocyte.hibernum.extensions

import net.dv8tion.jda.api.entities.Guild

fun Guild.searchMembers(query: String) = memberCache
    .filter { getSearchPriority(query, it.user.name) > 0 }
    .sortedByDescending { getSearchPriority(query, it.user.name) }

internal fun getSearchPriority(expected: String, actual: String): Byte = when {
    expected == actual -> 6
    expected.lowercase() == actual.lowercase() -> 5
    expected.startsWith(actual) -> 4
    expected.startsWith(actual, true) -> 3
    actual in expected -> 2
    actual.lowercase() in expected.lowercase() -> 1
    else -> 0
}