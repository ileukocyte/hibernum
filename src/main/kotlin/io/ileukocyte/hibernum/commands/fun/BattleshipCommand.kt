package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.ClassicTextOnlyCommand
import io.ileukocyte.hibernum.commands.CommandCategory
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.extensions.await
import io.ileukocyte.hibernum.extensions.sendWarning
import io.ileukocyte.hibernum.utils.getDominantColorByImageUrl

import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException

class BattleshipCommand : ClassicTextOnlyCommand {
    override val name = "bs"
    override val description = "n/a"
    override val category = CommandCategory.BETA

    override suspend fun invoke(event: MessageReceivedEvent, args: String?) {
        val opponent = event.message.mentions.members.firstOrNull()?.user
            ?.let { Battleship.BattleshipPlayer(it, it.openPrivateChannel().await(), event.author) }
            ?: return
        val starter = Battleship.BattleshipPlayer(event.author, event.author.openPrivateChannel().await(), opponent.user)

        val battleship = Battleship(starter, opponent)

        for (player in battleship.players) {
            try {
                val check = player.dm
                    .sendWarning("Checking if the game can be played in the DMs!") {
                        text = "This message will self-delete in 10 seconds"
                    }.await()

                check.delete().queueAfter(10, TimeUnit.SECONDS, null) {}

                player.generateOwnBoard()

                val ownBoard = buildEmbed {
                    description = "\u2B1B"

                    repeat(10) {
                        append(Emoji.fromFormatted(":regional_indicator_${'a' + it}:").name)
                    }

                    appendLine()

                    for ((index, row) in player.ownBoard.withIndex()) {
                        append("${'0' + index}\u20E3")
                        appendLine(row.joinToString(""))
                    }

                    author {
                        name = "Your Board"
                        iconUrl = player.user.effectiveAvatarUrl
                    }

                    color = getDominantColorByImageUrl(player.user.effectiveAvatarUrl)
                }

                val opponentBoard = buildEmbed {
                    description = "\u2B1B"

                    repeat(10) {
                        append(Emoji.fromFormatted(":regional_indicator_${'a' + it}:").name)
                    }

                    appendLine()

                    for ((index, row) in player.opponentBoard.withIndex()) {
                        append("${'0' + index}\u20E3")
                        appendLine(row.joinToString(""))
                    }

                    author {
                        name = "Opponent's Board"
                        iconUrl = player.opponent.effectiveAvatarUrl
                    }

                    color = getDominantColorByImageUrl(player.opponent.effectiveAvatarUrl)
                }

                player.dm.sendMessageEmbeds(ownBoard, opponentBoard).await()
            } catch (_: ErrorResponseException) {
                throw CommandException("The game session has been aborted " +
                        "since the bot is unable to talk to one of the players in their DMs!")
            }
        }
    }

    data class Battleship(val starter: BattleshipPlayer, val opponent: BattleshipPlayer) {
        val players: Set<BattleshipPlayer>
            get() = setOf(starter, opponent)

        data class BattleshipPlayer(val user: User, val dm: PrivateChannel, val opponent: User) {
            var ownBoard = Array(10) { Array(10) { WHITE_SQUARE } }
            val opponentBoard = Array(10) { Array(10) { WHITE_SQUARE } }

            val ownShips = mutableSetOf<Ship>()

            fun generateOwnBoard() {
                ownBoard = ownBoard.generateBoard()
            }

            private fun BattleshipBoard.generateBoard(): BattleshipBoard {
                val range = 0..9

                repeat(4) {
                    val coord: Pair<Int, Int>

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (this[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                this[row][column] = BLUE_SQUARE

                                coord = row to column

                                break
                            }
                        }
                    }

                    ownShips += SingleCellShip(coord)
                }

                repeat(3) {
                    val coord1: BattleshipCoordinate
                    val coord2: BattleshipCoordinate

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (this[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                val first = row.inc() to column
                                val second = row.dec() to column
                                val third = row to column.inc()
                                val fourth = row to column.dec()


                                val nearbyPossiblyUnavailableGaps = listOf(first, second, third, fourth)
                                    .associateWith { getPossiblyUnavailableGaps(it) }

                                val filtered = nearbyPossiblyUnavailableGaps.filter { (coord, npug) ->
                                    coord.first in 0..9
                                            && coord.second in 0..9
                                            && npug.none { it == BLUE_SQUARE }
                                }

                                if (filtered.isNotEmpty()) {
                                    val (nextRow, nextColumn) = filtered.keys.random()

                                    this[row][column] = BLUE_SQUARE
                                    this[nextRow][nextColumn] = BLUE_SQUARE

                                    coord1 = row to column
                                    coord2 = nextRow to nextColumn

                                    break
                                }
                            }
                        }
                    }

                    ownShips += DoubleCellShip(coord1, coord2)
                }

                repeat(2) {
                    val coord1: BattleshipCoordinate
                    val coord2: BattleshipCoordinate
                    val coord3: BattleshipCoordinate

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (this[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                val first = row.inc() to column
                                val second = row.dec() to column
                                val third = row to column.inc()
                                val fourth = row to column.dec()

                                val nearbyPossiblyUnavailableGaps = listOf(first, second, third, fourth)
                                    .associateWith { getPossiblyUnavailableGaps(it) }

                                val filtered = nearbyPossiblyUnavailableGaps.filter { (coord, npug) ->
                                    coord.first in 0..9
                                            && coord.second in 0..9
                                            && npug.none { it == BLUE_SQUARE }
                                }

                                if (filtered.isNotEmpty()) {
                                    val (nextRow, nextColumn) = filtered.keys.random()

                                    if (nextRow == row) {
                                        val nextNextColumn = if (nextColumn == column.inc()) {
                                            this[row].getOrNull(nextColumn.inc())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextColumn.inc() }
                                                ?: this[row].getOrNull(column.dec())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.dec() }
                                        } else {
                                            this[row].getOrNull(nextColumn.dec())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextColumn.dec() }
                                                ?: this[row].getOrNull(column.inc())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.inc() }
                                        }

                                        if (nextNextColumn !== null) {
                                            if (getPossiblyUnavailableGaps(row to nextNextColumn)
                                                    .none { it == BLUE_SQUARE }) {
                                                coord1 = row to column
                                                coord2 = row to nextColumn
                                                coord3 = row to nextNextColumn

                                                this[row][column] = BLUE_SQUARE
                                                this[row][nextColumn] = BLUE_SQUARE
                                                this[row][nextNextColumn] = BLUE_SQUARE

                                                break
                                            }
                                        }
                                    } else {
                                        val nextNextRow = if (nextRow == row.inc()) {
                                            getOrNull(nextRow.inc())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextRow.inc() }
                                                ?: getOrNull(row.dec())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.dec() }
                                        } else {
                                            getOrNull(nextRow.dec())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextRow.dec() }
                                                ?: getOrNull(row.inc())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.inc() }
                                        }

                                        if (nextNextRow !== null) {
                                            if (getPossiblyUnavailableGaps(nextNextRow to column)
                                                    .none { it == BLUE_SQUARE }) {
                                                coord1 = row to column
                                                coord2 = nextRow to column
                                                coord3 = nextNextRow to column

                                                this[row][column] = BLUE_SQUARE
                                                this[nextRow][column] = BLUE_SQUARE
                                                this[nextNextRow][column] = BLUE_SQUARE

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
                    val coord1: BattleshipCoordinate
                    val coord2: BattleshipCoordinate
                    val coord3: BattleshipCoordinate
                    val coord4: BattleshipCoordinate

                    while (true) {
                        val row = range.random()
                        val column = range.random()

                        if (this[row][column] != BLUE_SQUARE) {
                            if (getPossiblyUnavailableGaps(row, column).none { it == BLUE_SQUARE }) {
                                val first = row.inc() to column
                                val second = row.dec() to column
                                val third = row to column.inc()
                                val fourth = row to column.dec()

                                val nearbyPossiblyUnavailableGaps = listOf(first, second, third, fourth)
                                    .associateWith { getPossiblyUnavailableGaps(it) }

                                val filtered = nearbyPossiblyUnavailableGaps.filter { (coord, npug) ->
                                    coord.first in 0..9
                                            && coord.second in 0..9
                                            && npug.none { it == BLUE_SQUARE }
                                }

                                if (filtered.isNotEmpty()) {
                                    val (nextRow, nextColumn) = filtered.keys.random()

                                    if (nextRow == row) {
                                        val nextNextColumn = if (nextColumn == column.inc()) {
                                            this[row].getOrNull(nextColumn.inc())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextColumn.inc() }
                                                ?: this[row].getOrNull(column.dec())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.dec() }
                                        } else {
                                            this[row].getOrNull(nextColumn.dec())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextColumn.dec() }
                                                ?: this[row].getOrNull(column.inc())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.inc() }
                                        }

                                        if (nextNextColumn !== null) {
                                            if (getPossiblyUnavailableGaps(row to nextNextColumn)
                                                    .none { it == BLUE_SQUARE }) {
                                                val columns = sortedSetOf(column, nextColumn, nextNextColumn)

                                                val nextNextNextColumn = columns.min().dec()
                                                    .takeIf { it in range && this[row][it] != BLUE_SQUARE }
                                                    ?: columns.max().inc()
                                                        .takeIf { it in range && this[row][it] != BLUE_SQUARE }

                                                if (nextNextNextColumn !== null) {
                                                    if (getPossiblyUnavailableGaps(row to nextNextNextColumn)
                                                            .none { it == BLUE_SQUARE }) {
                                                        coord1 = row to column
                                                        coord2 = row to nextColumn
                                                        coord3 = row to nextNextColumn
                                                        coord4 = row to nextNextNextColumn

                                                        this[row][column] = BLUE_SQUARE
                                                        this[row][nextColumn] = BLUE_SQUARE
                                                        this[row][nextNextColumn] = BLUE_SQUARE
                                                        this[row][nextNextNextColumn] = BLUE_SQUARE

                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val nextNextRow = if (nextRow == row.inc()) {
                                            getOrNull(nextRow.inc())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextRow.inc() }
                                                ?: getOrNull(row.dec())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.dec() }
                                        } else {
                                            getOrNull(nextRow.dec())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { nextRow.dec() }
                                                ?: getOrNull(row.inc())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.inc() }
                                        }

                                        if (nextNextRow !== null) {
                                            if (getPossiblyUnavailableGaps(nextNextRow to column)
                                                    .none { it == BLUE_SQUARE }) {
                                                val rows = sortedSetOf(row, nextRow, nextNextRow)

                                                val nextNextNextRow = rows.min().dec()
                                                    .takeIf { it in range && this[it][column] != BLUE_SQUARE }
                                                    ?: rows.max().inc()
                                                        .takeIf { it in range && this[it][column] != BLUE_SQUARE }

                                                if (nextNextNextRow !== null) {
                                                    if (getPossiblyUnavailableGaps(nextNextNextRow to column)
                                                            .none { it == BLUE_SQUARE }) {
                                                        coord1 = row to column
                                                        coord2 = nextRow to column
                                                        coord3 = nextNextRow to column
                                                        coord4 = nextNextNextRow to column

                                                        this[row][column] = BLUE_SQUARE
                                                        this[nextRow][column] = BLUE_SQUARE
                                                        this[nextNextRow][column] = BLUE_SQUARE
                                                        this[nextNextNextRow][column] = BLUE_SQUARE

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

                    ownShips += QuadrupleCellShip(coord1, coord2, coord3, coord4)
                }

                return this
            }

            private fun BattleshipBoard.getPossiblyUnavailableGaps(pair: BattleshipCoordinate) =
                getPossiblyUnavailableGaps(pair.first, pair.second)

            private fun BattleshipBoard.getPossiblyUnavailableGaps(
                row: Int,
                column: Int,
            ) = listOf(
                getOrNull(row.inc())?.getOrNull(column),
                getOrNull(row.dec())?.getOrNull(column),
                getOrNull(row)?.getOrNull(column.inc()),
                getOrNull(row)?.getOrNull(column.dec()),
                getOrNull(row.inc())?.getOrNull(column.inc()),
                getOrNull(row.inc())?.getOrNull(column.dec()),
                getOrNull(row.dec())?.getOrNull(column.inc()),
                getOrNull(row.dec())?.getOrNull(column.dec()),
            )
        }

        enum class BattleshipTurn(val sign: String) {
            MISS(RED_CIRCLE),
            HIT(RED_SQUARE),
        }

        interface Ship {
            val coords: Set<BattleshipCoordinate>
        }

        data class SingleCellShip(val coord: BattleshipCoordinate) : Ship {
            override val coords = setOf(coord)
        }

        data class DoubleCellShip(
            val coord1: BattleshipCoordinate,
            val coord2: BattleshipCoordinate,
        ) : Ship {
            override val coords = setOf(coord1, coord2)
        }

        data class TripleCellShip(
            val coord1: BattleshipCoordinate,
            val coord2: BattleshipCoordinate,
            val coord3: BattleshipCoordinate,
        ) : Ship {
            override val coords = setOf(coord1, coord2, coord3)
        }

        data class QuadrupleCellShip(
            val coord1: BattleshipCoordinate,
            val coord2: BattleshipCoordinate,
            val coord3: BattleshipCoordinate,
            val coord4: BattleshipCoordinate,
        ) : Ship {
            override val coords = setOf(coord1, coord2, coord3, coord4)
        }

        companion object {
            const val WHITE_SQUARE = "\u2B1C"
            const val BLUE_SQUARE = "\uD83D\uDFE6"
            const val RED_SQUARE = "\uD83D\uDFE5"
            const val RED_CIRCLE = "\uD83D\uDD34"
        }
    }
}

private typealias BattleshipBoard = Array<Array<String>>
private typealias BattleshipCoordinate = Pair<Int, Int>