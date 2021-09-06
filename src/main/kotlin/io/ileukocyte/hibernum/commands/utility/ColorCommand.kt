package io.ileukocyte.hibernum.commands.utility

import io.ileukocyte.hibernum.commands.CommandCategory
import io.ileukocyte.hibernum.commands.NoArgumentsException
import io.ileukocyte.hibernum.commands.TextOnlyCommand
import io.ileukocyte.hibernum.extensions.orNull
import io.ileukocyte.hibernum.extensions.toJSONObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ColorCommand : TextOnlyCommand {
    override val name = "color"
    override val description = "n/a"
    override val category = CommandCategory.BETA

    override suspend fun invoke(event: GuildMessageReceivedEvent, args: String?) {
        val input = args?.takeIf { it matches HEX_REGEX } ?: throw NoArgumentsException

    }

    /*private*/ suspend fun color(input: String): Color {
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
            response.getJSONObject("hex").getString("value"),
            rgb,
            hsl,
            cmyk
        )
    }

    data class Color(
        val name: String,
        val hexString: String,
        val rgb: RGB,
        val hsl: HSL,
        val cmyk: CMYK
    ) {
        data class RGB(val red: Int, val green: Int, val blue: Int)
        data class HSL(val hue: Int, val saturation: Int, val lightness: Int)
        data class CMYK(val cyan: Int, val magenta: Int, val yellow: Int, val key: Int)

        val image: ByteArray
            get() {
                val bufferedImage = BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB)
                val graphics = bufferedImage.createGraphics()

                graphics.color = Color(rgb.red, rgb.green, rgb.blue)
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