@file:JvmName("Hibernum")
package io.ileukocyte.hibernum

import io.ileukocyte.hibernum.Immutable.DEVELOPERS
import io.ileukocyte.hibernum.Immutable.DISCORD_TOKEN
import io.ileukocyte.hibernum.Immutable.EVAL_KOTLIN_ENGINE
import io.ileukocyte.hibernum.Immutable.LOGGER
import io.ileukocyte.hibernum.Immutable.VERSION
import io.ileukocyte.hibernum.annotations.HibernumExperimental
import io.ileukocyte.hibernum.builders.buildActivity
import io.ileukocyte.hibernum.builders.buildJDA
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.Command.CommandType
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.handlers.EventHandler

import kotlin.reflect.full.createInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity.ActivityType

import org.reflections.Reflections

private inline fun <reified T> Reflections.getSubtypesOf() =
    getSubTypesOf(T::class.java)

@HibernumExperimental
fun main() = runBlocking {
    // initializing Discord
    val discord = buildJDA {
        token = DISCORD_TOKEN
        onlineStatus = OnlineStatus.DO_NOT_DISTURB
        activity {
            name = "loading..."
            type = ActivityType.WATCHING
        }
    }

    LOGGER.info("JDA has been successfully initialized!")

    // first evaluation
    val jsr223Init = launch {
        val deferred = async { EVAL_KOTLIN_ENGINE.eval("Unit") as? Unit }
        deferred.await()

        LOGGER.info("Kotlin JSR-223 engine is ready to use!")
    }

    jsr223Init.join()

    // registering commands
    val handlerInit = launch {
        Reflections("io.ileukocyte.hibernum.commands")
            .getSubtypesOf<Command>()
            .map { it.kotlin }
            .forEach { CommandHandler += it.createInstance() }
    }

    handlerInit.join()

    if (CommandHandler.isNotEmpty())
        LOGGER.info("CommandHandler has successfully loaded ${CommandHandler.size} commands!")

    // updating global slash commands
    if (CommandHandler.any { it.name !in discord.retrieveCommands().await().map { c -> c.name } })
        discord.updateCommands().addCommands(CommandHandler.asSlashCommands).queue()

    discord.retrieveCommands().queue { cmds ->
        val nonSlashRegisteredAsSlash = cmds.filter { CommandHandler[it.name]?.type == CommandType.TEXT_ONLY }
        nonSlashRegisteredAsSlash.takeUnless { it.isEmpty() }?.forEach { discord.deleteCommandById(it.id).queue() }
    }

    // adding an event listener
    discord.addEventListener(EventHandler)

    LOGGER.info("EventHandler has been successfully set as an event listener")

    // retrieving developers
    with(discord.retrieveApplicationInfo().await()) {
        DEVELOPERS += owner.idLong

        LOGGER.info("Retrieved $DEVELOPERS as the bot's developers")
    }

    // setting activity
    with(discord.presence) {
        activity = buildActivity {
            name = VERSION.toString()
            url = "https://twitch.tv/discordapp"
            type = ActivityType.STREAMING
        }

        setStatus(OnlineStatus.ONLINE)

        LOGGER.info("The Discord presence has been updated!")
    }
}