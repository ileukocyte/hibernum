package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
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
                Button.secondary("$name-${opponent.idLong}-${event.user.idLong}-$staticProcessId-play", "Yes"),
                Button.danger("$name-${opponent.idLong}-${event.user.idLong}-deny", "No"),
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
        val id = event.componentId.removePrefix("$name-").split("-")

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
                            "$name-${starter.user.idLong}|${opponent.user.idLong}-$staticProcessId-exit",
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
                        .setEmbeds(
                            defaultEmbed(
                                "The session has been terminated by ${event.user.asMention}!",
                                EmbedType.SUCCESS,
                            )
                        ).queue(null) {
                            event.messageChannel.sendSuccess(
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
                ) ?: return

                if (!turnMessage.contentRaw.matches(Battleship.TURN_REGEX)) {
                    turnMessage.channel.sendFailure(
                        "The message does not match the valid turn format! " +
                                "Try again!"
                    ).queue()

                    awaitTurn(battleship, guildMessage, guildChannel, processId)
                } else {
                    val (columnLetter, rowChar) = turnMessage.contentRaw.toCharArray()

                    val row = rowChar.digitToInt()
                    val column = Battleship.letterToIndex(columnLetter.lowercaseChar()) ?: return

                    val turn = currentTurn.opponentBoard[row][column]

                    if (turn == Battleship.RED_SQUARE || turn == Battleship.YELLOW_CIRCLE) {
                        turnMessage.channel.sendFailure("The gap is taken! Try again!").queue()

                        awaitTurn(battleship, guildMessage, guildChannel, processId)
                    } else {
                        val opponent = battleship.players.first { it.user.idLong != currentTurn.user.idLong }

                        if (opponent.ownBoard[row][column] == Battleship.WHITE_SQUARE) {
                            opponent.ownBoard[row][column] = Battleship.YELLOW_CIRCLE
                            currentTurn.opponentBoard[row][column] = Battleship.YELLOW_CIRCLE

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
                            opponent.ownBoard[row][column] = Battleship.RED_SQUARE
                            currentTurn.opponentBoard[row][column] = Battleship.RED_SQUARE

                            if (!battleship.isOver) {
                                val hitOrDestroyed =
                                    opponent.ownShips.firstOrNull { row to column in it.coords }?.let { ship ->
                                        ship.coords.map { (r, c) -> opponent.ownBoard[r][c] }
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
                }
            } catch (_: TimeoutCancellationException) {
                for (player in battleship.players) {
                    player.dm.sendFailure("Time is out!").queue(null) {}
                }
            }
        } catch (_: ErrorResponseException) {
            if (!battleship.isOver) {
                val error = "The game session has been aborted " +
                        "since the bot is unable to DM one of the players!"

                guildMessage.editMessageComponents()
                    .setEmbeds(defaultEmbed(error, EmbedType.FAILURE))
                    .queue(null) {
                        guildChannel.sendFailure(error).queue()
                    }
            }
        }
    }

    data class Battleship(val starter: BattleshipPlayer, val opponent: BattleshipPlayer) {
        var currentTurn = starter

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
                    val firstCell: BattleshipCoordinate
                    val secondCell: BattleshipCoordinate
                    val thirdCell: BattleshipCoordinate
                    val fourthCell: BattleshipCoordinate

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
                                    val (secondCellRow, secondCellColumn) = filtered.keys.random()

                                    if (secondCellRow == row) {
                                        val thirdCellColumn = if (secondCellColumn == column.inc()) {
                                            this[row].getOrNull(secondCellColumn.inc())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellColumn.inc() }
                                                ?: this[row].getOrNull(column.dec())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.dec() }
                                        } else {
                                            this[row].getOrNull(secondCellColumn.dec())
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellColumn.dec() }
                                                ?: this[row].getOrNull(column.inc())
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { column.inc() }
                                        }

                                        if (thirdCellColumn !== null) {
                                            if (getPossiblyUnavailableGaps(row to thirdCellColumn)
                                                    .none { it == BLUE_SQUARE }) {
                                                val columns = sortedSetOf(column, secondCellColumn, thirdCellColumn)

                                                val fourthCellColumn = columns.min().dec()
                                                    .takeIf { it in range && this[row][it] != BLUE_SQUARE }
                                                    ?: columns.max().inc()
                                                        .takeIf { it in range && this[row][it] != BLUE_SQUARE }

                                                if (fourthCellColumn !== null) {
                                                    if (getPossiblyUnavailableGaps(row to fourthCellColumn)
                                                            .none { it == BLUE_SQUARE }) {
                                                        firstCell = row to column
                                                        secondCell = row to secondCellColumn
                                                        thirdCell = row to thirdCellColumn
                                                        fourthCell = row to fourthCellColumn

                                                        this[row][column] = BLUE_SQUARE
                                                        this[row][secondCellColumn] = BLUE_SQUARE
                                                        this[row][thirdCellColumn] = BLUE_SQUARE
                                                        this[row][fourthCellColumn] = BLUE_SQUARE

                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val thirdCellRow = if (secondCellRow == row.inc()) {
                                            getOrNull(secondCellRow.inc())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellRow.inc() }
                                                ?: getOrNull(row.dec())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.dec() }
                                        } else {
                                            getOrNull(secondCellRow.dec())
                                                ?.get(column)
                                                ?.takeUnless { it == BLUE_SQUARE }
                                                ?.let { secondCellRow.dec() }
                                                ?: getOrNull(row.inc())
                                                    ?.get(column)
                                                    ?.takeUnless { it == BLUE_SQUARE }
                                                    ?.let { row.inc() }
                                        }

                                        if (thirdCellRow !== null) {
                                            if (getPossiblyUnavailableGaps(thirdCellRow to column)
                                                    .none { it == BLUE_SQUARE }) {
                                                val rows = sortedSetOf(row, secondCellRow, thirdCellRow)

                                                val fourthCellRow = rows.min().dec()
                                                    .takeIf { it in range && this[it][column] != BLUE_SQUARE }
                                                    ?: rows.max().inc()
                                                        .takeIf { it in range && this[it][column] != BLUE_SQUARE }

                                                if (fourthCellRow !== null) {
                                                    if (getPossiblyUnavailableGaps(fourthCellRow to column)
                                                            .none { it == BLUE_SQUARE }) {
                                                        firstCell = row to column
                                                        secondCell = secondCellRow to column
                                                        thirdCell = thirdCellRow to column
                                                        fourthCell = fourthCellRow to column

                                                        this[row][column] = BLUE_SQUARE
                                                        this[secondCellRow][column] = BLUE_SQUARE
                                                        this[thirdCellRow][column] = BLUE_SQUARE
                                                        this[fourthCellRow][column] = BLUE_SQUARE

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

            fun getPrintableOwnBoard(
                isStartingMessage: Boolean = false,
                isOwn: Boolean = true,
            ) = buildEmbed {
                description = "\u2B1B"

                repeat(10) {
                    append(Emoji.fromFormatted(":regional_indicator_${'a' + it}:").name)
                }

                appendLine()

                for ((index, row) in ownBoard.withIndex()) {
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

                for ((index, row) in opponentBoard.withIndex()) {
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
            get() = players.map { it.ownBoard }.any { it.flatten().none { gap -> gap == BLUE_SQUARE } }

        fun reverseTurn() = if (currentTurn.user == starter.user) {
            currentTurn = opponent
        } else {
            currentTurn = starter
        }

        interface Ship {
            val coords: Set<BattleshipCoordinate>
        }

        data class SingleCellShip(val cell: BattleshipCoordinate) : Ship {
            override val coords = setOf(cell)
        }

        data class DoubleCellShip(
            val firstCell: BattleshipCoordinate,
            val secondCell: BattleshipCoordinate,
        ) : Ship {
            override val coords = setOf(firstCell, secondCell)
        }

        data class TripleCellShip(
            val firstCell: BattleshipCoordinate,
            val secondCell: BattleshipCoordinate,
            val thirdCell: BattleshipCoordinate,
        ) : Ship {
            override val coords = setOf(firstCell, secondCell, thirdCell)
        }

        data class QuadrupleCellShip(
            val firstCell: BattleshipCoordinate,
            val secondCell: BattleshipCoordinate,
            val thirdCell: BattleshipCoordinate,
            val fourthCell: BattleshipCoordinate,
        ) : Ship {
            override val coords = setOf(firstCell, secondCell, thirdCell, fourthCell)
        }

        companion object {
            const val WHITE_SQUARE = "\u2B1C"
            const val BLUE_SQUARE = "\uD83D\uDFE6"
            const val RED_SQUARE = "\uD83D\uDFE5"
            const val YELLOW_CIRCLE = "\uD83D\uDFE1"

            @JvmField
            val TURN_REGEX = Regex("([A-Ja-j])(\\d)")

            @JvmStatic
            fun letterToIndex(letter: Char) =
                (0..9).associateBy { 'a' + it }[letter]
        }
    }
}

private typealias BattleshipBoard = Array<Array<String>>
private typealias BattleshipCoordinate = Pair<Int, Int>