package io.ileukocyte.hibernum.builders

import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.components.ItemComponent
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder

import org.jetbrains.kotlin.utils.addToStdlib.applyIf

class KMessageBuilder {
    var content = ""
    var isTTS = false
    var mentionRepliedUser = true

    val actionRow = mutableListOf<ItemComponent>()
    val components = mutableListOf<LayoutComponent>()
    val embeds = mutableListOf<MessageEmbed>()
    val files = mutableListOf<FileUpload>()
    val mentions = mutableListOf<IMentionable>()

    fun clear() {
        content = ""
        isTTS = false

        actionRow.clear()
        components.clear()
        embeds.clear()
        files.clear()
        mentions.clear()
    }

    operator fun plusAssign(message: String) {
        content += message
    }

    operator fun plusAssign(mention: IMentionable) {
        content += mention.asMention
    }

    fun appendLine(line: String) {
        this += "$line\n"
    }

    inline fun embed(block: KEmbedBuilder.() -> Unit) {
        embeds += buildEmbed(block)
    }

    @PublishedApi
    internal operator fun invoke() = MessageCreateBuilder()
        .setContent(content)
        .setComponents(components)
        .setEmbeds(embeds)
        .setFiles(files)
        .setTTS(isTTS)
        .mention(mentions)
        .mentionRepliedUser(mentionRepliedUser)
        .applyIf(actionRow.isNotEmpty()) { setActionRow(actionRow) }
        .build()
}

inline fun buildMessage(block: KMessageBuilder.() -> Unit) = KMessageBuilder().apply(block)()