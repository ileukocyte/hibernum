package io.ileukocyte.hibernum.utils

import de.androidpit.colorthief.ColorThief

import io.ileukocyte.hibernum.Immutable
import io.ileukocyte.hibernum.extensions.await

import io.ktor.client.call.body
import io.ktor.client.request.get

import java.awt.image.BufferedImage
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.InputStream

import javax.imageio.ImageIO

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import net.dv8tion.jda.api.utils.ImageProxy

fun Color.getImageBytes(width: Int, height: Int): ByteArray {
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = bufferedImage.createGraphics()

    graphics.color = this
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()

    return ByteArrayOutputStream().use {
        ImageIO.write(bufferedImage, "png", it)

        it.toByteArray()
    }
}

suspend fun getDominantColorByImageProxy(proxy: ImageProxy) = proxy.download().await().use {
    val bufferedImage = withContext(Dispatchers.IO) { ImageIO.read(it) }
    val rgb = ColorThief.getColor(bufferedImage)

    Color(rgb[0], rgb[1], rgb[2])
}

suspend fun getDominantColorByImageUrl(url: String) = Immutable.HTTP_CLIENT.get(url)
    .body<InputStream>()
    .use {
        val bufferedImage = withContext(Dispatchers.IO) { ImageIO.read(it) }
        val rgb = ColorThief.getColor(bufferedImage)

        Color(rgb[0], rgb[1], rgb[2])
    }

fun BufferedImage.invert() {
    for (x in 0 until width) {
        for (y in 0 until height) {
            val rgba = getRGB(x, y)

            val a = rgba shr 24 and 0xff shl 24
            var r = rgba shr 16 and 0xff
            var g = rgba shr 8 and 0xff
            var b = rgba and 0xff

            r = 255 - r shl 16
            g = 255 - g shl 8
            b = 255 - b

            setRGB(x, y, a or r or g or b)
        }
    }
}