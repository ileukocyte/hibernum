package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.*

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.TimeoutCancellationException

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class TicTacToeCommand : SlashOnlyCommand {
    override val name = "tictactoe"
    override val description = "Starts the tic-tac-toe game against the specified user"
    override val options = setOf(
        OptionData(OptionType.USER, "opponent", "The user to play tic-tac-toe against", true))

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        if (event.user.processes.any { it.command is TicTacToeCommand }) {
            throw CommandException("You have another TicTacToe command running somewhere else! " +
                    "Finish the process first!")
        }

        val opponent = event.getOption("opponent")?.asUser
            ?.takeUnless { it.isBot || it.idLong == event.user.idLong }
            ?: throw CommandException("You cannot play against the specified user!")

        val staticProcessId = generateStaticProcessId(event.jda)

        val hook = event.replyConfirmation("Do you want to play tic-tac-toe against ${event.user.asMention}?")
            .setContent(opponent.asMention)
            .addActionRow(
                Button.secondary("$name-${opponent.idLong}-${event.user.idLong}-$staticProcessId-play", "Yes"),
                Button.danger("$name-${opponent.idLong}-${event.user.idLong}-deny", "No"),
            ).await()
        val message = hook.retrieveOriginal().await()

        event.jda.awaitEvent<ButtonInteractionEvent>(waiterProcess = waiterProcess {
            channel = event.messageChannel.idLong
            users += setOf(event.user.idLong, opponent.idLong)
            command = this@TicTacToeCommand
            invoker = message.idLong
            id = staticProcessId
        }) { it.user.idLong == opponent.idLong && it.message.idLong == message.idLong } // used to block other commands
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id == id.first()) {
            when (id.last()) {
                "play" -> {
                    val deferred = event.editComponents().await()

                    try {
                        val opponent = event.user
                        val starter = event.guild?.retrieveMemberById(id[1])?.await()?.user
                            ?: return

                        val staticProcessId = id[2].toInt()

                        val ttt = TicTacToe(starter, opponent)

                        val embed = buildEmbed {
                            description = "It is ${starter.asMention}'s turn!"
                            color = Immutable.SUCCESS

                            footer {
                                text = "Type in \"exit\" to finish the session!"
                                iconUrl = ttt.currentTurn.effectiveAvatarUrl
                            }
                        }

                        val board = try {
                            deferred.editOriginalEmbeds(embed)
                                .setContent(ttt.printableBoard)
                                .await()
                        } catch (_: ErrorResponseException) {
                            event.channel.sendMessageEmbeds(embed)
                                .setContent(ttt.printableBoard)
                                .await()
                        }

                        awaitTurn(ttt, event.channel, board, staticProcessId)
                    } catch (_: ErrorResponseException) {
                        deferred.setFailureEmbed(
                            "The user who initiated the game is no longer available for the bot!"
                        ).queue(null) {
                            event.messageChannel
                                .sendFailure("The user who initiated the game is no longer available for the bot!")
                                .queue()
                        }
                    }
                }
                "deny" -> {
                    val starter = try {
                        event.guild?.retrieveMemberById(id[1])?.await()?.user
                    } catch (_: ErrorResponseException) {
                        null
                    }

                    val embed = defaultEmbed(
                        "${event.user.asMention} has denied your tic-tac-toe invitation!",
                        EmbedType.FAILURE,
                    )

                    event.editComponents()
                        .setEmbeds(embed)
                        .setContent(starter?.asMention.orEmpty())
                        .queue(null) {
                            event.messageChannel.sendMessageEmbeds(embed)
                                .setContent(starter?.asMention.orEmpty())
                                .queue()
                        }
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun awaitTurn(
        ttt: TicTacToe,
        channel: MessageChannel,
        message: Message,
        processId: Int,
    ) {
        try {
            val response = channel.awaitMessage(ttt.players, this, message, processId = processId)
                ?: return
            val content = response.contentRaw

            if (content.isInt && content.toInt() in 1..9) {
                if (response.author.idLong != ttt.currentTurn.idLong) {
                    response.replyFailure("It is currently not your turn!") {
                        text = "Type in \"exit\" to finish the session!"
                    }.queue()

                    awaitTurn(ttt, channel, message, processId)
                } else {
                    try {
                        val (row, column) = TicTacToe.COORDS[content.toInt()] ?: return

                        val sign = if (ttt.currentTurn == ttt.starter) {
                            TTT_CROSS
                        } else {
                            TTT_NOUGHT
                        }

                        ttt.turn(row, column, sign)

                        if (ttt.isOver) {
                            response.replyEmbed {
                                description = "${ttt.currentTurn.asMention} wins!"
                                color = Immutable.SUCCESS

                                author {
                                    name = "Congratulations!"
                                    iconUrl = ttt.currentTurn.effectiveAvatarUrl
                                }
                            }.setContent(ttt.printableBoard).queue()
                        } else {
                            if (ttt.board.flatten().none { it matches TicTacToe.GAP_REGEX }) {
                                response.replyEmbed {
                                    description = "It is a draw!"
                                    color = Immutable.SUCCESS
                                }.setContent(ttt.printableBoard).queue()
                            } else {
                                ttt.switchTurns()

                                val board = response.replyEmbed {
                                    description = "It is ${ttt.currentTurn.asMention}'s turn!"
                                    color = Immutable.SUCCESS

                                    footer {
                                        text = "Type in \"exit\" to finish the session!"
                                        iconUrl = ttt.currentTurn.effectiveAvatarUrl
                                    }
                                }.setContent(ttt.printableBoard).await()

                                awaitTurn(ttt, channel, board, processId)
                            }
                        }
                    } catch (_: TakenGapException) {
                        response.replyFailure("The gap is already taken!").queue()

                        awaitTurn(ttt, channel, message, processId)
                    }
                }
            } else {
                if (content.lowercase() == "exit") {
                    val confirmation = response.replyConfirmation("Are you sure you want to exit?")
                        .setActionRow(
                            Button.danger("$name-${response.author.idLong}-exit", "Yes"),
                            Button.secondary("$name-${response.author.idLong}-stay", "No"),
                        ).await()

                    try {
                        val buttonEvent = response.jda
                            .awaitEvent<ButtonInteractionEvent>(15, TimeUnit.MINUTES, waiterProcess = waiterProcess {
                                this.channel = channel.idLong
                                users += response.author.idLong
                                command = this@TicTacToeCommand
                                invoker = confirmation.idLong
                                id = processId
                            }) {
                                it.user.idLong == response.author.idLong && it.message.idLong == confirmation.idLong
                            } ?: return

                        when (buttonEvent.componentId.split("-").last()) {
                            "exit" -> {
                                buttonEvent.editComponents().setEmbeds(
                                    defaultEmbed(
                                        "The game session has been terminated by ${buttonEvent.user.asMention}!",
                                        EmbedType.SUCCESS,
                                    )
                                ).queue(null) {
                                    channel.sendSuccess("The game session has been terminated by ${buttonEvent.user.asMention}!")
                                        .queue()
                                }
                            }
                            "stay" -> {
                                buttonEvent.editComponents().setEmbeds(
                                    defaultEmbed(
                                        "The game session has been resumed!",
                                        EmbedType.SUCCESS,
                                    )
                                ).queue(null) {
                                    channel.sendSuccess("The game session has been resumed!")
                                        .queue()
                                }

                                awaitTurn(ttt, channel, message, processId)
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        confirmation.editMessageComponents().setEmbeds(
                            defaultEmbed("Time is out!", EmbedType.FAILURE)
                        ).setContent(EmbedBuilder.ZERO_WIDTH_SPACE).queue(null) {
                            channel.sendFailure("Time is out!").queue()
                        }
                    }
                } else {
                    response.replyFailure("The message is not of the expected turn format!") {
                        text = "Type in \"exit\" to finish the session!"
                    }.queue()

                    awaitTurn(ttt, channel, message, processId)
                }
            }
        } catch (_: TimeoutCancellationException) {
            message.editMessageEmbeds(defaultEmbed("Time is out!", EmbedType.FAILURE))
                .setContent(EmbedBuilder.ZERO_WIDTH_SPACE)
                .queue(null) {
                    channel.sendFailure("Time is out!").queue()
                }
        }
    }

    private class TicTacToe(
        val starter: User,
        val opponent: User,
    ) {
        val board = arrayOf(
            arrayOf("1\u20E3", "2\u20E3", "3\u20E3"),
            arrayOf("4\u20E3", "5\u20E3", "6\u20E3"),
            arrayOf("7\u20E3", "8\u20E3", "9\u20E3"),
        )

        val printableBoard: String
            get() = buildString {
                for (row in 0 until ROWS) {
                    for (column in 0 until COLUMNS) {
                        append(board[row][column])
                    }

                    appendLine()
                }
            }

        var currentTurn = starter

        val players = setOf(starter, opponent)

        val isOver: Boolean
            get() {
                for (row in 0 until ROWS) {
                    if (!board[row][0].matches(GAP_REGEX)
                            && board[row][0] == board[row][1]
                            && board[row][1] == board[row][2]) {
                        return true
                    }
                }

                for (column in 0 until COLUMNS) {
                    if (!board[0][column].matches(GAP_REGEX)
                            && board[0][column] == board[1][column]
                            && board[1][column] == board[2][column]) {
                        return true
                    }
                }

                if (!board[0][0].matches(GAP_REGEX)
                        && board[0][0] == board[1][1]
                        && board[1][1] == board[2][2]) {
                    return true
                }

                if (!board[0][2].matches(GAP_REGEX)
                        && board[0][2] == board[1][1]
                        && board[1][1] == board[2][0]) {
                    return true
                }

                return false
            }

        fun turn(row: Int, column: Int, sign: String) {
            if (board[row][column] matches GAP_REGEX) {
                board[row][column] = sign
            } else {
                throw TakenGapException()
            }
        }

        fun switchTurns() {
            currentTurn = if (currentTurn == starter) {
                opponent
            } else {
                starter
            }
        }

        companion object {
            const val ROWS = 3
            const val COLUMNS = 3

            @JvmStatic
            val GAP_REGEX = "[1-9]\u20E3".toRegex()

            @JvmStatic
            val COORDS = mapOf(
                1 to (0 to 0),
                2 to (0 to 1),
                3 to (0 to 2),
                4 to (1 to 0),
                5 to (1 to 1),
                6 to (1 to 2),
                7 to (2 to 0),
                8 to (2 to 1),
                9 to (2 to 2),
            )
        }
    }

    private class TakenGapException : RuntimeException()

    companion object {
        const val TTT_CROSS = "\u274C"
        const val TTT_NOUGHT = "\u2B55"
    }
}