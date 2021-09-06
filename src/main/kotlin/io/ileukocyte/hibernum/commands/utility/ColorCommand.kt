package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.builders.buildEmbed
import io.ileukocyte.hibernum.commands.Command
import io.ileukocyte.hibernum.commands.CommandException
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.extensions.orNull
import io.ileukocyte.hibernum.extensions.toJSONObject

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ColorCommand : Command {
    override val name = "color"
    override val description = "Sends brief information about the color provided by its hexadecimal representation"
    override val aliases = setOf("colorinfo", "color-info")
    override val usages = setOf("hex")
    override val options = setOf(
        OptionData(OptionType.STRING, "hex", "The color's hexadecimal representation", true)
    )
    override val cooldown = 3L

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = (args ?: throw NoArgumentsException)
            .takeIf { it matches HEX_REGEX }
            ?: throw CommandException("You have provided invalid arguments!")
        val info = getColorInfo(input)

        event.channel.sendMessageEmbeds(colorEmbed(info))
            .addFile(info.image, "${info.hexString.removePrefix("#")}.png")
            .queue()
    }

    override suspend fun invoke(event: SlashCommandEvent) {
        val input = event.getOption("hex")?.asString?.takeIf { it matches HEX_REGEX }
            ?: throw CommandException("You have provided invalid arguments!")
        val info = getColorInfo(input)

        event.replyEmbeds(colorEmbed(info))
            .addFile(info.image, "${info.hexString.removePrefix("#")}.png")
            .queue()
    }

    private fun colorEmbed(colorInfo: Color) = buildEmbed {
        color = colorInfo.javaColor
        image = "attachment://${colorInfo.hexString.removePrefix("#")}.png"
        description = """**HEX**: ${colorInfo.hexString}
            |**RGB**: ${colorInfo.rgb.red}, ${colorInfo.rgb.green}, ${colorInfo.rgb.blue}
            |**DEC**: ${colorInfo.hexString.removePrefix("#").toInt(16)}
            |**CMYK**: ${colorInfo.cmyk.cyan}, ${colorInfo.cmyk.magenta}, ${colorInfo.cmyk.yellow}, ${colorInfo.cmyk.key}
            |**HSL**: ${colorInfo.hsl.hue}, ${colorInfo.hsl.saturation}%, ${colorInfo.hsl.lightness}%""".trimMargin()

        author { name = colorInfo.name }
    }

    private suspend fun getColorInfo(input: String): Color {
        val client = HttpClient(CIO)
        val api = "http://www.thecolorapi.com/id?hex=${input.removePrefix("#").lowercase()}"
        val response = client.get<String>(api).toJSONObject()

        val rgb = response.getJSONObject("rgb").let {
            Color.RGB(
                red = it.orNull("r") ?: 0,
                green = it.orNull("g") ?: 0,
                blue = it.orNull("b") ?: 0
            )
        }
        val cmyk = response.getJSONObject("cmyk").let {
            Color.CMYK(
                cyan = it.orNull("c") ?: 0,
                magenta = it.orNull("m") ?: 0,
                yellow = it.orNull("y") ?: 0,
                key = it.orNull("k") ?: 1
            )
        }
        val hsl = response.getJSONObject("hsl").let {
            Color.HSL(
                hue = it.orNull("h") ?: 0,
                saturation = it.orNull("s") ?: 0,
                lightness = it.orNull("l") ?: 0
            )
        }

        return Color(
            response.getJSONObject("name").getString("value"),
            response.getJSONObject("hex").getString("value").lowercase(),
            rgb,
            hsl,
            cmyk
        )
    }

    private data class Color(
        val name: String,
        val hexString: String,
        val rgb: RGB,
        val hsl: HSL,
        val cmyk: CMYK
    ) {
        data class RGB(val red: Int, val green: Int, val blue: Int)
        data class HSL(val hue: Int, val saturation: Int, val lightness: Int)
        data class CMYK(val cyan: Int, val magenta: Int, val yellow: Int, val key: Int)

        val javaColor get() = Color(rgb.red, rgb.green, rgb.blue)
        val image: ByteArray
            get() {
                val bufferedImage = BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB)
                val graphics = bufferedImage.createGraphics()

                graphics.color = javaColor
                graphics.fillRect(0, 0, 150, 150)
                graphics.dispose()

                return ByteArrayOutputStream().use {
                    ImageIO.write(bufferedImage, "png", it)

                    it.toByteArray()
                }
            }
    }

    companion object {
        val HEX_REGEX = Regex("#?(([A-Fa-f\\d]){3}|([A-Fa-f\\d]){6})")
    }
}