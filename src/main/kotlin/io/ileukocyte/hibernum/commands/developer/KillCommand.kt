package io.ileukocyte.hibernum.commands.developer

import com.google.common.collect.Lists

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.WaiterProcess
import io.ileukocyte.hibernum.utils.getProcessById
import io.ileukocyte.hibernum.utils.kill
import io.ileukocyte.hibernum.utils.processes

import kotlin.math.max
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Button

class KillCommand : Command {
    override val name = "kill"
    override val description = "Sends a list of running processes or terminates the one provided by its ID"
    override val aliases = setOf("kill-process", "terminate")
    override val usages = setOf(setOf("process ID (optional)"))
    override val options = setOf(OptionData(OptionType.STRING, "pid", "The ID of the process to kill"))

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val processes = event.jda.processes.takeUnless { it.isEmpty() }
            ?: throw CommandException("No processes are currently running!")

        if (args === null) {
            event.channel.sendMessageEmbeds(processesListEmbed(processes, 0, event.jda))
                .setActionRow(
                    pageButtons(event.author.id, 0).takeIf { processes.size > 5 }
                        ?: setOf(Button.danger("$name-${event.author.idLong}-exit", "Close"))
                ).queue()
        } else {
            val process = event.jda.getProcessById(args)
                ?: throw CommandException("No process has been found by the provided ID!")

            event.channel.sendConfirmation("Are you sure you want to terminate the process?")
                .setActionRow(
                    Button.danger("$name-${event.author.idLong}-${process.id}-kill", "Yes"),
                    Button.secondary("$name-${event.author.idLong}-exit", "No"),
                ).queue()
        }
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val processes = event.jda.processes.takeUnless { it.isEmpty() }
            ?: throw CommandException("No processes are currently running!")
        val input = event.getOption("pid")?.asString

        if (input === null) {
            event.replyEmbeds(processesListEmbed(processes, 0, event.jda))
                .addActionRow(
                    pageButtons(event.user.id, 0).takeIf { processes.size > 5 }
                        ?: setOf(Button.danger("$name-${event.user.idLong}-exit", "Close"))
                ).queue()
        } else {
            val process = event.jda.getProcessById(input)
                ?: throw CommandException("No process has been found by the provided ID!")

            event.replyConfirmation("Are you sure you want to terminate the process?")
                .addActionRow(
                    Button.danger("$name-${event.user.idLong}-${process.id}-kill", "Yes"),
                    Button.secondary("$name-${event.user.idLong}-exit", "No"),
                ).queue()
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun invoke(event: ButtonClickEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            val type = id.last()
            val processes = event.jda.processes

            when (type) {
                "exit" -> event.message.delete().queue()
                "kill" -> {
                    val process = event.jda.getProcessById(id[1]) ?: return

                    process.kill(event.jda)

                    event.editMessageEmbeds(defaultEmbed("The process has been terminated!", EmbedType.SUCCESS))
                        .setActionRows()
                        .queue()

                    val description =
                        "The ${process.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} process " +
                                "running in this channel has been terminated via message deletion!"

                    event.jda.getTextChannelById(process.channel)?.let { channel ->
                        process.invoker?.let {
                            channel.retrieveMessageById(it).await().delete().queue({}) {}
                        }

                        channel.sendMessage {
                            embeds += defaultEmbed(description, EmbedType.WARNING) {
                                text = "This message will self-delete in 5 seconds"
                            }

                            process.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                                .takeUnless { it.isEmpty() }
                                ?.let { content += it }
                        }.queue({ it.delete().queueAfter(5, DurationUnit.SECONDS, {}) {} }, {})
                    }
                }
                else -> {
                    val page = id[1].toInt()

                    when (type) {
                        "first" -> {
                            event.editMessageEmbeds(processesListEmbed(processes, 0, event.jda))
                                .setActionRow(
                                    pageButtons(id.first(), 0).takeIf { processes.size > 5 }
                                        ?: setOf(Button.danger("$name-${id.first()}-exit", "Close"))
                                ).queue()
                        }
                        "last" -> {
                            val partition = Lists.partition(processes.toList(), 5)
                            val lastPage = partition.lastIndex

                            event.editMessageEmbeds(processesListEmbed(processes, lastPage, event.jda))
                                .setActionRow(
                                    pageButtons(id.first(), lastPage).takeIf { processes.size > 5 }
                                        ?: setOf(Button.danger("$name-${id.first()}-exit", "Close"))
                                ).queue()
                        }
                        "back" -> {
                            val newPage = max(0, page - 1)

                            event.editMessageEmbeds(processesListEmbed(processes, newPage, event.jda))
                                .setActionRow(
                                    pageButtons(id.first(), newPage).takeIf { processes.size > 5 }
                                        ?: setOf(Button.danger("$name-${id.first()}-exit", "Close"))
                                ).queue()
                        }
                        "next" -> {
                            val partition = Lists.partition(processes.toList(), 5)
                            val lastPage = partition.lastIndex
                            val newPage = min(page + 1, lastPage)

                            event.editMessageEmbeds(processesListEmbed(processes, newPage, event.jda))
                                .setActionRow(
                                    pageButtons(id.first(), newPage).takeIf { processes.size > 5 }
                                        ?: setOf(Button.danger("$name-${id.first()}-exit", "Close"))
                                ).queue()
                        }
                    }
                }
            }
        } else throw CommandException("You did not invoke the initial command!")
    }

    private fun processesListEmbed(
        originalSet: Set<WaiterProcess>,
        page: Int,
        jda: JDA,
    ) = buildEmbed {
        val partition = Lists.partition(originalSet.toList(), 5)
        val processes = partition[page]

        color = Immutable.SUCCESS

        for ((index, process) in processes.withIndex()) {
            val pid = process.id
            val command = process.command?.name ?: "Unknown"
            val users = process.users.joinToString { jda.getUserById(it)?.asTag?.let { t -> "$t ($it)" } ?: "$it" }
            val channel = process.channel.let { jda.getTextChannelById(it)?.name?.let { c -> "#$c ($it)" } ?: "$it" }
            val type = process.eventType?.simpleName ?: "Unknown"
            val timeCreated = process.timeCreated

            val value =
                "Process(pid=$pid, command=$command, users=[$users], channel=$channel, type=$type, timeCreated=$timeCreated)"

            appendLine("**${index + 1 + page * 5}.** $value")
        }

        author {
            name = "Process Manager"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        if (partition.size > 1) footer { text = "Total processes: ${originalSet.size}" }
    }

    private fun pageButtons(userId: String, page: Int) = setOf(
        Button.secondary("$name-$userId-$page-first", "First Page"),
        Button.secondary("$name-$userId-$page-back", "Back"),
        Button.secondary("$name-$userId-$page-next", "Next"),
        Button.secondary("$name-$userId-$page-last", "Last Page"),
        Button.danger("$name-$userId-exit", "Exit"),
    )
}