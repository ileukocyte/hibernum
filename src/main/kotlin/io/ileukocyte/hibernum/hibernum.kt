@file:JvmName("Hibernum")
package io.ileukocyte.hibernum

import io.ileukocyte.hibernum.Immutable.DEFAULT_PREFIX
import io.ileukocyte.hibernum.Immutable.DEVELOPERS
import io.ileukocyte.hibernum.Immutable.DISCORD_TOKEN
import io.ileukocyte.hibernum.Immutable.EVAL_KOTLIN_ENGINE
import io.ileukocyte.hibernum.Immutable.LOGGER
import io.ileukocyte.hibernum.Immutable.VERSION
import io.ileukocyte.hibernum.annotations.HibernumExperimental
import io.ileukocyte.hibernum.audio.loadGuildMusicManagers
import io.ileukocyte.hibernum.builders.buildActivity
import io.ileukocyte.hibernum.builders.buildJDA
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.handlers.EventHandler
import io.ileukocyte.hibernum.utils.isEqualTo
import io.ileukocyte.hibernum.utils.toOptionData

import kotlin.reflect.full.createInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity.ActivityType
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
    val jsr223Deferred = async { EVAL_KOTLIN_ENGINE.eval("Unit") }

    jsr223Deferred.await()

    LOGGER.info("Kotlin JSR-223 engine is ready to use!")

    // loading guild music managers
    discord.loadGuildMusicManagers()

    LOGGER.info("All the guilds have loaded their music managers!")

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
        val predicate = { cmd: Command ->
            cmd.name !in discordCommands.map { it.name }
                    || cmd.description !in discordCommands.map { it.description.removePrefix("(Developer-only) ") }
                    || discordCommands.any {
                        cmd.name == it.name && !cmd.options.toList().isEqualTo(it.options.map(Option::toOptionData))
                    }
        }

        CommandHandler.filter { it !is TextOnlyCommand }.filter(predicate).forEach {
            discord.upsertCommand(it.asSlashCommand!!).queue { cmd ->
                LOGGER.info("UPDATE: Discord has updated the following slash command: ${cmd.name}!")
            }
        }

        discordCommands.filter {
            CommandHandler[it.name] is TextOnlyCommand || it.name !in CommandHandler.map(Command::name)
        }.takeUnless { it.isEmpty() }?.forEach {
            discord.deleteCommandById(it.id).queue { _ ->
                LOGGER.info("${it.name} is no longer a slash command!")
            }
        }
    }

    // adding the event listener
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
            name = "${DEFAULT_PREFIX}help | $VERSION"
            url = "https://twitch.tv/discord"
            type = ActivityType.STREAMING
        }

        setStatus(OnlineStatus.ONLINE)

        LOGGER.info("The Discord presence has been updated!")
    }
}