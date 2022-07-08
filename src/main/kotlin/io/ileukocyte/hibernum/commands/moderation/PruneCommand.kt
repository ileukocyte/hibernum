package io.ileukocyte.hibernum.commands.moderation

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*

import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.apache.commons.validator.routines.UrlValidator

class PruneCommand : SlashOnlyCommand {
    override val name = "prune"
    override val description = "Deletes messages by the amount and filters provided"
    override val fullDescription = "$description\n\n*Using filters leads to the deletion process being pretty slow!*"
    override val options = setOf(
        OptionData(OptionType.INTEGER, "count", "The amount of messages to delete (up to 1000)", true),
        OptionData(OptionType.USER, "user", "The user whose messages are to delete"),
        OptionData(OptionType.STRING, "filter", "Type of messages to delete")
            .let { o ->
                val options = setOf("attachments", "bots", "embeds", "invites", "links", "mentions")

                o.addChoices(options.map { Choice(it.capitalizeAll(), it) })
            },
        OptionData(OptionType.STRING, "text-filter", "Type of message content check")
            .addChoice("Contains", "contains")
            .addChoice("Does Not Contain", "not")
            .addChoice("Starts With", "startswith")
            .addChoice("Ends With", "endswith"),
        OptionData(OptionType.STRING, "text", "The content to filter messages by"),
    )
    override val cooldown = 5L
    override val memberPermissions = setOf(Permission.MESSAGE_MANAGE)
    override val botPermissions = setOf(Permission.MESSAGE_MANAGE)

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        if (event.getOption("text-filter") !== null && event.getOption("text") === null)
            throw CommandException("The \"text\" option must be initialized in case of the \"text-filter\" option being provided!")

        val count = event.getOption("count")?.asLong?.toInt()?.takeIf { it in 1..1000 }
            ?: throw CommandException("The provided amount is out of the required range of 1 through 1,000!")

        val amount = count.takeUnless { event.options.filterNotNull().size > 1 } ?: 1000
        val history = event.channel.iterableHistory

        val deferred = event.deferReply().setEphemeral(true).await()

        val filtered = history.takeAsync(amount + 1).await().filter { message ->
            val filter = event.getOption("filter")?.let {
                when (it.asString) {
                    "attachments" -> message.attachments.isNotEmpty()
                    "bots" -> message.author.isBot
                    "embeds" -> message.embeds.isNotEmpty()
                    "invites" -> message.invites.isNotEmpty()
                    "links" -> message.contentRaw.split(" ").any { s -> UrlValidator.getInstance().isValid(s) }
                    "mentions" -> message.mentions.roles.isNotEmpty() || message.mentions.users.isNotEmpty()
                    else -> true
                }
            } ?: true

            val textFilter = event.getOption("text-filter")?.let {
                val text = event.getOption("text")?.asString ?: return

                when (it.asString) {
                    "contains" -> text in message.contentRaw
                    "not" -> text !in message.contentRaw
                    "startswith" -> message.contentRaw.startsWith(text)
                    "endswith" -> message.contentRaw.endsWith(text)
                    else -> true
                }
            } ?: true

            message.author == (event.getOption("user")?.asUser ?: message.author) && filter && textFilter
        }.take(count).takeUnless { it.isEmpty() } ?: run {
            deferred.editOriginalEmbeds(defaultEmbed("No messages to delete have been found!", EmbedType.FAILURE)).queue()

            return
        }

        val response = getResponse(
            filtered.size,
            event.getOption("user")?.asUser,
            event.getOption("filter")?.asString,
            event.getOption("text-filter")?.asString,
        )

        event.channel.purgeMessages(filtered)

        deferred.editOriginalEmbeds(defaultEmbed(response, EmbedType.SUCCESS)).queue()

        event.channel.sendWarning("${event.user.asMention} has used the `$name` command!") {
            text = "This message will self-delete in 5 seconds"
        }.queue { it.delete().queueAfter(5, TimeUnit.SECONDS, {}) {} }
    }

    private fun getResponse(
        deletedMessages: Int,
        user: User?,
        filter: String?,
        textFilter: String?,
    ) = buildString {
        append("Deleted $deletedMessages ")

        if (filter == "embeds") append("embed ")

        append("message".singularOrPlural(deletedMessages))

        filter?.let {
            append(when (it) {
                "attachments" -> " containing any attachments"
                "bots" -> " sent by bots"
                "embeds" -> ""
                "invites" -> " containing invite links"
                "links" -> " containing any links"
                else -> " mentioning any users or any roles"
            })
        }

        textFilter?.let {
            if (filter !== null) {
                append(if (user !== null) "," else " and")
            }

            append(when (it) {
                "contains" -> " containing the provided text"
                "not" -> " not containing the provided text"
                "startswith" -> " starting with the provided text"
                else -> " ending with the provided text"
            })
        }

        user?.let {
            if (filter !== null || textFilter !== null) {
                if (filter !== null && textFilter !== null) {
                    append(",")
                }

                append (" and")
            }

            append(" sent by ${it.asMention}")
        }

        append("!")
    }
}