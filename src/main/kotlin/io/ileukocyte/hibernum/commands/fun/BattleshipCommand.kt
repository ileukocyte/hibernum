package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.commands.`fun`.BattleshipCommand.Battleship.Coordinate
import io.ileukocyte.hibernum.extensions.*
import io.ileukocyte.hibernum.extensions.EmbedType
import io.ileukocyte.hibernum.utils.*

import java.util.concurrent.TimeUnit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class BattleshipCommand : SlashOnlyCommand {
    override val name = "battleship"
    override val description = "Starts the battleship game against the specified user in the DMs"
    override val options = setOf(
        OptionData(OptionType.USER, "opponent", "The user to play the sea battle against", true)
    )

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        if (event.user.processes.any { it.command is BattleshipCommand }) {
            throw CommandException("You have another Battleship command running somewhere else! " +
                    "Finish the process first!")
        }

        val opponent = event.getOption("opponent")?.asUser
            ?.takeUnless { it.isBot || it.idLong == event.user.idLong }
            ?: throw CommandException("You cannot play against the specified user!")

        val staticProcessId = generateStaticProcessId(event.jda)

        val hook = event.replyConfirmation("Do you want to play the sea battle against ${event.user.asMention}?")
            .setContent(opponent.asMention)
            .addActionRow(
                Button.secondary(
                    "$interactionName-${opponent.idLong}-${event.user.idLong}-$staticProcessId-play",
                    "Yes",
                ),
                Button.danger("$interactionName-${opponent.idLong}-${event.user.idLong}-deny", "No"),
            ).await()
        val message = hook.retrieveOriginal().await()

        event.jda.awaitEvent<ButtonInteractionEvent>(waiterProcess = waiterProcess {
            channel = event.messageChannel.idLong
            users += setOf(event.user.idLong, opponent.idLong)
            command = this@BattleshipCommand
            invoker = message.idLong
            id = staticProcessId
        }) { it.user.idLong == opponent.idLong && it.message.idLong == message.idLong } // used to block other commands
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$interactionName-").split("-")

        if (event.user.id in id.first().split("|")) {
            when (id.last()) {
                "play" -> {
                    val deferred = event.editComponents().await()
                    val staticProcessId = id[2].toInt()

                    try {
                        val starter = event.guild?.retrieveMemberById(id[1])?.await()?.user
                            ?.let { Battleship.BattleshipPlayer(it, it.openPrivateChannel().await(), event.user) }
                            ?: return
                        val opponent = Battleship.BattleshipPlayer(
                            event.user,
                            event.user.openPrivateChannel().await(),
                            starter.user,
                        )

                        CoroutineScope(WaiterContext).launch {
                            event.jda.awaitEvent<MessageReceivedEvent>(waiterProcess = waiterProcess {
                                channel = event.messageChannel.idLong
                                users += setOf(event.user.idLong, opponent.user.idLong)
                                command = this@BattleshipCommand
                                this.id = staticProcessId
                            }) { false } // used to block other commands
                        }

                        val battleship = Battleship(starter, opponent)

                        for (player in battleship.players) {
                            try {
                                val check = player.dm
                                    .sendWarning("Checking if the game can be played in the DMs!") {
                                        text = "This message will self-delete in 5 seconds"
                                    }.await()

                                check.delete().queueAfter(5, TimeUnit.SECONDS, null) {}

                                player.generateOwnBoard()
                            } catch (_: ErrorResponseException) {
                                event.jda.getProcessById("%04d".format(staticProcessId))?.kill(event.jda)

                                val error = "The game session has been aborted " +
                                        "since the bot is unable to DM one of the players (${player.user.asMention})!"

                                deferred.setFailureEmbed(error).queue(null) {
                                    throw CommandException(error)
                                }
                            }
                        }

                        starter.dm.sendMessageEmbeds(
                            buildEmbed {
                                color = Immutable.SUCCESS
                                description = "It is your turn!"
                            },
                            starter.getPrintableOwnBoard(true),
                            starter.getPrintableOpponentBoard(true),
                        ).await()

                        opponent.dm.sendMessageEmbeds(
                            buildEmbed {
                                color = Immutable.WARNING
                                description = "It is ${starter.user.asMention}'s turn!"
                            },
                            opponent.getPrintableOwnBoard(true),
                            opponent.getPrintableOpponentBoard(true),
                        ).await()

                        val termination = Button.danger(
                            "$interactionName-${starter.user.idLong}|${opponent.user.idLong}-$staticProcessId-exit",
                            "Terminate",
                        )

                        val serverMessage = try {
                            deferred.setSuccessEmbed("The session has started!") {
                                text = "Do not delete this message!"
                            }.setContent(null)
                                .setActionRow(termination)
                                .await()
                        } catch (_: ErrorResponseException) {
                            event.messageChannel.sendSuccess("The session has started!") {
                                text = "Do not delete this message!"
                            }.setActionRow(termination).await()
                        }

                        event.jda.getProcessById("%04d".format(staticProcessId))?.kill(event.jda)

                        awaitTurn(battleship, serverMessage, event.messageChannel, staticProcessId)
                    } catch (_: ErrorResponseException) {
                        event.jda.getProcessById("%04d".format(staticProcessId))?.kill(event.jda)

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
                        "${event.user.asMention} has denied your battleship invitation!",
                        EmbedType.FAILURE,
                    )

                    event.editComponents()
                        .setEmbeds(embed)
                        .setContent(starter?.asMention.orEmpty())
                        .queue(null) {
                            event.messageChannel.sendMessageEmbeds(embed)
                                .setContent(starter?.asMention.orEmpty()).queue()
                        }
                }
                "exit" -> {
                    val processId = "%04d".format(id[1].toInt())

                    event.jda.getProcessById(processId)?.kill(event.jda)

                    event.editComponents()
                        .setWarningEmbed("The session has been terminated by ${event.user.asMention}!")
                        .queue(null) {
                            event.messageChannel.sendWarning(
                                "The session has been terminated by ${event.user.asMention}!").queue()
                        }

                    val anotherPlayer = try {
                        val anotherPlayer = id.first().split("|").first { it != event.user.id }

                        event.jda.retrieveUserById(anotherPlayer).await()
                    } catch (_: ErrorResponseException) {
                        null
                    }

                    try {
                        anotherPlayer?.openPrivateChannel()?.await()
                            ?.sendWarning("The session has been terminated by ${event.user.asMention}!")
                            ?.await()
                    } catch (_: ErrorResponseException) {}
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    private suspend fun awaitTurn(
        battleship: Battleship,
        guildMessage: Message,
        guildChannel: MessageChannel,
        processId: Int,
    ) {
        try {
            val currentTurn = battleship.currentTurn

            try {
                val turnMessage = currentTurn.dm.awaitMessage(
                    battleship.players.map { it.user }.toSet(),
                    this,
                    delay = 10,
                    processId = processId,
                ) { it.message.contentRaw matches Battleship.TURN_REGEX } ?: return

                val (columnLetter, rowChar) = turnMessage.contentRaw.toCharArray()

                val row = rowChar.digitToInt()
                val column = Battleship.letterToIndex(columnLetter.lowercaseChar()) ?: return

                val turn = currentTurn.opponentBoard.board[row][column]

                if (turn == Battleship.RED_SQUARE || turn == Battleship.YELLOW_SQUARE) {
                    turnMessage.channel.sendFailure("The gap is taken! Try again!").queue()

                    awaitTurn(battleship, guildMessage, guildChannel, processId)
                } else {
                    val opponent = battleship.players.first { it.user.idLong != currentTurn.user.idLong }

                    if (opponent.ownBoard.board[row][column] == Battleship.WHITE_SQUARE) {
                        opponent.ownBoard.board[row][column] = Battleship.YELLOW_SQUARE
                        currentTurn.opponentBoard.board[row][column] = Battleship.YELLOW_SQUARE

                        turnMessage.replyEmbeds(
                            defaultEmbed(
                                "You have missed! It is ${opponent.user.asTag}'s turn!",
                                EmbedType.WARNING
                            ),
                            currentTurn.getPrintableOwnBoard(),
                            currentTurn.getPrintableOpponentBoard(),
                        ).await()

                        opponent.dm.sendMessageEmbeds(
                            defaultEmbed(
                                "${currentTurn.user.asTag} has missed! It is your turn!",
                                EmbedType.SUCCESS,
                            ),
                            opponent.getPrintableOwnBoard(),
                            opponent.getPrintableOpponentBoard(),
                        ).await()

                        battleship.reverseTurn()

                        awaitTurn(battleship, guildMessage, guildChannel, processId)
                    } else {
                        opponent.ownBoard.board[row][column] = Battleship.RED_SQUARE
                        currentTurn.opponentBoard.board[row][column] = Battleship.RED_SQUARE

                        if (!battleship.isOver) {
                            val hitOrDestroyed =
                                opponent.ownShips.firstOrNull {
                                    Coordinate(row to column) in it.coords
                                }?.let { ship ->
                                    ship.coords.map { opponent.ownBoard.board[it.coords.first][it.coords.second] }
                                }?.takeUnless { it.none { c -> c == Battleship.BLUE_SQUARE } }
                                    ?.let { "hit" }
                                    ?: "destroyed"

                            turnMessage.replyEmbeds(
                                defaultEmbed(
                                    "You have $hitOrDestroyed your opponent's ship! It is your turn once again!",
                                    EmbedType.SUCCESS,
                                ),
                                currentTurn.getPrintableOwnBoard(),
                                currentTurn.getPrintableOpponentBoard(),
                            ).await()

                            opponent.dm.sendMessageEmbeds(
                                defaultEmbed(
                                    "${currentTurn.user.asTag} has $hitOrDestroyed your ship! It is their turn once again!",
                                    EmbedType.FAILURE,
                                ),
                                opponent.getPrintableOwnBoard(),
                                opponent.getPrintableOpponentBoard(),
                            ).await()

                            awaitTurn(battleship, guildMessage, guildChannel, processId)
                        } else {
                            val guildEmbed = buildEmbed {
                                description = "${currentTurn.user.asMention} wins!"
                                color = Immutable.SUCCESS

                                author {
                                    name = "Congratulations!"
                                    iconUrl = currentTurn.user.effectiveAvatarUrl
                                }
                            }

                            guildMessage
                                .editMessageComponents()
                                .setEmbeds(guildEmbed)
                                .queue(null) {}

                            turnMessage.replyEmbeds(
                                buildEmbed {
                                    description = "You have destroyed all the opponent's ships!"
                                    color = Immutable.SUCCESS

                                    author {
                                        name = "Congratulations!"
                                        iconUrl = currentTurn.user.effectiveAvatarUrl
                                    }
                                },
                                currentTurn.getPrintableOwnBoard(),
                                currentTurn.getPrintableOpponentBoard(),
                            ).await()

                            opponent.dm.sendMessageEmbeds(
                                buildEmbed {
                                    description =
                                        "${currentTurn.user.asTag} wins! All of your ships have been destroyed!"
                                    color = Immutable.FAILURE

                                    author {
                                        name = "Game Over!"
                                        iconUrl = currentTurn.user.effectiveAvatarUrl
                                    }
                                },
                                opponent.getPrintableOwnBoard(),
                                currentTurn.getPrintableOwnBoard(isOwn = false),
                            ).await()
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                for (player in battleship.players) {
                    player.dm.sendFailure("Time is out!").queue(null) {}
                }

                guildMessage.editMessageComponents()
                    .setFailureEmbed("Time is out!")
                    .queue(null) {}
            }
        } catch (_: ErrorResponseException) {
            if (!battleship.isOver) {
                val error = "The game session has been aborted " +
                        "since the bot is unable to DM one of the players!"

                guildMessage.editMessageComponents()
                    .setFailureEmbed(error)
                    .queue(null) {
                        guildChannel.sendFailure(error).queue(null) {}
                    }
            }
        }
    }

    data class Battleship(val starter: BattleshipPlayer, val opponent: BattleshipPlayer) {
        var currentTurn = starter

        val players: Set<BattleshipPlayer>
            get() = setOf(starter, opponent)

        data class BattleshipPlayer(val user: User, val dm: PrivateChannel, val opponent: User) {
            var ownBoard = Board(Array(10) { Array(10) { WHITE_SQUARE } })
            val opponentBoard = Board(Array(10) { Array(10) { WHITE_SQUARE } })

            val ownShips = mutableSetOf<Ship>()

            fun generateOwnBoard() {
                ownBoard = ownBoard.generateBoard()
            }

            private fun Board.generateBoard(): Board {
                val range = 0..9

                repeat(4) {
                    val coord: Pair<Int, Int>

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (board[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                board[row][column] = BLUE_SQUARE

                                coord = row to column

                                break
                            }
                        }
                    }

                    ownShips += SingleCellShip(Coordinate(coord))
                }

                repeat(3) {
                    val coord1: Coordinate
                    val coord2: Coordinate

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (board[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                val first = row.inc() to column
                                val second = row.dec() to column
                                val third = row to column.inc()
                                val fourth = row to column.dec()


                                val nearbyPossiblyUnavailableGaps = listOf(first, second, third, fourth)
                                    .associateWith { getPossiblyUnavailableGaps(Coordinate(it)) }

                                val filtered = nearbyPossiblyUnavailableGaps.filter { (coord, npug) ->
                                    coord.first in 0..9
                                            && coord.second in 0..9
                                            && npug.none { it == BLUE_SQUARE }
                                }

                                if (filtered.isNotEmpty()) {
                                    val (nextRow, nextColumn) = filtered.keys.random()

                                    board[row][column] = BLUE_SQUARE
                                    board[nextRow][nextColumn] = BLUE_SQUARE

                                    coord1 = Coordinate(row to column)
                                    coord2 = Coordinate(nextRow to nextColumn)

                                    break
                                }
                            }
                        }
                    }

                    ownShips += DoubleCellShip(coord1, coord2)
                }

                repeat(2) {
                    val coord1: Coordinate
                    val coord2: Coordinate
                    val coord3: Coordinate

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (board[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                val first = row.inc() to column
                                val second = row.dec() to column
                                val third = row to column.inc()
                                val fourth = row to column.dec()

                                val nearbyPossiblyUnavailableGaps = listOf(first, second, third, fourth)
                                    .associateWith { getPossiblyUnavailableGaps(Coordinate(it)) }

                                val filtered = nearbyPossiblyUnavailableGaps.filter { (coord, npug) ->
                                    coord.first in 0..9
                                            && coord.second in 0..9
                                            && npug.none { it == BLUE_SQUARE }
                                }

                                if (filtered.isNotEmpty()) {
                                    val (nextRow, nextColumn) = filtered.keys.random()

                                    if (nextRow == row) {
                                        val nextNextColumn = if (nextColumn == column.inc()) {
                                            board[row].getOrNull(nextColumn.inc())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextColumn.inc() }
                                                ?: board[row].getOrNull(column.dec())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.dec() }
                                        } else {
                                            board[row].getOrNull(nextColumn.dec())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextColumn.dec() }
                                                ?: board[row].getOrNull(column.inc())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.inc() }
                                        }

                                        if (nextNextColumn !== null) {
                                            if (getPossiblyUnavailableGaps(Coordinate(row to nextNextColumn))
                                                    .none { it == BLUE_SQUARE }) {
                                                coord1 = Coordinate(row to column)
                                                coord2 = Coordinate(row to nextColumn)
                                                coord3 = Coordinate(row to nextNextColumn)

                                                board[row][column] = BLUE_SQUARE
                                                board[row][nextColumn] = BLUE_SQUARE
                                                board[row][nextNextColumn] = BLUE_SQUARE

                                                break
                                            }
                                        }
                                    } else {
                                        val nextNextRow = if (nextRow == row.inc()) {
                                            board.getOrNull(nextRow.inc())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextRow.inc() }
                                                ?: board.getOrNull(row.dec())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.dec() }
                                        } else {
                                            board.getOrNull(nextRow.dec())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextRow.dec() }
                                                ?: board.getOrNull(row.inc())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.inc() }
                                        }

                                        if (nextNextRow !== null) {
                                            if (getPossiblyUnavailableGaps(Coordinate(nextNextRow to column))
                                                    .none { it == BLUE_SQUARE }) {
                                                coord1 = Coordinate(row to column)
                                                coord2 = Coordinate(nextRow to column)
                                                coord3 = Coordinate(nextNextRow to column)

                                                board[row][column] = BLUE_SQUARE
                                                board[nextRow][column] = BLUE_SQUARE
                                                board[nextNextRow][column] = BLUE_SQUARE

                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ownShips += TripleCellShip(coord1, coord2, coord3)
                }

                run {
                    val firstCell: Coordinate
                    val secondCell: Coordinate
                    val thirdCell: Coordinate
                    val fourthCell: Coordinate

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (board[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                val first = row.inc() to column
                                val second = row.dec() to column
                                val third = row to column.inc()
                                val fourth = row to column.dec()

                                val nearbyPossiblyUnavailableGaps = listOf(first, second, third, fourth)
                                    .associateWith { getPossiblyUnavailableGaps(Coordinate(it)) }

                                val filtered = nearbyPossiblyUnavailableGaps.filter { (coord, npug) ->
                                    coord.first in 0..9
                                            && coord.second in 0..9
                                            && npug.none { it == BLUE_SQUARE }
                                }

                                if (filtered.isNotEmpty()) {
                                    val (secondCellRow, secondCellColumn) = filtered.keys.random()

                                    if (secondCellRow == row) {
                                        val thirdCellColumn = if (secondCellColumn == column.inc()) {
                                            board[row].getOrNull(secondCellColumn.inc())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellColumn.inc() }
                                                ?: board[row].getOrNull(column.dec())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.dec() }
                                        } else {
                                            board[row].getOrNull(secondCellColumn.dec())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellColumn.dec() }
                                                ?: board[row].getOrNull(column.inc())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.inc() }
                                        }

                                        if (thirdCellColumn !== null) {
                                            if (getPossiblyUnavailableGaps(Coordinate(row to thirdCellColumn))
                                                    .none { it == BLUE_SQUARE }) {
                                                val columns = sortedSetOf(column, secondCellColumn, thirdCellColumn)

                                                val fourthCellColumn = columns.min().dec()
                                                    .takeIf { it in range && board[row][it] != BLUE_SQUARE }
                                                    ?: columns.max().inc()
                                                        .takeIf { it in range && board[row][it] != BLUE_SQUARE }

                                                if (fourthCellColumn !== null) {
                                                    if (getPossiblyUnavailableGaps(Coordinate(row to fourthCellColumn))
                                                            .none { it == BLUE_SQUARE }) {
                                                        firstCell = Coordinate(row to column)
                                                        secondCell = Coordinate(row to secondCellColumn)
                                                        thirdCell = Coordinate(row to thirdCellColumn)
                                                        fourthCell = Coordinate(row to fourthCellColumn)

                                                        board[row][column] = BLUE_SQUARE
                                                        board[row][secondCellColumn] = BLUE_SQUARE
                                                        board[row][thirdCellColumn] = BLUE_SQUARE
                                                        board[row][fourthCellColumn] = BLUE_SQUARE

                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val thirdCellRow = if (secondCellRow == row.inc()) {
                                            board.getOrNull(secondCellRow.inc())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellRow.inc() }
                                                ?: board.getOrNull(row.dec())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.dec() }
                                        } else {
                                            board.getOrNull(secondCellRow.dec())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellRow.dec() }
                                                ?: board.getOrNull(row.inc())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.inc() }
                                        }

                                        if (thirdCellRow !== null) {
                                            if (getPossiblyUnavailableGaps(Coordinate(thirdCellRow to column))
                                                    .none { it == BLUE_SQUARE }) {
                                                val rows = sortedSetOf(row, secondCellRow, thirdCellRow)

                                                val fourthCellRow = rows.min().dec()
                                                    .takeIf { it in range && board[it][column] != BLUE_SQUARE }
                                                    ?: rows.max().inc()
                                                        .takeIf { it in range && board[it][column] != BLUE_SQUARE }

                                                if (fourthCellRow !== null) {
                                                    if (getPossiblyUnavailableGaps(Coordinate(fourthCellRow to column))
                                                            .none { it == BLUE_SQUARE }) {
                                                        firstCell = Coordinate(row to column)
                                                        secondCell = Coordinate(secondCellRow to column)
                                                        thirdCell = Coordinate(thirdCellRow to column)
                                                        fourthCell = Coordinate(fourthCellRow to column)

                                                        board[row][column] = BLUE_SQUARE
                                                        board[secondCellRow][column] = BLUE_SQUARE
                                                        board[thirdCellRow][column] = BLUE_SQUARE
                                                        board[fourthCellRow][column] = BLUE_SQUARE

                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ownShips += QuadrupleCellShip(firstCell, secondCell, thirdCell, fourthCell)
                }

                return this
            }

            private fun Board.getPossiblyUnavailableGaps(coord: Coordinate) =
                getPossiblyUnavailableGaps(coord.coords.first, coord.coords.second)

            private fun Board.getPossiblyUnavailableGaps(
                row: Int,
                column: Int,
            ) = listOf(
                board.getOrNull(row.inc())?.getOrNull(column),
                board.getOrNull(row.dec())?.getOrNull(column),
                board.getOrNull(row)?.getOrNull(column.inc()),
                board.getOrNull(row)?.getOrNull(column.dec()),
                board.getOrNull(row.inc())?.getOrNull(column.inc()),
                board.getOrNull(row.inc())?.getOrNull(column.dec()),
                board.getOrNull(row.dec())?.getOrNull(column.inc()),
                board.getOrNull(row.dec())?.getOrNull(column.dec()),
            )

            fun getPrintableOwnBoard(
                isStartingMessage: Boolean = false,
                isOwn: Boolean = true,
            ) = buildEmbed {
                description = "\u2B1B"

                repeat(10) {
                    append(Emoji.fromFormatted(":regional_indicator_${'a' + it}:").name)
                }

                appendLine()

                for ((index, row) in ownBoard.board.withIndex()) {
                    append("${'0' + index}\u20E3")
                    appendLine(row.joinToString(""))
                }

                author {
                    name = if (isOwn) {
                        "Your Board"
                    } else {
                        "Opponent's Board"
                    }

                    iconUrl = user.effectiveAvatarUrl
                }

                color = Immutable.SUCCESS

                if (isStartingMessage) {
                    footer {
                        text = "You can go back to the server channel in order to terminate the game session!"
                    }
                }
            }

            fun getPrintableOpponentBoard(isStartingMessage: Boolean = false) = buildEmbed {
                description = "\u2B1B"

                repeat(10) {
                    append(Emoji.fromFormatted(":regional_indicator_${'a' + it}:").name)
                }

                appendLine()

                for ((index, row) in opponentBoard.board.withIndex()) {
                    append("${'0' + index}\u20E3")
                    appendLine(row.joinToString(""))
                }

                author {
                    name = "Opponent's Board"
                    iconUrl = opponent.effectiveAvatarUrl
                }

                color = Immutable.SUCCESS

                if (isStartingMessage) {
                    footer {
                        text = "The turn format is <letter><digit> (e.g. A0, B5, etc.)"
                    }
                }
            }
        }

        val isOver: Boolean
            get() = players.map { it.ownBoard.board }.any { it.flatten().none { gap -> gap == BLUE_SQUARE } }

        fun reverseTurn() = if (currentTurn.user == starter.user) {
            currentTurn = opponent
        } else {
            currentTurn = starter
        }

        interface Ship {
            val coords: Set<Coordinate>
        }

        data class SingleCellShip(val cell: Coordinate) : Ship {
            override val coords = setOf(cell)
        }

        data class DoubleCellShip(
            val firstCell: Coordinate,
            val secondCell: Coordinate,
        ) : Ship {
            override val coords = setOf(firstCell, secondCell)
        }

        data class TripleCellShip(
            val firstCell: Coordinate,
            val secondCell: Coordinate,
            val thirdCell: Coordinate,
        ) : Ship {
            override val coords = setOf(firstCell, secondCell, thirdCell)
        }

        data class QuadrupleCellShip(
            val firstCell: Coordinate,
            val secondCell: Coordinate,
            val thirdCell: Coordinate,
            val fourthCell: Coordinate,
        ) : Ship {
            override val coords = setOf(firstCell, secondCell, thirdCell, fourthCell)
        }

        @JvmInline
        value class Board(val board: Array<Array<String>>)

        @JvmInline
        value class Coordinate(val coords: Pair<Int, Int>)

        companion object {
            const val WHITE_SQUARE = "\u2B1C"
            const val BLUE_SQUARE = "\uD83D\uDFE6"
            const val RED_SQUARE = "\uD83D\uDFE5"
            const val YELLOW_SQUARE = "\uD83D\uDFE8"

            @JvmField
            val TURN_REGEX = Regex("([A-Ja-j])(\\d)")

            @JvmStatic
            fun letterToIndex(letter: Char) =
                (0..9).associateBy { 'a' + it }[letter]
        }
    }
}