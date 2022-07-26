package io.ileukocyte.hibernum.extensions

import net.dv8tion.jda.api.utils.MarkdownSanitizer
import net.dv8tion.jda.api.utils.MarkdownUtil

fun String.escapeMarkdown(ignored: Int = MarkdownSanitizer.NORMAL) =
    MarkdownSanitizer.escape(this, ignored)
fun String.sanitizeMarkdown() = MarkdownSanitizer.sanitize(this)

fun String.bold() = MarkdownUtil.bold(this)
fun String.codeblock(language: String? = null) = MarkdownUtil.codeblock(language, this)
fun String.italics() = MarkdownUtil.italics(this)
fun String.monospace() = MarkdownUtil.monospace(this)
fun Pair<String, String>.maskedLink() = MarkdownUtil.maskedLink(first, second)
fun String.quote() = MarkdownUtil.quote(this)
fun String.quoteBlock() = MarkdownUtil.quoteBlock(this)
fun String.spoiler() = MarkdownUtil.spoiler(this)
fun String.strike() = MarkdownUtil.strike(this)
fun String.underline() = MarkdownUtil.underline(this)