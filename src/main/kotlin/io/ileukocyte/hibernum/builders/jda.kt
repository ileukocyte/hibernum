package io.ileukocyte.hibernum.builders

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.hooks.IEventManager

@DslMarker
internal annotation class JDADslMarker

@JDADslMarker
class KJDABuilder {
    @JDADslMarker
    class KActivityBuilder {
        lateinit var type: Activity.ActivityType
        lateinit var name: String
        var url: String? = null

        @PublishedApi
        internal operator fun invoke() = if (::type.isInitialized) {
            Activity.of(type, name, url)
        } else throw BuilderNotInitializedException("`type`")
    }

    lateinit var token: String
    private var _activity: Activity? = null
    var eventManager: IEventManager? = null
    val eventListeners = mutableSetOf<Any>()
    var bulkDeleteSplitting = false
    var onlineStatus = OnlineStatus.ONLINE

    fun activity(block: KActivityBuilder.() -> Unit) {
        _activity = KActivityBuilder().apply(block)()
    }

    @PublishedApi
    internal operator fun invoke() = if (::token.isInitialized) {
        JDABuilder
            .createDefault(token)
            .setEventManager(eventManager)
            .addEventListeners(*eventListeners.toTypedArray())
            .setActivity(_activity)
            .setBulkDeleteSplittingEnabled(bulkDeleteSplitting)
            .setStatus(onlineStatus)
            .build()
    } else throw BuilderNotInitializedException("`token`")
}

inline fun buildJDA(block: KJDABuilder.() -> Unit) = KJDABuilder().apply(block)()

inline fun buildActivity(block: KJDABuilder.KActivityBuilder.() -> Unit) =
    KJDABuilder.KActivityBuilder().apply(block)()