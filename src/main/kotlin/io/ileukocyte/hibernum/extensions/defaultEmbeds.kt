@file:JvmName("DefaultEmbeds")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed

import java.awt.Color

enum class EmbedType(val color: Color) {
    SUCCESS(Immutable.SUCCESS),
    FAILURE(Immutable.FAILURE),
    CONFIRMATION(Immutable.CONFIRMATION),
    WARNING(Immutable.WARNING);

    val title = "${name.capitalizeAll()}!"
}

fun defaultEmbed(desc: String, type: EmbedType) = buildEmbed {
    color = type.color
    description = desc
    author { name = type.title }
}