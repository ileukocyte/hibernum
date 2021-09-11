package io.ileukocyte.hibernum.commands.moderation

import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandCategory
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.apache.commons.validator.routines.UrlValidator

class PruneCommand : SlashOnlyCommand {
    override val name = "prune"
    override val description = "Deletes messages by the amount and filters provided"
    override val category = CommandCategory.BETA
    override val options = setOf(
        OptionData(OptionType.INTEGER, "count", "The amount of messages to delete (max 1000)", true),
        OptionData(OptionType.USER, "user", "The user whose messages are to delete"),
        OptionData(OptionType.STRING, "filter", "Type of messages to delete")
            .let { o ->
                val options = setOf("embeds", "invites", "attachments", "bots", "links", "mentions")

                o.addChoices(options.map { Choice(it.capitalizeAll(), it) })
            },
        OptionData(OptionType.STRING, "content-filter", "Type of message content check")
            .addChoice("Contains", "contains")
            .addChoice("Does Not Contain", "not")
            .addChoice("Starts With", "startswith")
            .addChoice("Ends With", "endswith"),
        OptionData(OptionType.STRING, "content", "The content to filter messages by")
    )
    override val cooldown = 3L

    override suspend fun invoke(event: SlashCommandEvent) {
        val guild = event.guild ?: return
        val member = event.member ?: return

        if (Permission.MESSAGE_MANAGE !in guild.selfMember.permissions)
            throw CommandException("${event.user.name} is not able to delete messages as no required permissions were granted! Contact the server's staff!")

        if (Permission.MESSAGE_MANAGE !in member.permissions)
            throw CommandException("You do not have the required permission to manage messages!")

        if (event.getOption("content-filter") !== null && event.getOption("content") === null)
            throw CommandException("The `content` option must be initialized in case of the `content-filter` option being provided!")

        val count = event.getOption("count")?.asLong?.toInt()?.takeIf { it in 1..1000 }
            ?: throw CommandException("The provided amount is out of the required range of 1 through 1,000!")

        val deferred = event.deferReply().setEphemeral(true).await()

        val history = event.channel.iterableHistory.takeAsync(1000).await()
        val filtered = history.filter { message ->
            val filter = event.getOption("filter")?.let {
                when (it.asString) {
                    "embeds" -> message.embeds.isNotEmpty()
                    "invites" -> message.invites.isNotEmpty()
                    "attachments" -> message.attachments.isNotEmpty()
                    "bots" -> message.author.isBot
                    "links" -> message.contentRaw.split(" ").any { s -> UrlValidator.getInstance().isValid(s) }
                    "mentions" -> message.mentionedRoles.isNotEmpty() || message.mentionedUsers.isNotEmpty()
                    else -> true
                }
            } ?: true

            val contentFilter = event.getOption("content-filter")?.let {
                val content = event.getOption("content")?.asString ?: return

                when (it.asString) {
                    "contains" -> content in message.contentRaw
                    "not" -> content !in message.contentRaw
                    "startswith" -> message.contentRaw.startsWith(content)
                    "endswith" -> message.contentRaw.endsWith(content)
                    else -> true
                }
            } ?: true

            message.author == (event.getOption("user")?.asUser ?: message.author) && filter && contentFilter
        }.take(count).takeUnless { it.isEmpty() } ?: run {
            deferred.editOriginalEmbeds(defaultEmbed("No messages to delete have been found!", EmbedType.FAILURE)).queue()
            return
        }

        val response = getResponse(
            filtered.size,
            event.getOption("user")?.asUser,
            event.getOption("filter")?.asString,
            event.getOption("content-filter")?.asString
        )

        event.channel.purgeMessages(filtered)

        deferred.editOriginal(response).queue()
    }

    fun getResponse(deletedMessages: Int, user: User?, filter: String?, contentFilter: String?) = buildString {
        append("Deleted $deletedMessages ")

        if (filter == "embeds") append("embed ")

        append("message".singularOrPlural(deletedMessages))

        filter?.let {
            append(when (it) {
                "embeds" -> ""
                "invites" -> " containing invite links"
                "attachments" -> " containing any attachments"
                "bots" -> " sent by bots"
                "links" -> " containing any URLs"
                else -> " mentioning any users or any roles"
            })
        }

        contentFilter?.let {
            if (filter !== null && user !== null) append(",")
            else if (filter !== null) append(" and")

            append(when (it) {
                "contains" -> " containing the provided text"
                "not" -> " not containing the provided text"
                "startswith" -> " starting with the provided text"
                else -> " ending with the provided text"
            })
        }

        user?.let {
            if (filter !== null && contentFilter !== null) append(", and")
            else if (filter !== null || contentFilter !== null) append (" and")

            append(" sent by ${it.asMention}")
        }

        append("!")
    }
}