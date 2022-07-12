@file:JvmName("Hibernum")
package io.ileukocyte.hibernum

import io.ileukocyte.hibernum.Immutable.DEFAULT_PREFIX
import io.ileukocyte.hibernum.Immutable.DEVELOPERS
import io.ileukocyte.hibernum.Immutable.DISCORD_TOKEN
import io.ileukocyte.hibernum.Immutable.EVAL_KOTLIN_ENGINE
import io.ileukocyte.hibernum.Immutable.LOGGER
import io.ileukocyte.hibernum.Immutable.VERSION
import io.ileukocyte.hibernum.audio.loadGuildMusicManagers
import io.ileukocyte.hibernum.builders.buildActivity
import io.ileukocyte.hibernum.builders.buildJDA
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.ContextCommand
import io.ileukocyte.hibernum.commands.GenericCommand
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.handlers.EventHandler
import io.ileukocyte.hibernum.utils.isEqualTo
import io.ileukocyte.hibernum.utils.toOptionData

import kotlin.reflect.full.createInstance

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.interactions.commands.Command.Type
import net.dv8tion.jda.api.interactions.commands.Command.Option

import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd

import org.reflections.Reflections

private inline fun <reified T> Reflections.getSubtypesOf() =
    getSubTypesOf(T::class.java)

suspend fun main() = coroutineScope {
    // initializing Discord
    val discord = buildJDA {
        token = DISCORD_TOKEN
        includePrivilegedIntents = true
        onlineStatus = OnlineStatus.DO_NOT_DISTURB

        activity {
            name = "loading..."
            type = ActivityType.WATCHING
        }
    }

    LOGGER.info("JDA has been successfully initialized!")

    // first evaluation
    launch {
        EVAL_KOTLIN_ENGINE
            .eval("io.ileukocyte.hibernum.Immutable.LOGGER.info(\"Kotlin JSR-223 engine is ready to use!\")")
    }.join()

    // loading guild music managers
    launch {
        discord.loadGuildMusicManagers()
    }.join()

    LOGGER.info("All the guilds have loaded their music managers!")

    // registering commands
    launch {
        Reflections("io.ileukocyte.hibernum.commands")
            .getSubtypesOf<GenericCommand>()
            .filter { !it.isInterface }
            .map { it.kotlin }
            .forEach { CommandHandler += it.createInstance() }
    }.join()

    if (CommandHandler.isNotEmpty()) {
        LOGGER.info("CommandHandler has successfully loaded ${CommandHandler.size} commands!")
    }

    // updating global slash commands
    launch {
        discord.retrieveCommands().queue { discordCommands ->
            val slashPredicate = { cmd: Command ->
                cmd.name !in discordCommands.map { it.name }
                        || cmd.description !in discordCommands.map { it.description.removePrefix("(Developer-only) ") }
                        || discordCommands.any {
                    cmd.name == it.name && !cmd.options.toList().isEqualTo(it.options.map(Option::toOptionData))
                }
            }

            CommandHandler
                .filterIsInstanceAnd<Command> { it !is TextOnlyCommand && slashPredicate(it) }
                .forEach {
                    discord.upsertCommand(it.asDiscordCommand!!).queue { cmd ->
                        LOGGER.info("UPDATE: Discord has updated the following slash command: ${cmd.name}!")
                    }
                }

            CommandHandler
                .filterIsInstance<ContextCommand>()
                .forEach {
                    discord.upsertCommand(it.asDiscordCommand).queue { cmd ->
                        //LOGGER.info
                        println("UPDATE: Discord has updated the following ${cmd.type.name.lowercase()} context command: ${cmd.name}!")
                    }
                }

            discordCommands.filter {
                CommandHandler[it.name] is TextOnlyCommand || it.name !in CommandHandler.map(GenericCommand::name)
            }.takeUnless { it.isEmpty() }?.forEach {
                discord.deleteCommandById(it.id).queue { _ ->
                    LOGGER.info("${it.name} is no longer a slash command!")
                }
            }

            discordCommands.filter {
                it.type in setOf(Type.MESSAGE, Type.USER)
                        && it.name !in CommandHandler.filterIsInstance<ContextCommand>().map(ContextCommand::contextName)
            }.takeUnless { it.isEmpty() }?.forEach {
                discord.deleteCommandById(it.id).queue { _ ->
                    //LOGGER.info
                    println("${it.name} is no longer a context command!")
                }
            }
        }
    }.join()

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