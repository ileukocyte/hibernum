package io.ileukocyte.hibernum.commands.developer

import com.google.common.collect.Lists

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleInteractionHandling
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.`fun`.AkinatorCommand
import io.ileukocyte.hibernum.commands.`fun`.ChomskyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.WaiterProcess
import io.ileukocyte.hibernum.utils.getProcessById
import io.ileukocyte.hibernum.utils.kill
import io.ileukocyte.hibernum.utils.processes

import java.util.concurrent.TimeUnit

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.GenericAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.utils.TimeFormat

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class ProcessesCommand : SlashOnlyCommand {
    override val name = "processes"
    override val description = "Sends a list of all the running processes or terminates the one provided by its ID"
    override val options = setOf(
        OptionData(OptionType.STRING, "pid", "The ID of the process to terminate")
            .setAutoComplete(true))
    override val staleInteractionHandling = StaleInteractionHandling.REMOVE_COMPONENTS

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val processes = event.jda.processes.takeUnless { it.isEmpty() }
            ?: throw CommandException("No processes are currently running!")
        val input = event.getOption("pid")?.asString

        if (input === null) {
            val pages = ceil(processes.size / 5.0).toInt()

            val actionRows = mutableSetOf<ActionRow>()

            actionRows += ActionRow.of(
                pageButtons(event.user.id, 0, pages).takeIf { processes.size > 5 }
                    ?: setOf(
                        Button.primary("$interactionName-${event.user.idLong}-kill", "Terminate"),
                        Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"),
                    )
            )

            if (processes.size > 5) {
                actionRows +=
                    ActionRow.of(Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"))
            }

            event.replyEmbeds(processesListEmbed(processes, 0, event.jda, event.guild ?: return))
                .setComponents(actionRows)
                .queue()
        } else {
            val process = event.jda.getProcessById(input)
                ?: throw CommandException("No process has been found by the provided ID!")

            event.replyConfirmation("Are you sure you want to terminate the process?")
                .addActionRow(
                    Button.danger("$interactionName-${event.user.idLong}-${process.id}-killc", "Yes"),
                    Button.secondary("$interactionName-${event.user.idLong}-exit", "No"),
                ).queue()
        }
    }

    override suspend fun invoke(event: GenericAutoCompleteInteractionEvent) {
        val interaction = event.interaction as CommandAutoCompleteInteraction

        interaction.getOption("pid") { option ->
            val query = option.asString

            if (query.isNotEmpty()) {
                event.jda.processes
                    .filter { it.id.startsWith(query) }
                    .let { wp ->
                        event.replyChoiceStrings(wp.map { it.id }).queue()
                    }
            } else {
                event.replyChoiceStrings().queue()
            }
        }
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val guild = event.guild ?: return
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id == id.first()) {
            val type = id.last()
            val processes = event.jda.processes

            when (type) {
                "exit" -> event.message.delete().queue(null) {}
                "kill" -> {
                    val input = TextInput
                        .create("$interactionName-pid", "Enter the Process ID:", TextInputStyle.SHORT)
                        .setMaxLength(4)
                        .build()
                    val modal = Modal
                        .create("$interactionName-modal", "Process Termination")
                        .addActionRow(input)
                        .build()

                    event.replyModal(modal).queue()
                }
                "killc" -> {
                    val process = event.jda.getProcessById(id[1]) ?: return

                    process.kill(event.jda)

                    if (process.command is AkinatorCommand) {
                        for (userId in process.users) {
                            AkinatorCommand.AKIWRAPPERS -= userId
                            AkinatorCommand.DECLINED_GUESSES -= userId
                            AkinatorCommand.GUESS_TYPES -= userId
                        }
                    }

                    if (process.command is ChomskyCommand) {
                        for (userId in process.users) {
                            ChomskyCommand.CHATTER_BOT_SESSIONS -= userId
                        }
                    }

                    event.editMessageEmbeds(defaultEmbed("The process has been terminated!", EmbedType.SUCCESS))
                        .setComponents(emptyList())
                        .queue(null) {
                            event.channel.sendSuccess("The process has been terminated!").queue()
                        }

                    val description =
                        "The ${process.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} process " +
                                "running in this channel has been terminated!"

                    event.jda.getChannelById(GuildMessageChannel::class.java, process.channel)?.let { channel ->
                        process.invoker?.let {
                            channel.retrieveMessageById(it).await().delete().queue(null) {}
                        }

                        channel.sendMessage {
                            embeds += defaultEmbed(description, EmbedType.WARNING) {
                                text = "This message will self-delete in 5 seconds"
                            }

                            process.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                                .takeUnless { it.isEmpty() }
                                ?.let { content += it }
                        }.queue({ it.delete().queueAfter(5, TimeUnit.SECONDS, null) {} }) {}
                    }
                }
                else -> {
                    val page = id[1].toInt()
                    val pages = ceil(processes.size / 5.0).toInt()

                    fun getUpdatedButtons(initialPage: Int): Set<ActionRow> {
                        val actionRows = mutableSetOf<ActionRow>()

                        actionRows += ActionRow.of(
                            pageButtons(event.user.id, initialPage, pages).takeIf { processes.size > 5 }
                                ?: setOf(
                                    Button.primary("$interactionName-${event.user.idLong}-kill", "Terminate"),
                                    Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"),
                                )
                        )

                        if (processes.size > 5) {
                            actionRows +=
                                ActionRow.of(Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"))
                        }

                        return actionRows
                    }

                    when (type) {
                        "first" -> {
                            event.editMessageEmbeds(processesListEmbed(processes, 0, event.jda, guild))
                                .setComponents(getUpdatedButtons(0))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(processesListEmbed(processes, 0, event.jda, guild))
                                        .setComponents(getUpdatedButtons(0))
                                        .queue()
                                }
                        }
                        "last" -> {
                            val lastPage = pages.dec()

                            event.editMessageEmbeds(processesListEmbed(processes, lastPage, event.jda, guild))
                                .setComponents(getUpdatedButtons(lastPage))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(processesListEmbed(processes, lastPage, event.jda, guild))
                                        .setComponents(getUpdatedButtons(lastPage))
                                        .queue()
                                }
                        }
                        "back" -> {
                            val newPage = min(max(0, page.dec()), pages.dec())

                            event.editMessageEmbeds(processesListEmbed(processes, newPage, event.jda, guild))
                                .setComponents(getUpdatedButtons(newPage))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(processesListEmbed(processes, newPage, event.jda, guild))
                                        .setComponents(getUpdatedButtons(newPage))
                                        .queue()
                                }
                        }
                        "next" -> {
                            val newPage = min(page.inc(), pages.dec())

                            event.editMessageEmbeds(processesListEmbed(processes, newPage, event.jda, guild))
                                .setComponents(getUpdatedButtons(newPage))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(processesListEmbed(processes, newPage, event.jda, guild))
                                        .setComponents(getUpdatedButtons(newPage))
                                        .queue()
                                }
                        }
                    }
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    override suspend fun invoke(event: ModalInteractionEvent) {
        val pid = event.getValue("$interactionName-pid")?.asString ?: return
        val process = event.jda.getProcessById(pid)
            ?: throw CommandException("No process has been found by the provided ID!")

        process.kill(event.jda)

        if (process.command is AkinatorCommand) {
            for (userId in process.users) {
                AkinatorCommand.AKIWRAPPERS -= userId
                AkinatorCommand.DECLINED_GUESSES -= userId
                AkinatorCommand.GUESS_TYPES -= userId
            }
        }

        if (process.command is ChomskyCommand) {
            for (userId in process.users) {
                ChomskyCommand.CHATTER_BOT_SESSIONS -= userId
            }
        }

        event.editMessageEmbeds(defaultEmbed("The process has been terminated!", EmbedType.SUCCESS))
            .setComponents(emptyList())
            .queue(null) {
                event.messageChannel.sendSuccess("The process has been terminated!").queue()
            }

        val description =
            "The ${process.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} process " +
                    "running in this channel has been terminated!"

        event.jda.getChannelById(GuildMessageChannel::class.java, process.channel)?.let { channel ->
            process.invoker?.let {
                channel.retrieveMessageById(it).await().delete().queue(null) {}
            }

            channel.sendMessage {
                embeds += defaultEmbed(description, EmbedType.WARNING) {
                    text = "This message will self-delete in 5 seconds"
                }

                process.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                    .takeUnless { it.isEmpty() }
                    ?.let { content += it }
            }.queue({ it.delete().queueAfter(5, TimeUnit.SECONDS, null) {} }) {}
        }
    }

    private fun processesListEmbed(
        originalSet: Set<WaiterProcess>,
        page: Int,
        jda: JDA,
        guild: Guild,
    ) = buildEmbed {
        val partition = Lists.partition(originalSet.toList(), 5)
        val processes = partition[page]

        color = Immutable.SUCCESS

        for ((index, process) in processes.withIndex()) {
            val users = process.users.joinToString { id ->
                jda.getUserById(id)?.let {
                    if (guild.isMember(it)) {
                        it.asMention
                    } else {
                        it.asTag
                    }
                } ?: id.toString()
            }

            val channel = process.channel.let { id ->
                jda.getChannelById(GuildMessageChannel::class.java, id)?.let {
                    if (it.guild.idLong == guild.idLong) {
                        it.asMention
                    } else {
                        "${it.name} (${it.id}, ${it.guild.name.escapeMarkdown()} (${it.guild.id}))"
                    }
                } ?: id.toString()
            }

            field {
                title = "Process #${(index.inc() + page * 5).toDecimalFormat("#,###")}"
                description = """**Command**: ${process.command?.getEffectiveContextName() ?: "Unknown"}
                    **Event**: ${process.eventType?.simpleName ?: "Unknown"}
                    **Process ID**: ${process.id}
                    **Users**: $users
                    **Channel**: $channel
                    **Creation Time**: ${TimeFormat.DATE_TIME_LONG.format(process.timeCreated)}
                """.trimIndent()
            }
        }

        author {
            name = "Process Manager"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        if (partition.size > 1) {
            footer { text = "Total Processes: ${originalSet.size.toDecimalFormat("#,###")}" }
        }
    }

    private fun pageButtons(userId: String, page: Int, size: Int) = setOf(
        Button.primary("$interactionName-$userId-kill", "Terminate"),
        Button.secondary("$interactionName-$userId-$page-first", "First Page")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$interactionName-$userId-$page-back", "Back")
            .applyIf(page == 0) { asDisabled() },
        Button.secondary("$interactionName-$userId-$page-next", "Next")
            .applyIf(page == size.dec()) { asDisabled() },
        Button.secondary("$interactionName-$userId-$page-last", "Last Page")
            .applyIf(page == size.dec()) { asDisabled() },
    )
}