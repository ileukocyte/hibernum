package io.ileukocyte.hibernum.builders

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed

import java.awt.Color
import java.time.temporal.TemporalAccessor

@DslMarker
internal annotation class EmbedDslMarker

@Suppress("PropertyName")
class KEmbedBuilder {
    var description: String? = null
    var color: Color? = null
    var thumbnail: String? = null
    var image: String? = null
    var timestamp: TemporalAccessor? = null
    val fields = mutableListOf<Field>()

    @PublishedApi
    internal var _title: Title? = null

    @PublishedApi
    internal var _author: Author? = null

    @PublishedApi
    internal var _footer: Footer? = null

    inline fun title(block: Title.() -> Unit) {
        _title = Title().apply(block)
    }

    inline fun author(block: Author.() -> Unit) {
        _author = Author().apply(block)
    }

    inline fun footer(block: Footer.() -> Unit) {
        _footer = Footer().apply(block)
    }

    inline fun field(block: Field.() -> Unit) {
        fields += Field().apply(block)
    }

    fun append(content: String) {
        if (description !== null) {
            description += content
        } else {
            description = content
        }
    }

    fun appendln(content: String = "") {
        if (description !== null) {
            description += "$content\n"
        } else {
            description = "$content\n"
        }
    }

    inline fun append(lazyContent: () -> Any?) = append(lazyContent()?.toString() ?: "")

    inline fun appendln(lazyContent: () -> Any?) = appendln(lazyContent()?.toString() ?: "")

    @PublishedApi
    internal operator fun invoke(): MessageEmbed {
        val javaBuilder = EmbedBuilder()
            .setDescription(description)
            .setColor(color)
            .setThumbnail(thumbnail)
            .setImage(image)
            .setTimestamp(timestamp)
            .setTitle(_title?.title, _title?.url)
            .setAuthor(_author?.name, _author?.url, _author?.iconUrl)
            .setFooter(_footer?.text, _footer?.iconUrl)

        for (field in fields) {
            if (field.title === null || field.description === null) {
                javaBuilder.addBlankField(field.isInline)
            } else {
                javaBuilder.addField(field.title, field.description, field.isInline)
            }
        }

        return javaBuilder.build()
    }

    @EmbedDslMarker
    class Title {
        var title: String? = null
        var url: String? = null
    }

    @EmbedDslMarker
    class Author {
        var name: String? = null
        var url: String? = null
        var iconUrl: String? = null
    }

    @EmbedDslMarker
    class Footer {
        var text: String? = null
        var iconUrl: String? = null
    }

    @EmbedDslMarker
    class Field {
        var title: String? = null
        var description: String? = null
        var isInline = false
    }
}

inline fun buildEmbed(block: KEmbedBuilder.() -> Unit) = KEmbedBuilder().apply(block)()