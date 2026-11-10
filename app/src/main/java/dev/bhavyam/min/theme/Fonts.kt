package dev.bhavyam.min.theme

import android.content.res.AssetManager
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Token Studio's type families (the bundled ones are variable fonts from Google Fonts, OFL).
 * Each file is memory-mapped once; any weight is then a cheap instance of that same buffer, which
 * is what lets the home editor glide weight continuously.
 */
class Fonts(private val assets: AssetManager) {

    class Family(val name: String, val asset: String?)

    private val buffers = arrayOfNulls<ByteBuffer>(ALL.size)
    private val cache = HashMap<Int, Typeface>()

    fun typeface(font: Int, weight: Int): Typeface {
        val w = weight.coerceIn(1, 1000)
        val key = font * 1000 + w
        cache[key]?.let { return it }
        if (cache.size > CACHE_LIMIT) cache.clear()
        val asset = ALL[font].asset
        val typeface = if (asset == null) {
            Typeface.create(Typeface.DEFAULT, w, false)
        } else {
            val instance = Font.Builder(buffer(font, asset))
                .setWeight(w)
                .setFontVariationSettings("'wght' $w")
                .build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(instance).build())
                .setSystemFallback("sans-serif")
                .build()
        }
        cache[key] = typeface
        return typeface
    }

    private fun buffer(font: Int, asset: String): ByteBuffer =
        buffers[font] ?: assets.openFd(asset).use { fd ->
            fd.createInputStream().channel.use { it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
        }.also { buffers[font] = it }

    companion object {
        private const val CACHE_LIMIT = 96

        val ALL = listOf(
            Family("System", null),
            Family("Inter", "fonts/inter.ttf"),
            Family("Manrope", "fonts/manrope.ttf"),
            Family("Space Grotesk", "fonts/space_grotesk.ttf"),
            Family("Nunito", "fonts/nunito.ttf"),
            Family("Fraunces", "fonts/fraunces.ttf"),
            Family("JetBrains Mono", "fonts/jetbrains_mono.ttf"),
        )
    }
}
