package io.ileukocyte.hibernum.builders

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.hooks.IEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag

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
    var includePrivilegedIntents = false

    fun activity(block: KActivityBuilder.() -> Unit) {
        _activity = KActivityBuilder().apply(block)()
    }

    @PublishedApi
    internal operator fun invoke() = if (::token.isInitialized) {
        JDABuilder
            .createDefault(token)
            .let {
                it.takeIf { includePrivilegedIntents }
                    ?.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                    ?.setMemberCachePolicy(MemberCachePolicy.ALL)
                    ?.enableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS)
                    ?: it
            }.disableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.DIRECT_MESSAGE_REACTIONS)
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