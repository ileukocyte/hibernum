package io.ileukocyte.hibernum.commands.general

import com.google.common.collect.Lists

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.GenericCommand.StaleInteractionHandling
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.`fun`.AkinatorCommand
import io.ileukocyte.hibernum.commands.`fun`.ChomskyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.extensions.EmbedType.SUCCESS
import io.ileukocyte.hibernum.extensions.EmbedType.WARNING
import io.ileukocyte.hibernum.utils.*

import java.util.concurrent.TimeUnit

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType.STRING
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.utils.TimeFormat

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class SessionsCommand : SlashOnlyCommand {
    override val name = "sessions"
    override val description = "Sends a list of the currently running sessions of yours " +
            "or aborts the one provided by its ID"
    override val options = setOf(
        OptionData(STRING, "id", "The ID of the session to abort")
            .setAutoComplete(true))
    override val staleInteractionHandling = StaleInteractionHandling.REMOVE_COMPONENTS
    override val neglectProcessBlock = true

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val sessions = event.user.processes
            .filter { it.command !== null }
            .takeUnless { it.isEmpty() }
            ?: throw CommandException("No sessions of yours are currently running!")

        val input = event.getOption("id")?.asString

        if (input === null) {
            val pages = ceil(sessions.size / 5.0).toInt()

            val actionRows = mutableSetOf<ActionRow>()

            actionRows += ActionRow.of(
                pageButtons(event.user.id, 0, pages).takeIf { sessions.size > 5 }
                    ?: setOf(
                        Button.primary("$interactionName-${event.user.idLong}-abort", "Abort"),
                        Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"),
                    )
            )

            if (sessions.size > 5) {
                actionRows += ActionRow.of(Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"))
            }

            event.replyEmbeds(sessionsListEmbed(sessions, 0, event.jda, event.guild ?: return))
                .setComponents(actionRows)
                .queue()
        } else {
            val session = event.jda.getProcessById(input)
                ?.takeIf { event.user.idLong in it.users && it.command !== null }
                ?: throw CommandException("No session of yours has been found by the provided ID!")

            event.replyConfirmation("Are you sure you want to abort the session?")
                .addActionRow(
                    Button.danger("$interactionName-${event.user.idLong}-${session.id}-abortc", "Yes"),
                    Button.secondary("$interactionName-${event.user.idLong}-exit", "No"),
                ).queue()
        }
    }

    override suspend fun invoke(event: CommandAutoCompleteInteractionEvent) {
        event.getOption("id") { option ->
            val query = option.asString

            if (query.isNotEmpty()) {
                event.user.processes
                    .filter { it.id.startsWith(query) && it.command !== null }
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
            val sessions = event.user.processes
                .filter { it.command !== null }
                .takeUnless { it.isEmpty() }
                ?: return

            when (type) {
                "exit" -> event.message.delete().queue(null) {}
                "abort" -> {
                    val input = TextInput
                        .create("$interactionName-id", "Enter the Session ID:", TextInputStyle.SHORT)
                        .setMaxLength(4)
                        .build()
                    val modal = Modal
                        .create("$interactionName-modal", "Session Abortion")
                        .addActionRow(input)
                        .build()

                    event.replyModal(modal).queue()
                }
                "abortc" -> {
                    val session = event.jda.getProcessById(id[1])
                        ?.takeIf { event.user.idLong in it.users && it.command !== null }
                        ?: return

                    session.kill(event.jda)

                    if (session.command is AkinatorCommand) {
                        for (userId in session.users) {
                            AkinatorCommand.AKIWRAPPERS -= userId
                            AkinatorCommand.DECLINED_GUESSES -= userId
                            AkinatorCommand.GUESS_TYPES -= userId
                        }
                    }

                    if (session.command is ChomskyCommand) {
                        for (userId in session.users) {
                            ChomskyCommand.CHATTER_BOT_SESSIONS -= userId
                        }
                    }

                    event.editMessageEmbeds(defaultEmbed("The session has been aborted!", SUCCESS))
                        .setComponents(emptyList())
                        .queue(null) {
                            event.channel.sendSuccess("The session has been aborted!").queue()
                        }

                    val description =
                        "The ${session.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} session " +
                                "running in this channel has been aborted!"

                    event.jda.getChannelById(GuildMessageChannel::class.java, session.channel)?.let { channel ->
                        session.invoker?.let {
                            channel.retrieveMessageById(it).await().delete().queue(null) {}
                        }

                        channel.sendMessage {
                            embeds += defaultEmbed(description, WARNING) {
                                text = "This message will self-delete in 5 seconds"
                            }

                            session.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                                .takeUnless { it.isEmpty() }
                                ?.let { content += it }
                        }.queue({ it.delete().queueAfter(5, TimeUnit.SECONDS, null) {} }) {}
                    }
                }
                else -> {
                    val page = id[1].toInt()
                    val pages = ceil(sessions.size / 5.0).toInt()

                    fun getUpdatedButtons(initialPage: Int): Set<ActionRow> {
                        val actionRows = mutableSetOf<ActionRow>()

                        actionRows += ActionRow.of(
                            pageButtons(event.user.id, initialPage, pages).takeIf { sessions.size > 5 }
                                ?: setOf(
                                    Button.primary("$interactionName-${event.user.idLong}-abort", "Abort"),
                                    Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"),
                                )
                        )

                        if (sessions.size > 5) {
                            actionRows +=
                                ActionRow.of(Button.danger("$interactionName-${event.user.idLong}-exit", "Exit"))
                        }

                        return actionRows
                    }

                    when (type) {
                        "first" -> {
                            event.editMessageEmbeds(sessionsListEmbed(sessions, 0, event.jda, guild))
                                .setComponents(getUpdatedButtons(0))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(sessionsListEmbed(sessions, 0, event.jda, guild))
                                        .setComponents(getUpdatedButtons(0))
                                        .queue()
                                }
                        }
                        "last" -> {
                            val lastPage = pages.dec()

                            event.editMessageEmbeds(sessionsListEmbed(sessions, lastPage, event.jda, guild))
                                .setComponents(getUpdatedButtons(lastPage))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(sessionsListEmbed(sessions, lastPage, event.jda, guild))
                                        .setComponents(getUpdatedButtons(lastPage))
                                        .queue()
                                }
                        }
                        "back" -> {
                            val newPage = min(max(0, page.dec()), pages.dec())

                            event.editMessageEmbeds(sessionsListEmbed(sessions, newPage, event.jda, guild))
                                .setComponents(getUpdatedButtons(newPage))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(sessionsListEmbed(sessions, newPage, event.jda, guild))
                                        .setComponents(getUpdatedButtons(newPage))
                                        .queue()
                                }
                        }
                        "next" -> {
                            val newPage = min(page.inc(), pages.dec())

                            event.editMessageEmbeds(sessionsListEmbed(sessions, newPage, event.jda, guild))
                                .setComponents(getUpdatedButtons(newPage))
                                .queue(null) {
                                    event.message
                                        .editMessageEmbeds(sessionsListEmbed(sessions, newPage, event.jda, guild))
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
        val id = event.getValue("$interactionName-id")?.asString ?: return
        val session = event.jda.getProcessById(id)
            ?.takeIf { event.user.idLong in it.users && it.command !== null }
            ?: throw CommandException("No session of yours has been found by the provided ID!")

        session.kill(event.jda)

        if (session.command is AkinatorCommand) {
            for (userId in session.users) {
                AkinatorCommand.AKIWRAPPERS -= userId
                AkinatorCommand.DECLINED_GUESSES -= userId
                AkinatorCommand.GUESS_TYPES -= userId
            }
        }

        if (session.command is ChomskyCommand) {
            for (userId in session.users) {
                ChomskyCommand.CHATTER_BOT_SESSIONS -= userId
            }
        }

        event.editMessageEmbeds(defaultEmbed("The session has been aborted!", SUCCESS))
            .setComponents(emptyList())
            .queue(null) {
                event.messageChannel.sendSuccess("The session has been aborted!").queue()
            }

        val description =
            "The ${session.command?.let { it::class.simpleName } ?: event.jda.selfUser.name} session " +
                    "running in this channel has been aborted!"

        event.jda.getChannelById(GuildMessageChannel::class.java, session.channel)?.let { channel ->
            session.invoker?.let {
                channel.retrieveMessageById(it).await().delete().queue(null) {}
            }

            channel.sendMessage {
                embeds += defaultEmbed(description, WARNING) {
                    text = "This message will self-delete in 5 seconds"
                }

                session.users.mapNotNull { event.jda.getUserById(it)?.asMention }.joinToString()
                    .takeUnless { it.isEmpty() }
                    ?.let { content += it }
            }.queue({ it.delete().queueAfter(5, TimeUnit.SECONDS, null) {} }) {}
        }
    }

    private fun sessionsListEmbed(
        originalSet: Collection<WaiterProcess>,
        page: Int,
        jda: JDA,
        guild: Guild,
    ) = buildEmbed {
        val partition = Lists.partition(originalSet.toList(), 5)
        val sessions = partition[page]

        color = Immutable.SUCCESS

        for ((index, session) in sessions.withIndex()) {
            val users = session.users.joinToString { id ->
                jda.getUserById(id)?.let {
                    if (guild.isMember(it)) {
                        it.asMention
                    } else {
                        it.asTag
                    }
                } ?: id.toString()
            }

            val channel = session.channel.let { id ->
                jda.getChannelById(GuildMessageChannel::class.java, id)?.let {
                    if (it.guild.idLong == guild.idLong) {
                        it.asMention
                    } else {
                        "${it.name} (${it.guild.name.escapeMarkdown()}, ${it.guild.id})"
                    }
                } ?: id.toString()
            }

            field {
                title = "Session #${(index.inc() + page * 5).toDecimalFormat("#,###")}"
                description = """**Command**: ${session.command?.getEffectiveContextName() ?: "Unknown"}
                    **Session ID**: ${session.id}
                    **Users**: $users
                    **Channel**: $channel
                    **Creation Time**: ${TimeFormat.DATE_TIME_LONG.format(session.timeCreated)}
                """.trimIndent()
            }
        }

        author {
            name = "Session Manager"
            iconUrl = jda.selfUser.effectiveAvatarUrl
        }

        if (partition.size > 1) {
            footer { text = "Total Sessions: ${originalSet.size.toDecimalFormat("#,###")}" }
        }
    }

    private fun pageButtons(userId: String, page: Int, size: Int) = setOf(
        Button.primary("$interactionName-$userId-abort", "Abort"),
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