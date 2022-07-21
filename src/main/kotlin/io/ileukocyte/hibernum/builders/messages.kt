package io.ileukocyte.hibernum.builders

import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.sticker.StickerSnowflake
import net.dv8tion.jda.api.interactions.components.ActionRow

class KMessageBuilder {
    var content = ""
    var isTTS = false
    var nonce: String? = null

    val embeds = mutableListOf<MessageEmbed>()
    val actionRows = mutableListOf<ActionRow>()
    val mentions = mutableListOf<IMentionable>()
    val stickers = mutableListOf<StickerSnowflake>()

    fun clear() {
        content = ""
        isTTS = false

        embeds.clear()
        actionRows.clear()
        mentions.clear()
        stickers.clear()
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
    internal operator fun invoke() = MessageBuilder(content)
        .setTTS(isTTS)
        .setEmbeds(embeds)
        .setActionRows(actionRows)
        .setStickers(stickers)
        .mention(mentions)
        .setNonce(nonce)
        .build()
}

inline fun buildMessage(block: KMessageBuilder.() -> Unit) = KMessageBuilder().apply(block)()