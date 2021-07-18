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
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.handlers.EventHandler

import kotlin.reflect.full.createInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.Command.Option

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
            .filter { !it.isInterface }
            .map { it.kotlin }
            .forEach { CommandHandler += it.createInstance() }
    }

    handlerInit.join()

    if (CommandHandler.isNotEmpty())
        LOGGER.info("CommandHandler has successfully loaded ${CommandHandler.size} commands!")

    // updating global slash commands
    discord.retrieveCommands().queue { discordCommands ->
        fun Option.toOptionData() =
            OptionData(type, name, description, isRequired).addChoices(choices)

        val predicate = { cmd: Command ->
            cmd.name !in discordCommands.map { it.name }
                    || cmd.description !in discordCommands.map { it.description.removePrefix("(Developer-only) ") }
                    || discordCommands.any { cmd.name == it.name && cmd.options != it.options.map(Option::toOptionData).toSet() }
        }

        if (CommandHandler.filter { it !is TextOnlyCommand }.any(predicate))
            discord.updateCommands().addCommands(CommandHandler.asSlashCommands).queue { commands ->
                LOGGER.info("UPDATE: Discord has loaded the following slash commands: ${commands.map { it.name }}")
            }

        discordCommands.filter { CommandHandler[it.name] is TextOnlyCommand }.takeUnless { it.isEmpty() }?.forEach {
            discord.deleteCommandById(it.id).queue { _ ->
                LOGGER.info("${it.name} is no longer a slash command!")
            }
        }
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
            url = "https://twitch.tv/discord"
            type = ActivityType.STREAMING
        }

        setStatus(OnlineStatus.ONLINE)

        LOGGER.info("The Discord presence has been updated!")
    }
}