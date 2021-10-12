package io.ileukocyte.hibernum.commands

import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

data class SelfDeletion
    @OptIn(ExperimentalTime::class) constructor(val delay: Long, val unit: DurationUnit = DurationUnit.SECONDS)

open class CommandException(
    message: String? = null,
    val selfDeletion: SelfDeletion? = null,
    val footer: String? = null,
) : RuntimeException(message)

object NoArgumentsException : CommandException("You have not specified any arguments!")