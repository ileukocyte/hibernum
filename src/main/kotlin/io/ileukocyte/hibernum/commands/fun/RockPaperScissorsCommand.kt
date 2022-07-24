package io.ileukocyte.hibernum.commands.`fun`

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button

class RockPaperScissorsCommand : SlashOnlyCommand {
    override val name = "rps"
    override val description = "Starts the rock-paper-scissors game against the specified user"
    override val options = setOf(
        OptionData(
            OptionType.USER,
            "opponent",
            "The user to play rock paper scissors against",
            true,
        )
    )

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val opponent = event.getOption("opponent")?.asUser
            ?.takeUnless { it.isBot || it.idLong == event.user.idLong }
            ?: throw CommandException("You cannot play against the specified user!")

        event.replyConfirmation("Do you want to play rock paper scissors against ${event.user.asMention}?")
            .setContent(opponent.asMention)
            .addActionRow(
                Button.secondary("$name-${opponent.idLong}-${event.user.idLong}-play", "Yes"),
                Button.danger("$name-${opponent.idLong}-${event.user.idLong}-exit", "No"),
            ).queue()
    }

    override suspend fun invoke(event: ButtonInteractionEvent) {
        val id = event.componentId.removePrefix("$name-").split("-")

        if (event.user.id in id.first().split("|")) {
            when (val last = id.last()) {
                "play" -> {
                    val deferred = event.editComponents().await()

                    try {
                        val opponent = event.user
                        val starter = event.guild?.retrieveMemberById(id[1])?.await()?.user
                            ?: return

                        val embed = buildEmbed {
                            description = "It is ${starter.asMention}'s turn!"
                            color = Immutable.SUCCESS

                            author {
                                name = "Round #1"
                                iconUrl = starter.effectiveAvatarUrl
                            }
                        }

                        val buttons = setOf(
                            Button.secondary(
                                "$name-${starter.idLong}-${opponent.idLong}-1-rock",
                                Emoji.fromUnicode(RPSTurn.ROCK.sign),
                            ),
                            Button.secondary(
                                "$name-${starter.idLong}-${opponent.idLong}-1-paper",
                                Emoji.fromUnicode(RPSTurn.PAPER.sign),
                            ),
                            Button.secondary(
                                "$name-${starter.idLong}-${opponent.idLong}-1-scissors",
                                Emoji.fromUnicode(RPSTurn.SCISSORS.sign),
                            ),
                            Button.danger("$name-${starter.idLong}|${opponent.idLong}-stop", "Exit"),
                        )

                        try {
                            deferred.editOriginalEmbeds(embed)
                                .setContent(EmbedBuilder.ZERO_WIDTH_SPACE)
                                .setActionRow(buttons)
                                .await()
                        } catch (_: ErrorResponseException) {
                            event.channel.sendMessageEmbeds(embed)
                                .setActionRow(buttons)
                                .await()
                        }
                    } catch (_: ErrorResponseException) {
                        deferred.editOriginalEmbeds(
                            defaultEmbed(
                                "The user who initiated the game is no longer available for the bot!",
                                EmbedType.FAILURE,
                            )
                        ).queue(null) {
                            event.messageChannel
                                .sendFailure("The user who initiated the game is no longer available for the bot!")
                                .queue()
                        }
                    }
                }
                "rock", "paper", "scissors" -> {
                    val deferred = event.deferEdit().await()

                    try {
                        val opponent = event.guild?.retrieveMemberById(id[1])?.await()?.user
                            ?: return

                        val roundNumber = id[2].toIntOrNull() ?: return

                        val starter = event.user
                        val starterTurn = RPSTurn.values()
                            .first { it.name.lowercase() == last }
                            .name
                            .lowercase()

                        val embed = buildEmbed {
                            description = "It is ${opponent.asMention}'s turn!"
                            color = Immutable.SUCCESS

                            author {
                                name = "Round #$roundNumber"
                                iconUrl = opponent.effectiveAvatarUrl
                            }
                        }

                        val buttons = setOf(
                            Button.secondary(
                                "$name-${opponent.idLong}-${starter.idLong}-$roundNumber-$starterTurn-nextrock",
                                Emoji.fromUnicode(RPSTurn.ROCK.sign),
                            ),
                            Button.secondary(
                                "$name-${opponent.idLong}-${starter.idLong}-$roundNumber-$starterTurn-nextpaper",
                                Emoji.fromUnicode(RPSTurn.PAPER.sign),
                            ),
                            Button.secondary(
                                "$name-${opponent.idLong}-${starter.idLong}-$roundNumber-$starterTurn-nextscissors",
                                Emoji.fromUnicode(RPSTurn.SCISSORS.sign),
                            ),
                            Button.danger("$name-${starter.idLong}|${opponent.idLong}-stop", "Exit"),
                        )

                        try {
                            deferred.editOriginalEmbeds(embed)
                                .setActionRow(buttons)
                                .await()
                        } catch (_: ErrorResponseException) {
                            event.channel.sendMessageEmbeds(embed)
                                .setActionRow(buttons)
                                .await()
                        }
                    } catch (_: ErrorResponseException) {
                        deferred.editOriginalEmbeds(
                            defaultEmbed(
                                "One of the players is no longer available for the bot!",
                                EmbedType.FAILURE,
                            )
                        ).queue(null) {
                            event.messageChannel
                                .sendFailure("One of the players is no longer available for the bot!")
                                .queue()
                        }
                    }
                }
                "nextrock", "nextpaper", "nextscissors" -> {
                    val deferred = event.deferEdit().await()

                    try {
                        val starter = event.guild?.retrieveMemberById(id[1])?.await()?.user
                            ?: return
                        val starterTurn = RPSTurn.values()
                            .first { it.name.lowercase() == id[3] }

                        val opponent = event.user
                        val opponentTurn = RPSTurn.values()
                            .first { it.name.lowercase() == last.removePrefix("next") }

                        var roundNumber = id[2].toIntOrNull() ?: return

                        val continuation = suspend {
                            roundNumber++

                            val embed = buildEmbed {
                                description = "It is ${starter.asMention}'s turn!"
                                color = Immutable.SUCCESS

                                author {
                                    name = "Round #$roundNumber"
                                    iconUrl = starter.effectiveAvatarUrl
                                }
                            }

                            val buttons = setOf(
                                Button.secondary(
                                    "$name-${starter.idLong}-${opponent.idLong}-$roundNumber-rock",
                                    Emoji.fromUnicode(RPSTurn.ROCK.sign),
                                ),
                                Button.secondary(
                                    "$name-${starter.idLong}-${opponent.idLong}-$roundNumber-paper",
                                    Emoji.fromUnicode(RPSTurn.PAPER.sign),
                                ),
                                Button.secondary(
                                    "$name-${starter.idLong}-${opponent.idLong}-$roundNumber-scissors",
                                    Emoji.fromUnicode(RPSTurn.SCISSORS.sign),
                                ),
                                Button.danger("$name-${starter.idLong}|${opponent.idLong}-stop", "Exit"),
                            )

                            try {
                                deferred.editOriginalEmbeds(embed)
                                    .setActionRow(buttons)
                                    .await()
                            } catch (_: ErrorResponseException) {
                                event.channel.sendMessageEmbeds(embed)
                                    .setActionRow(buttons)
                                    .await()
                            }
                        }

                        val (winner, winnerTurn) = when (starterTurn) {
                            RPSTurn.ROCK -> when (opponentTurn) {
                                RPSTurn.PAPER -> opponent to opponentTurn
                                RPSTurn.SCISSORS -> starter to starterTurn
                                else -> {
                                    continuation()

                                    return
                                }
                            }
                            RPSTurn.PAPER -> when (opponentTurn) {
                                RPSTurn.ROCK -> starter to starterTurn
                                RPSTurn.SCISSORS -> opponent to opponentTurn
                                else -> {
                                    continuation()

                                    return
                                }
                            }
                            RPSTurn.SCISSORS -> when (opponentTurn) {
                                RPSTurn.ROCK -> opponent to opponentTurn
                                RPSTurn.PAPER -> starter to starterTurn
                                else -> {
                                    continuation()

                                    return
                                }
                            }
                        }

                        val embed = buildEmbed {
                            description = "${winner.asMention} wins!"
                            color = Immutable.SUCCESS

                            author {
                                name = "Congratulations!"
                                iconUrl = winner.effectiveAvatarUrl
                            }

                            field {
                                title = "Winner's Turn"
                                description = winnerTurn.let {
                                    "${it.sign} ${it.name.capitalizeAll()}"
                                }
                            }

                            field {
                                val loserTurn = if (winnerTurn == starterTurn) {
                                    opponentTurn
                                } else {
                                    starterTurn
                                }

                                title = "Loser's Turn"
                                description = loserTurn.let {
                                    "${it.sign} ${it.name.capitalizeAll()}"
                                }
                            }
                        }

                        try {
                            deferred.editOriginalComponents()
                                .setEmbeds(embed)
                                .await()
                        } catch (_: ErrorResponseException) {
                            event.messageChannel.sendMessageEmbeds(embed).await()
                        }
                    } catch (_: ErrorResponseException) {
                        deferred.editOriginalEmbeds(
                            defaultEmbed(
                                "One of the players is no longer available for the bot!",
                                EmbedType.FAILURE,
                            )
                        ).queue(null) {
                            event.messageChannel
                                .sendFailure("One of the players is no longer available for the bot!")
                                .queue()
                        }
                    }
                }
                "exit" -> {
                    val starter = try {
                        event.guild?.retrieveMemberById(id[1])?.await()?.user
                    } catch (_: ErrorResponseException) {
                        null
                    }

                    val embed = defaultEmbed(
                        "${event.user.asMention} has denied your rock-paper-scissors invitation!",
                        EmbedType.FAILURE,
                    )

                    event.editComponents()
                        .setEmbeds(embed)
                        .setContent(starter?.asMention.orEmpty())
                        .queue(null) {
                            event.messageChannel.sendMessageEmbeds(embed)
                                .content(starter?.asMention.orEmpty()).queue()
                        }
                }
                "stop" -> event.editComponents().setEmbeds(
                    defaultEmbed(
                        "The game session has been terminated by ${event.user.asMention}!",
                        EmbedType.SUCCESS,
                    )
                ).queue(null) {
                    event.messageChannel
                        .sendSuccess("The game session has been terminated by ${event.user.asMention}!")
                        .queue()
                }
            }
        } else {
            throw CommandException("You did not invoke the initial command!")
        }
    }

    enum class RPSTurn(val sign: String) {
        ROCK("\u270A"),
        PAPER("\u270B"),
        SCISSORS("\u270C"),
    }
}