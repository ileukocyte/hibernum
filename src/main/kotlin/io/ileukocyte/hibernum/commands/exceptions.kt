package io.ileukocyte.hibernum.commands

open class CommandException(message: String? = null) : RuntimeException(message)

object NoArgumentsException : CommandException("You have not specified any arguments!")