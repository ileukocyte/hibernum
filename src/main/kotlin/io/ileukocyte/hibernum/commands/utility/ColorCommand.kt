package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.utils.getImageBytes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json

import java.awt.Color as JColor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

class ColorCommand : Command {
    override val name = "color"
    override val description = "Sends brief information about the color provided by its hexadecimal representation"
    override val aliases = setOf("colorinfo", "color-info")
    override val usages = setOf(setOf("hex"))
    override val options = setOf(
        OptionData(OptionType.STRING, "hex", "The color's hexadecimal representation", true))
    override val cooldown = 3L

    private val jsonSerializer = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(jsonSerializer) }
    }

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = (args ?: throw NoArgumentsException)
            .takeIf { it matches HEX_REGEX }
            ?: throw CommandException("You have provided invalid arguments!")
        val info = getColorInfo(input)

        event.channel.sendMessageEmbeds(colorEmbed(info))
            .addFile(
                info.javaColor.getImageBytes(150, 150),
                "${info.hex.value.lowercase().removePrefix("#")}.png",
            ).queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val input = event.getOption("hex")?.asString?.takeIf { it matches HEX_REGEX }
            ?: throw CommandException("You have provided invalid arguments!")
        val info = getColorInfo(input)

        event.replyEmbeds(colorEmbed(info))
            .addFile(
                info.javaColor.getImageBytes(150, 150),
                "${info.hex.value.lowercase().removePrefix("#")}.png",
            ).queue()
    }

    private fun colorEmbed(colorInfo: Color) = buildEmbed {
        color = colorInfo.javaColor
        image = "attachment://${colorInfo.hex.value.lowercase().removePrefix("#")}.png"
        description = """**HEX**: ${colorInfo.hex.value.lowercase()}
            |**RGB**: ${colorInfo.rgb.red}, ${colorInfo.rgb.green}, ${colorInfo.rgb.blue}
            |**DEC**: ${colorInfo.hex.value.removePrefix("#").toInt(16)}
            |**CMYK**: ${colorInfo.cmyk.cyan}, ${colorInfo.cmyk.magenta}, ${colorInfo.cmyk.yellow}, ${colorInfo.cmyk.key}
            |**HSL**: ${colorInfo.hsl.hue}, ${colorInfo.hsl.saturation}%, ${colorInfo.hsl.lightness}%""".trimMargin()

        author { name = colorInfo.name.value }
    }

    private suspend fun getColorInfo(input: String) = client
        .get("http://www.thecolorapi.com/id?hex=${input.removePrefix("#").lowercase()}")
        .apply {
            if (status != HttpStatusCode.OK) {
                throw CommandException("The Color API is not available at the moment!")
            }
        }.body<Color>()

    @Serializable
    private data class Color(
        val name: Value,
        val hex: Value,
        val rgb: RGB,
        val hsl: HSL,
        val cmyk: CMYK,
    ) {
        @Serializable
        data class Value(val value: String)

        @Serializable
        data class RGB(
            @SerialName("r") val red: Int,
            @SerialName("g") val green: Int,
            @SerialName("b") val blue: Int,
        )

        @Serializable
        data class HSL(
            @SerialName("h") val hue: Int,
            @SerialName("s") val saturation: Int,
            @SerialName("l") val lightness: Int,
        )

        @Serializable
        data class CMYK(
            @SerialName("c") val cyan: Int = 0,
            @SerialName("m") val magenta: Int = 0,
            @SerialName("y") val yellow: Int = 0,
            @SerialName("k") val key: Int = 1,
        )

        val javaColor get() = JColor(rgb.red, rgb.green, rgb.blue)
    }

    companion object {
        @JvmField
        val HEX_REGEX = Regex("#?(([A-Fa-f\\d]){3}|([A-Fa-f\\d]){6})")
    }
}