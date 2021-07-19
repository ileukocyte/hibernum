@file:JvmName("TimeUtils")
package io.ileukocyte.hibernum.utils

import io.ileukocyte.hibernum.extensions.singularOrPlural

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

fun Long.millisToDate(zone: ZoneId = ZoneId.of("Etc/GMT0")): OffsetDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toOffsetDateTime()

/**
 * A function that formats time of the specified unit as a text
 *
 * **Example**:
 * ```
 * val millisExample = "ileukocyte/hibernum".hashCode() // 884095017
 * val text = asText(millisExample.toLong()) // "10 days, 5 hours, 34 minutes, and 55 seconds"
 * ```
 *
 * @param time
 * The amount of time to be formatted
 * @param unit
 * A time unit for the aforementioned parameter
 *
 * @return A textual representation of the specified amount of time
 *
 * @author Alexander Oksanich
 */
@OptIn(ExperimentalTime::class)
fun asText(time: Long, unit: DurationUnit = DurationUnit.MILLISECONDS): String {
    val duration = time.toDuration(unit)

    val days = duration.inWholeDays
    val hours = duration.inWholeHours % 24L
    val minutes = duration.inWholeMinutes % 60L
    val seconds = duration.inWholeSeconds % 60L

    return buildString {
        var alreadyPresent = false

        if (days > 0L) {
            append("$days ${"day".singularOrPlural(days)}")
            alreadyPresent = true
        }

        if (hours > 0L) {
            if (alreadyPresent && (minutes != 0L || seconds != 0L))
                append(", ")

            if (isNotEmpty() && (minutes == 0L && seconds == 0L)) {
                if (", " in this)
                    append(",")

                append(" and ")
            }

            append("$hours ${"hour".singularOrPlural(hours)}")
            alreadyPresent = true
        }

        if (minutes > 0L) {
            if (alreadyPresent && seconds != 0L)
                append(", ")

            if (isNotEmpty() && seconds == 0L) {
                if (", " in this)
                    append(",")

                append(" and ")
            }

            alreadyPresent = true
            append("$minutes ${"minute".singularOrPlural(minutes)}")
        }

        if (seconds > 0L) {
            if (alreadyPresent) {
                if (", " in this)
                    append(",")

                append(" and ")
            }

            append("$seconds ${"second".singularOrPlural(seconds)}")
        }

        if (isEmpty() && !alreadyPresent)
            append("0 seconds")
    }
}

@OptIn(ExperimentalTime::class)
fun asDuration(
    time: Long,
    unit: DurationUnit = DurationUnit.MILLISECONDS,
    prependZeroDays: Boolean = false,
    prependZeroHours: Boolean = false
): String {
    val duration = time.toDuration(unit)

    val seconds = duration.inWholeSeconds % 60L
    val minutes = duration.inWholeMinutes % 60L
    val hours = duration.inWholeHours % 24L
    val days = duration.inWholeDays % 30L

    return buildString {
        if (prependZeroDays || days > 0)
            append("0$days:".takeIf { days < 10 } ?: "$days:")

        if (prependZeroHours || days > 0 || prependZeroDays || hours > 0)
            append("0$hours:".takeIf { hours < 10 } ?: "$hours:")

        append("0$minutes:".takeIf { minutes < 10 } ?: "$minutes:")
        append("0$seconds".takeIf { seconds < 10 } ?: "$seconds")
    }
}