package io.ileukocyte.hibernum.builders

import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.components.ActionRow

class KMessageBuilder {
    var content = ""
    var isTTS = false
    var nonce: String? = null

    val embeds = mutableListOf<MessageEmbed>()
    val actionRows = mutableListOf<ActionRow>()
    val mentions = mutableListOf<IMentionable>()

    fun clear() {
        content = ""
        isTTS = false

        embeds.clear()
        actionRows.clear()
        mentions.clear()
    }

    operator fun plusAssign(message: String) {
        content += message
    }

    operator fun plusAssign(mention: IMentionable) {
        content += mention.asMention
    }

    @PublishedApi
    internal operator fun invoke() = MessageBuilder(content)
        .setTTS(isTTS)
        .setEmbeds(embeds)
        .setActionRows(actionRows)
        .mention(mentions)
        .setNonce(nonce)
        .build()
}

inline fun buildMessage(block: KMessageBuilder.() -> Unit) = KMessageBuilder().apply(block)()