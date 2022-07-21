package io.ileukocyte.hibernum.commands.moderation

import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.SlashOnlyCommand
import io.ileukocyte.hibernum.extensions.*

import java.util.concurrent.TimeUnit

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.EmbedType as JDAEmbedType
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import org.apache.commons.validator.routines.UrlValidator

class PruneCommand : SlashOnlyCommand {
    override val name = "prune"
    override val description = "Deletes the specified amount of messages matching the provided filters"
    override val options = setOf(
        OptionData(OptionType.INTEGER, "count", "The amount of messages to delete (up to 1000)", true)
            .setMinValue(1)
            .setMaxValue(1000),
        OptionData(OptionType.USER, "user", "The user whose messages are to (not) delete"),
        OptionData(OptionType.STRING, "filter", "Type of messages to delete or type of their content")
            .let { o ->
                val options = sortedSetOf(
                    "action components",
                    "all attachments",
                    "all embeds",
                    "bot embeds",
                    "bot messages",
                    "discord commands",
                    "gifs",
                    "human messages",
                    "image attachments",
                    "invites",
                    "links",
                    "mentions",
                    "no attachments",
                    "stickers",
                    "system messages",
                    "unpinned messages",
                    "users aside from",
                    "video attachments",
                    "webhook messages",
                )

                val choices = options.map {
                    val label = if (it == "gifs") {
                        "GIFs"
                    } else {
                        it.capitalizeAll()
                    }

                    Choice(label, it.replace(" ", "-"))
                }

                o.addChoices(choices)
            },
        OptionData(OptionType.STRING, "text-filter", "Type of message text content filter")
            .addChoice("Contains", "contains")
            .addChoice("Does Not Contain", "not")
            .addChoice("Starts With", "startswith")
            .addChoice("Ends With", "endswith")
            .addChoice("Equals To", "equals")
            .addChoice("Does Not Equal To", "doesntequal"),
        OptionData(OptionType.STRING, "text", "The content to filter messages by"),
        OptionData(OptionType.BOOLEAN, "text-case-sensitive", "Case sensitivity for text filtering (default is true)"),
        OptionData(OptionType.MENTIONABLE, "mention", "The mentions that messages to delete contain"),
    )
    override val cooldown = 5L
    override val memberPermissions = setOf(Permission.MESSAGE_MANAGE)
    override val botPermissions = setOf(Permission.MESSAGE_MANAGE)

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        val filterOption = event.getOption("filter")?.asString

        if (filterOption == "users-aside-from" && event.getOption("user")?.asUser === null) {
            throw CommandException("The \"user\" option must be initialized " +
                    "in case of the \"filter\" option set to \"Users Aside From\"!")
        }

        if (event.getOption("text-filter") !== null && event.getOption("text") === null) {
            throw CommandException("The \"text\" option must be initialized " +
                    "in case of the \"text-filter\" option being provided!")
        }

        if (event.getOption("mention") !== null && filterOption != "mentions") {
            throw CommandException("The \"filter\" option must be set to \"mentions\" " +
                    "in case of the \"mention\" option being provided!")
        }

        val botFilters = setOf("action-components", "bot-embeds", "bot-messages", "discord-commands")

        if ((filterOption in botFilters && event.getOption("user")?.asUser?.isBot == false)
                || (filterOption == "human-messages" && event.getOption("user")?.asUser?.isBot == true)) {
            throw CommandException("No messages to delete have been found!")
        }

        if (filterOption in setOf("system-messages", "webhook-messages") && event.getOption("user") !== null) {
            throw CommandException("The \"user\" option cannot be used " +
                    "if the \"filter\" option is set to \"System Messages\" or \"Webhook Messages\"!")
        }

        if (filterOption == "system-messages" && event.getOption("text-filter") !== null) {
            throw CommandException("The \"text-filter\" option cannot be used " +
                    "if the \"filter\" option is set to \"System Messages\"!")
        }

        val count = event.getOption("count")?.asLong?.toInt() ?: return

        val deferred = event.deferReply().setEphemeral(true).await()

        var counter = 0
        val filtered = mutableListOf<Message>()

        event.channel.iterableHistory.forEachAsync { message ->
            if (counter == count) {
                return@forEachAsync false
            }

            val filter = filterOption?.let {
                val mention = event.getOption("mention")?.asMentionable

                when (it) {
                    "action-components" -> message.actionRows.isNotEmpty()
                    "all-attachments" -> message.attachments.isNotEmpty()
                    "all-embeds" -> message.embeds.isNotEmpty()
                    "bot-messages" -> message.author.isBot
                    "bot-embeds" -> message.author.isBot && message.embeds.any { e -> e.type == JDAEmbedType.RICH }
                    "discord-commands" -> message.type == MessageType.SLASH_COMMAND
                            || message.type == MessageType.CONTEXT_COMMAND
                    "gifs" -> message.attachments.any { a -> a.fileExtension == "gif" }
                            || message.contentRaw.split("\\s+".toRegex())
                                .any { a -> UrlValidator.getInstance().isValid(a) && (".gif" in a || "-gif-" in a) }
                    "human-messages" -> !message.author.isBot && !message.author.isSystem
                    "image-attachments" -> message.attachments.any { a -> a.isImage }
                    "invites" -> message.invites.isNotEmpty()
                    "links" -> message.contentRaw.split(" ").any { s -> UrlValidator.getInstance().isValid(s) }
                    "mentions" -> {
                        val users = message.mentions.users
                        val roles = message.mentions.roles

                        if (mention !== null) {
                            users.any { u -> u.idLong == mention.idLong } ||
                                    roles.any { r -> r.idLong == mention.idLong }
                        } else {
                            users.isNotEmpty() || roles.isNotEmpty()
                        }
                    }
                    "no-attachments" -> message.attachments.isEmpty()
                    "stickers" -> message.stickers.isNotEmpty()
                    "system-messages" -> message.type.isSystem && message.type.canDelete()
                    "unpinned-messages" -> !message.isPinned
                    "users-aside-from" -> true
                    "video-attachments" -> message.attachments.any { a -> a.isVideo }
                    "webhook-messages" -> message.isWebhookMessage
                    else -> true
                }
            } ?: true

            val textFilter = event.getOption("text-filter")?.let {
                val isCaseSensitive = event.getOption("text-case-sensitive")?.asBoolean
                    ?: true

                val text = event.getOption("text")!!.asString.let { t ->
                    if (isCaseSensitive) {
                        t
                    } else {
                        t.lowercase()
                    }
                }

                val content = message.contentRaw.let { c ->
                    if (isCaseSensitive) {
                        c
                    } else {
                        c.lowercase()
                    }
                }

                when (it.asString) {
                    "contains" -> text in content
                    "not" -> text !in content
                    "startswith" -> content.startsWith(text)
                    "endswith" -> content.endsWith(text)
                    "equals" -> content == text
                    "doesntequal" -> content != text
                    else -> true
                }
            } ?: true

            val userFilter = event.getOption("user")?.asUser?.let {
                if (filterOption == "users-aside-from") {
                    message.author != it
                } else {
                    message.author == it
                }
            } ?: true

            val predicate = userFilter && filter && textFilter

            if (predicate) {
                counter++

                filtered += message
            }

            true
        }.await()

        filtered.takeUnless { it.isEmpty() } ?: run {
            deferred.editOriginalEmbeds(defaultEmbed("No messages to delete have been found!", EmbedType.FAILURE))
                .queue()

            return
        }

        val response = getResponse(
            filtered.size,
            event.getOption("user")?.asUser,
            event.getOption("filter")?.asString,
            event.getOption("text-filter")?.asString,
            event.getOption("mention")?.asMentionable,
        )

        event.channel.purgeMessages(filtered)

        deferred.editOriginalEmbeds(defaultEmbed(response, EmbedType.SUCCESS)).queue()

        event.channel.sendWarning("${event.user.asMention} has used the `$name` command!") {
            text = "This message will self-delete in 5 seconds"
        }.queue { it.delete().queueAfter(5, TimeUnit.SECONDS, null) {} }
    }

    private fun getResponse(
        deletedMessages: Int,
        user: User?,
        filter: String?,
        textFilter: String?,
        mention: IMentionable?,
    ) = buildString {
        append("Deleted $deletedMessages ")

        if (filter?.contains("embeds") == true) {
            append("embed ")
        }

        if (filter == "system-messages") {
            append("system ")
        }

        append("message".singularOrPlural(deletedMessages))

        filter?.let {
            append(when (it) {
                "action-components" -> " containing any action components (e.g. buttons or menus)"
                "all-attachments" -> " containing any attachments"
                "bot-messages", "bot-embeds" -> " sent by bots" + user?.asMention?.let { u -> " ($u)" }.orEmpty()
                "discord-commands" -> " that ${if (deletedMessages == 1) "is a" else "are"} " +
                        "response".singularOrPlural(deletedMessages) + " to a slash or context menu command"
                "gifs" -> " containing any GIFs"
                "human-messages" -> " sent by people" + user?.asMention?.let { u -> " ($u)" }.orEmpty()
                "image-attachments" -> " containing any images"
                "invites" -> " containing any invite links"
                "links" -> " containing any links"
                "mentions" -> " mentioning ${mention?.asMention ?: "any users or any roles"}"
                "no-attachments" -> " containing no attachments"
                "stickers" -> " containing any stickers"
                "unpinned-messages" -> " that ${if (deletedMessages == 1) "is" else "are"} not pinned"
                "video-attachments" -> " containing any videos"
                "webhook-messages" -> " sent by webhooks"
                else -> ""
            })
        }

        textFilter?.let {
            if (filter?.takeUnless { f -> f == "users-aside-from" || f == "all-embeds" } !== null) {
                append(",".takeIf { user !== null && filter != "bot-embeds" } ?: " and")
            }

            var clause = when (it) {
                "contains" -> " containing the provided text"
                "not" -> " not containing the provided text"
                "startswith" -> " starting with the provided text"
                "equals" -> " having the content equal to the provided text"
                "doesntequal" -> " not having the content equal to the provided text"
                else -> " ending with the provided text"
            }

            if (filter == "discord-commands" || filter == "unpinned-messages") {
                clause = clause
                    .replace("ing", "s".takeIf { deletedMessages == 1 }.orEmpty())
                    .replace("not\\s(\\w)".toRegex(), "does not $1".takeIf { deletedMessages == 1 }
                        ?: "do not $1")
                    .replace("(do(?:es)?\\snot\\s\\w+(?=s))(s\\s)".toRegex(), "$1 ")
                    .replace("havs", "has")
                    .replace("hav", "have")
            }

            append(clause)
        }

        user?.takeUnless { filter?.contains("bot") == true || filter?.contains("human") == true }?.let {
            if (filter?.takeUnless { f -> f == "users-aside-from" } !== null || textFilter !== null) {
                if (filter?.takeUnless { f -> f == "users-aside-from" || f == "all-embeds" } !== null
                        && textFilter !== null) {
                    append(",")
                }

                if (filter == "all-embeds") {
                    if (textFilter !== null) {
                        append(" and")
                    }
                } else {
                    append(" and")

                    if (filter == "discord-commands" || filter == "unpinned-messages") {
                        append(" is".takeIf { deletedMessages == 1 } ?: " are")
                    }
                }
            }

            append(" sent ${"not ".takeIf { filter == "users-aside-from" }.orEmpty()}by ${it.asMention}")
        }

        append("!")
    }
}