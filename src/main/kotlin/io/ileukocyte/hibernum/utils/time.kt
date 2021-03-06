@file:JvmName("TimeUtils")
package io.ileukocyte.hibernum.utils

import io.ileukocyte.hibernum.extensions.removeLastChar
import io.ileukocyte.hibernum.extensions.singularOrPlural

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

val TIME_CODE_REGEX =
    Regex("(?:(?<hours>\\d{1,2}):)?(?:(?<minutes>\\d{1,2}):)?(?<seconds>\\d{1,2})")

val DURATION_REGEX = Regex("([1-9]\\d*)([smhdw])")

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
fun asText(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): String {
    val days = unit.toDays(time)
    val hours = unit.toHours(time) % 24L
    val minutes = unit.toMinutes(time) % 60L
    val seconds = unit.toSeconds(time) % 60L

    return buildString {
        var alreadyPresent = false

        if (days > 0L) {
            append("$days ${"day".singularOrPlural(days)}")

            alreadyPresent = true
        }

        if (hours > 0L) {
            if (alreadyPresent && (minutes != 0L || seconds != 0L)) {
                append(", ")
            }

            if (isNotEmpty() && (minutes == 0L && seconds == 0L)) {
                if (", " in this) {
                    append(",")
                }

                append(" and ")
            }

            append("$hours ${"hour".singularOrPlural(hours)}")

            alreadyPresent = true
        }

        if (minutes > 0L) {
            if (alreadyPresent && seconds != 0L) {
                append(", ")
            }

            if (isNotEmpty() && seconds == 0L) {
                if (", " in this) {
                    append(",")
                }

                append(" and ")
            }

            append("$minutes ${"minute".singularOrPlural(minutes)}")

            alreadyPresent = true
        }

        if (seconds > 0L) {
            if (alreadyPresent) {
                if (", " in this) {
                    append(",")
                }

                append(" and ")
            }

            append("$seconds ${"second".singularOrPlural(seconds)}")
        }

        if (isEmpty() && !alreadyPresent) {
            append("0 seconds")
        }
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
fun asDuration(
    time: Long,
    unit: TimeUnit = TimeUnit.MILLISECONDS,
    prependZeroDays: Boolean = false,
    prependZeroHours: Boolean = false,
): String {
    val seconds = unit.toSeconds(time) % 60L
    val minutes = unit.toMinutes(time) % 60L
    val hours = unit.toHours(time) % 24L
    val days = unit.toDays(time) % 30L

    return buildString {
        if (prependZeroDays || days > 0) {
            append("%02d:".format(days))
        }

        if (prependZeroHours || days > 0 || prependZeroDays || hours > 0) {
            append("%02d:".format(hours))
        }

        append("%02d:".format(minutes))
        append("%02d".format(seconds))
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
 * @throws [IllegalArgumentException]
 *
 * @author Alexander Oksanich
 */
fun timeCodeToMillis(timeCode: String) = TIME_CODE_REGEX.find(timeCode)?.let {
    data class Time(
        val seconds: ParsingTimeUnit,
        var minutes: ParsingTimeUnit,
        var hours: ParsingTimeUnit,
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
        it.groups["seconds"]?.value?.toLongOrNull() to TimeUnit.SECONDS,
        it.groups["minutes"]?.value?.toLongOrNull() to TimeUnit.MINUTES,
        it.groups["hours"]?.value?.toLongOrNull() to TimeUnit.HOURS,
    )

    timeData.millisSum
} ?: throw IllegalArgumentException("You have entered an argument of a wrong format!")

/**
 * A function that gets time from the specified duration [String] and returns it as milliseconds
 *
 * **Example**:
 * ```
 * val duration = "5h34m55s"
 * val millis = durationToMillis(duration) // 20095000
 *
 * val check = asText(millis) // "5 hours, 34 minutes, and 55 seconds"
 * ```
 *
 * @param input
 * The amount of time as a duration [String]
 *
 * @return An amount of milliseconds that corresponds to the amount of time in the provided duration input
 *
 * @author Alexander Oksanich
 */
fun durationToMillis(input: String): Long {
    var time = 0L

    for (result in DURATION_REGEX.findAll(input)) {
        val number = result.value.removeLastChar().toLong()

        time += when (result.value.last()) {
            's' -> TimeUnit.SECONDS.toMillis(number)
            'm' -> TimeUnit.MINUTES.toMillis(number)
            'h' -> TimeUnit.HOURS.toMillis(number)
            'd' -> TimeUnit.DAYS.toMillis(number)
            else -> TimeUnit.DAYS.toMillis(number * 7)
        }
    }

    return time
}

private typealias ParsingTimeUnit = Pair<Long?, TimeUnit>