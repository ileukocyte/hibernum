@file:JvmName("StandardLibraryExtensions")
package io.ileukocyte.hibernum.extensions

// kotlin.String
fun String.containsAll(vararg args: CharSequence): Boolean {
    for (element in args)
        if (element !in this)
            return false

    return true
}
fun String.containsAll(elements: Collection<CharSequence>) = containsAll(*elements.toTypedArray())
fun String.containsAll(vararg args: Char) = toCharArray().toList().containsAll(args.toList())

fun String.remove(input: String) = replace(input, "")
fun String.remove(regex: Regex) = replace(regex, "")

fun String.replaceLastChar(charSequence: CharSequence) = substring(0, length - 1) + charSequence
fun String.replaceLastChar(char: Char) = replaceLastChar(char.toString())

fun String.capitalizeAll() =
    lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

fun <N : Number> String.singularOrPlural(number: N) = this + "s".takeUnless { number.toLong() == 1L }.orEmpty()