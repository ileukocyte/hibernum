@file:JvmName("JDAExtensions")
package io.ileukocyte.hibernum.extensions

import io.ileukocyte.hibernum.utils.millisToDate

import java.lang.management.ManagementFactory
import java.time.OffsetDateTime

import net.dv8tion.jda.api.JDA

private val runtimeMXBean = ManagementFactory.getRuntimeMXBean()

val JDA.uptime: Long
    get() = runtimeMXBean.uptime

val JDA.startTime: Long
    get() = runtimeMXBean.startTime

val JDA.startDate: OffsetDateTime
    get() = startTime.millisToDate()