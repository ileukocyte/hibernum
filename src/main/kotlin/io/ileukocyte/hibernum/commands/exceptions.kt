package io.ileukocyte.hibernum.commands

import java.util.concurrent.TimeUnit

data class SelfDeletion(val delay: Long, val unit: TimeUnit = TimeUnit.SECONDS)

open class CommandException(
    message: String? = null,
    val selfDeletion: SelfDeletion? = null,
    val footer: String? = null,
) : RuntimeException(message)

object NoArgumentsException : CommandException("You have not specified any arguments!")