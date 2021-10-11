package io.ileukocyte.hibernum.extensions

import net.dv8tion.jda.api.entities.Guild

fun Guild.searchMembers(query: String) = memberCache
    .filter { getSearchPriority(query, it.user.name) > 0 }
    .sortedByDescending { getSearchPriority(query, it.user.name) }

internal fun getSearchPriority(expected: String, actual: String) = when {
    expected == actual -> 6
    expected.lowercase() == actual.lowercase() -> 5
    actual.startsWith(expected) -> 4
    actual.startsWith(expected, true) -> 3
    expected in actual -> 2
    expected.lowercase() in actual.lowercase() -> 1
    else -> 0
}