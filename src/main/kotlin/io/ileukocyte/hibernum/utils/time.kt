package io.ileukocyte.hibernum.utils

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

import kotlin.time.*
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

fun Long.millisToDate(zone: ZoneId = ZoneId.of("Etc/GMT0")) =
    Instant.ofEpochMilli(this).atZone(zone).toOffsetDateTime()

@OptIn(ExperimentalTime::class)
fun asText(time: Long, unit: DurationUnit = DurationUnit.MILLISECONDS): String {
    fun String.singularOrPlural(long: Long) = this + "s".takeIf { long > 1L }.orEmpty()

    val duration = time.toDuration(unit)

    val days = duration.inWholeDays
    val hours = duration.inWholeHours - days.toDuration(DurationUnit.DAYS).inWholeHours
    val minutes = duration.inWholeMinutes - duration.inWholeHours.toDuration(DurationUnit.HOURS).inWholeMinutes
    val seconds = duration.inWholeSeconds - duration.inWholeMinutes.toDuration(DurationUnit.MINUTES).inWholeSeconds

    return buildString {
        var alreadyPresent = false

        if (days > 0L) {
            append("$days ${"day".singularOrPlural(days)}")
            alreadyPresent = true
        }

        if (hours > 0L) {
            if (alreadyPresent && (minutes != 0L || seconds != 0L))
                append(", ")

            if (toString().isNotEmpty() && (minutes == 0L && seconds == 0L))
                append(" and ")

            append("$hours ${"hour".singularOrPlural(hours)}")
            alreadyPresent = true
        }

        if (minutes > 0L) {
            if (alreadyPresent && seconds != 0L)
                append(", ")

            if (toString().isNotEmpty() && seconds == 0L)
                append(" and ")

            alreadyPresent = true
            append("$minutes ${"minute".singularOrPlural(minutes)}")
        }

        if (seconds > 0L) {
            if (alreadyPresent)
                append(" and ")

            append("$seconds ${"second".singularOrPlural(seconds)}")
        }

        if (toString().isEmpty() && !alreadyPresent) {
            append("0 seconds")
        }
    }
}

@Deprecated(message = "Deprecated in favor of more Kotlin-natural function", replaceWith = ReplaceWith("asText(Long, DurationUnit)"))
fun asTextJava(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): String {
    fun String.singularOrPlural(long: Long) = this + "s".takeIf { long > 1L  }.orEmpty()

    val millis = unit.toMillis(time)
    val days = TimeUnit.MILLISECONDS.toDays(millis)
    val hours = TimeUnit.MILLISECONDS.toHours(millis) - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(millis))
    val minutes =
        TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis))
    val seconds =
        TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))

    return buildString {
        var alreadyPresent = false

        if (days > 0L) {
            append("$days ${"day".singularOrPlural(days)}")
            alreadyPresent = true
        }

        if (hours > 0L) {
            if (alreadyPresent && (minutes != 0L || seconds != 0L))
                append(", ")

            if (toString().isNotEmpty() && (minutes == 0L && seconds == 0L))
                append(" and ")

            append("$hours ${"hour".singularOrPlural(hours)}")
            alreadyPresent = true
        }

        if (minutes > 0L) {
            if (alreadyPresent && seconds != 0L)
                append(", ")

            if (toString().isNotEmpty() && seconds == 0L)
                append(" and ")

            alreadyPresent = true
            append("$minutes ${"minute".singularOrPlural(minutes)}")
        }

        if (seconds > 0L) {
            if (alreadyPresent)
                append(" and ")

            append("$seconds ${"second".singularOrPlural(seconds)}")
        }

        if (toString().isEmpty() && !alreadyPresent) {
            append("0 seconds")
        }
    }
}