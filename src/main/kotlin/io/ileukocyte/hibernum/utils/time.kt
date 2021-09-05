@file:JvmName("TimeUtils")
package io.ileukocyte.hibernum.utils

import io.ileukocyte.hibernum.extensions.singularOrPlural

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

val TIME_CODE_REGEX =
    Regex("(?:(?<hours>\\d{1,2}):)?(?:(?<minutes>\\d{1,2}):)?(?<seconds>\\d{1,2})")

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

            append("$minutes ${"minute".singularOrPlural(minutes)}")

            alreadyPresent = true
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

/**
 * A function that formats time of the specified unit into a duration format
 *
 * **Example**:
 * ```
 * val millisExample = "ileukocyte/hibernum".hashCode() // 884095017
 * val text = asText(millisExample.toLong()) // "10:05:34:55"
 * ```
 *
 * @param time
 * The amount of time to be formatted
 * @param unit
 * A time unit for the aforementioned parameter
 * @param prependZeroDays
 * A boolean value that tells if "00" should be prepended to the output in case of days' count equaling to zero
 * (default: false)
 * @param prependZeroHours
 * A boolean value that tells if "00" should be prepended to the output in case of hours' count equaling to zero
 * (default: false OR true if [prependZeroDays] is enabled)
 *
 * @return A textual representation of the specified amount of time in a duration format
 *
 * @author Alexander Oksanich
 */
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

/**
 * A function that gets time from the specified [String] time code and returns it as milliseconds
 *
 * **Example**:
 * ```
 * val timeCode = "05:34:55"
 * val millis = timeCodeToMillis(timeCode) // 20095000
 *
 * val check = asText(millis) // "5 hours, 34 minutes, and 55 seconds"
 * ```
 *
 * @param timeCode
 * The amount of time as a [String] time code
 *
 * @return An amount of milliseconds that corresponds to the amount of time in the specified time code
 *
 * @author Alexander Oksanich
 */
@OptIn(ExperimentalTime::class)
fun timeCodeToMillis(timeCode: String) = TIME_CODE_REGEX.find(timeCode)?.let {
    data class Time(
        val seconds: ParsingTimeUnit,
        var minutes: ParsingTimeUnit,
        var hours: ParsingTimeUnit
    ) {
        init {
            hours.first?.also {
                if (minutes.first === null) {
                    minutes = hours.first to minutes.second
                    hours = null to hours.second
                }
            }
        }

        val millisSum = setOf(seconds, minutes, hours)
            .filter { (t, _) -> t !== null }
            .sumOf { (t, u) -> u.toMillis(t!!) }
    }

    val timeData = Time(
        it.groups["seconds"]?.value?.toLongOrNull() to DurationUnit.SECONDS,
        it.groups["minutes"]?.value?.toLongOrNull() to DurationUnit.MINUTES,
        it.groups["hours"]?.value?.toLongOrNull() to DurationUnit.HOURS
    )

    timeData.millisSum
} ?: throw IllegalArgumentException("You have entered an argument of a wrong format!")

@ExperimentalTime
private typealias ParsingTimeUnit = Pair<Long?, DurationUnit>