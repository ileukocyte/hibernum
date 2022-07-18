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
import io.ileukocyte.hibernum.commands.*
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.handlers.CommandHandler
import io.ileukocyte.hibernum.handlers.EventHandler
import io.ileukocyte.hibernum.utils.*

import kotlin.reflect.full.createInstance

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.interactions.commands.Command.Type
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions

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
            name = "loading\u2026"
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

    // updating global slash and context commands
    launch {
        val discordCommands = discord.retrieveCommands().await()

        launch {
            discordCommands.filter {
                val condition = CommandHandler[it.name] is ClassicTextOnlyCommand
                        || it.name !in CommandHandler.map { cmd -> cmd.name }
                        || it.defaultPermissions.permissionsRaw !=
                        CommandHandler[it.name]?.memberPermissions
                            ?.let(DefaultMemberPermissions::enabledFor)
                            ?.permissionsRaw

                it.type == Type.SLASH && condition
            }.takeUnless { it.isEmpty() }?.forEach {
                discord.deleteCommandById(it.id).queue { _ ->
                    LOGGER.info("${it.name} is no longer a slash command!")
                }
            }

            discordCommands.filter {
                val contextCommands = CommandHandler.filterIsInstance<ContextCommand>()

                it.type != Type.SLASH && (it.name !in contextCommands.map { cmd -> cmd.contextName }
                        || contextCommands.firstOrNull { cmd -> cmd.contextName == it.name }
                            ?.contextTypes
                            ?.contains(it.type) == false
                        || it.defaultPermissions.permissionsRaw !=
                            CommandHandler[it.name, it.type]
                                ?.memberPermissions
                                ?.let(DefaultMemberPermissions::enabledFor)
                                ?.permissionsRaw
                )
            }.takeUnless { it.isEmpty() }?.forEach {
                discord.deleteCommandById(it.id).queue { _ ->
                    LOGGER.info("${it.name} is no longer a ${it.type.name.lowercase()} context command!")
                }
            }
        }.join()

        launch {
            val slashPredicate = { cmd: TextCommand ->
                cmd.name !in discordCommands.map { it.name }
                        || cmd.description !in discordCommands.map { it.description.removePrefix("(Developer-only) ") }
                        || discordCommands.any {
                    val unequalSubcommands = if (cmd is SubcommandHolder) {
                        !cmd.subcommands.keys.toList().subcommandsEqual(it.subcommands.map { s -> s.toSubcommandData() })
                    } else {
                        false
                    }

                    cmd.name == it.name && (!cmd.options.toList().optionsEqual(it.options.map { o -> o.toOptionData() })
                            || unequalSubcommands)
                }
            }

            CommandHandler
                .filterIsInstanceAnd<TextCommand> { it !is ClassicTextOnlyCommand && slashPredicate(it) }
                .forEach {
                    discord.upsertCommand(it.asJDASlashCommandData() ?: return@forEach).queue({ cmd ->
                        LOGGER.info("UPDATE: Discord has updated the following slash command: ${cmd.name}!")
                    }, Throwable::printStackTrace)
                }

            CommandHandler
                .filterIsInstanceAnd<ContextCommand> { cmd ->
                    cmd.contextName !in discordCommands.map { it.name }
                            || cmd.contextTypes.size != discordCommands.count { it.name == cmd.contextName }
                }.forEach {
                    for (contextCommand in it.asJDAContextCommandDataInstances().filter { cmd ->
                        val map = discordCommands.associate { c -> c.name to c.type }

                        map[cmd.name] != cmd.type
                    }) {
                        discord.upsertCommand(contextCommand).queue({ cmd ->
                            LOGGER.info("UPDATE: Discord has updated the following ${cmd.type.name.lowercase()} context command: ${cmd.name}!")
                        }, Throwable::printStackTrace)
                    }
                }
        }.join()
    }.join()

    // adding the event listener
    discord.addEventListener(EventHandler)

    LOGGER.info("EventHandler has been successfully set as an event listener")

    // retrieving developers
    with(discord.retrieveApplicationInfo().await()) {
        DEVELOPERS += owner.idLong

        LOGGER.info("Retrieved $DEVELOPERS as the bot's developers")
    }

    // setting final activity
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