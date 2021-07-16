package io.ileukocyte.hibernum.extensions

// kotlin.String
fun String.containsAll(vararg args: CharSequence): Boolean {
    for (element in args)
        if (element !in this)
            return false

    return true
}
fun String.containsAll(elements: Collection<CharSequence>) = containsAll(*elements.toTypedArray())
fun String.containsAll(vararg args: Char) = containsAll(args.map { it.toString() })

fun String.remove(input: String) = replace(input, "")
fun String.remove(regex: Regex) = replace(regex, "")

fun String.capitalizeAll(isForce: Boolean = true): String {
    val chars = (if (isForce) lowercase() else this).toCharArray()
    var found = false

    for (i in chars.indices) {
        if (!found && chars[i].isLetter()) {
            chars[i] = chars[i].uppercaseChar()
            found = true
        } else if (chars[i].isWhitespace() || chars[i] == '.' || chars[i] == '\'') {
            found = false
        }
    }

    return String(chars)
}