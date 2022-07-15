package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.commands.ClassicTextOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.utils.awaitEvent
import io.ileukocyte.hibernum.utils.processes
import io.ileukocyte.hibernum.utils.waiterProcess
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button

class TicTacToeCommand : ClassicTextOnlyCommand {
    override val name = "ttt"
    override val description = "n/a"

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val opponent = event.message.mentions.usersBag.firstOrNull()
            ?.takeUnless { it.isBot || it.idLong == event.author.idLong }
            ?: return

        val ttt = TicTacToe(event.author, opponent)

        val staticProcessId = (1..9999).filter {
            it !in event.jda.processes.map { p -> p.id.toInt() }
        }.random()

        val board = event.channel.sendEmbed {
            description = "It is ${event.author.asMention}'s turn!"
        }.content(ttt.printableBoard).await()

        awaitTurn(ttt, event.channel, board, staticProcessId)
    }

    private suspend fun awaitTurn(
        ttt: TicTacToe,
        channel: MessageChannel,
        message: Message?,
        processId: Int,
    ) {
        try {
            val response = channel.awaitMessage(ttt.players, this, message, 5, processId = processId)
                ?: return
            val content = response.contentRaw

            if (content.isInt && content.toInt() in 1..9) {
                if (response.author.idLong != ttt.currentTurn.idLong) {
                    response.replyFailure("It is currently not your turn!").queue()

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
                                description = "${ttt.currentTurn.asMention} wins! Well done!"
                                color = Immutable.SUCCESS
                            }.content(ttt.printableBoard).queue()
                        } else {
                            if (ttt.board.flatten().none { it matches TicTacToe.GAP_REGEX }) {
                                response.replyEmbed {
                                    description = "It is a draw!"
                                    color = Immutable.SUCCESS
                                }.content(ttt.printableBoard).queue()
                            } else {
                                ttt.inverseTurn()

                                val board = response.replyEmbed {
                                    description = "It is ${ttt.currentTurn.asMention}'s turn now!"
                                    color = Immutable.SUCCESS
                                }.content(ttt.printableBoard).await()

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
                            Button.danger("$name-exit", "Yes"),
                            Button.secondary("$name-stay", "No"),
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
                        ).queue(null) {
                            channel.sendFailure("Time is out!").queue()
                        }
                    }
                } else {
                    response.replyFailure("You have tried an invalid turn!").queue()

                    awaitTurn(ttt, channel, message, processId)
                }
            }
        } catch (_: TimeoutCancellationException) {

        }
    }

    class TicTacToe(
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

        fun inverseTurn() {
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