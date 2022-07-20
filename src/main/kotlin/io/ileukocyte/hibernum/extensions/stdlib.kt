@file:JvmName("StandardLibraryExtensions")
package io.ileukocyte.hibernum.extensions

import java.util.concurrent.CompletableFuture

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// Java concurrency
suspend fun <T> CompletableFuture<T>.await() = suspendCoroutine<T> { c ->
    whenComplete { r, e -> e?.let { c.resumeWithException(it) } ?: c.resume(r) }
}

// kotlin.Boolean
val Boolean.asWord get() = if (this) {
    "Yes"
} else {
    "No"
}

// kotlin.Int
val Int.charName: String?
    get() = Character.getName(this)

fun Int.toChars(): CharArray = Character.toChars(this)

// kotlin.String
val String.isByte get() = toByteOrNull() !== null
val String.isUByte get() = toUByteOrNull() != null
val String.isShort get() = toShortOrNull() !== null
val String.isUShort get() = toUShortOrNull() != null
val String.isInt get() = toIntOrNull() !== null
val String.isUInt get() = toUIntOrNull() != null
val String.isLong get() = toLongOrNull() !== null
val String.isULong get() = toULongOrNull() != null

fun String.capitalizeAll() =
    lowercase().split(" ").joinToString(" ") {
        it.replaceFirstChar { c -> c.uppercase() }
    }

fun String.containsAll(vararg args: CharSequence): Boolean {
    for (element in args) {
        if (element !in this) {
            return false
        }
    }

    return true
}
fun String.containsAll(elements: Collection<CharSequence>) = containsAll(*elements.toTypedArray())
fun String.containsAll(vararg args: Char) = toCharArray().toList().containsAll(args.toList())

// Only works with UTF-16 encoding
fun String.limitTo(limit: Int, trim: Boolean = true) = take(limit)
    .let {
        if (length > limit) {
            (it.removeLastChar().takeIf { trim }?.trim()
                ?: it.removeLastChar()) + '\u2026'
        } else {
            this
        }
    }

fun String.remove(input: String) = replace(input, "")
fun String.remove(regex: Regex) = replace(regex, "")

fun String.removeLastChar() = substring(0, length.dec())

fun String.replaceLastChar(charSequence: CharSequence) = removeLastChar() + charSequence
fun String.replaceLastChar(char: Char) = removeLastChar() + char

fun <N : Number> String.singularOrPlural(number: N) =
    this + "s".takeUnless { number.toLong() == 1L }.orEmpty()

fun String.surroundWith(charSequence: CharSequence) = "$charSequence$this$charSequence"
fun String.surroundWith(prefix: CharSequence, suffix: CharSequence) = "$prefix$this$suffix"

fun String.surroundWith(char: Char) = "$char$this$char"
fun String.surroundWith(prefix: Char, suffix: Char) = "$prefix$this$suffix"

operator fun String.times(num: Int) = buildString {
    repeat(num) { append(this@times) }
}