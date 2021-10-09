package io.ileukocyte.hibernum.builders

@Suppress("UNUSED")
class BuilderNotInitializedException : Exception {
    constructor() : super()
    constructor(
        message: String,
        defaultTemplate: String? = "The required properties [$message] have not been initialized!"
    ) : super(defaultTemplate ?: message)
    constructor(throwable: Throwable?) : super(throwable)
    constructor(message: String, throwable: Throwable?) : super(message, throwable)
}