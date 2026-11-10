package com.bhavyam.min.theme

import android.content.res.AssetManager
import android.graphics.Typeface
import android.util.SparseArray

/**
 * Bundled variable fonts. Each family is one file with a real `wght` axis, so the weight
 * token glides continuously instead of jumping between static cuts. Typefaces are built
 * once per (family, weight step) and reused, so dragging the weight never allocates twice.
 */
class Fonts(private val assets: AssetManager) {

    class Family(val name: String, val file: String)

    private val cache = SparseArray<Typeface>()

    fun get(family: Int, weight: Float): Typeface {
        val w = ((weight / WEIGHT_STEP).toInt() * WEIGHT_STEP).coerceIn(1, 1000)
        val key = family * 1000 + w
        cache[key]?.let { return it }
        val tf = Typeface.Builder(assets, FAMILIES[family].file)
            .setFontVariationSettings("'wght' $w")
            .setWeight(w)
            .build()
        cache.put(key, tf)
        return tf
    }

    companion object {
        private const val WEIGHT_STEP = 10

        val FAMILIES = listOf(
            Family("Inter", "fonts/inter.ttf"),
            Family("Manrope", "fonts/manrope.ttf"),
            Family("Space Grotesk", "fonts/space_grotesk.ttf"),
            Family("Nunito", "fonts/nunito.ttf"),
            Family("Fraunces", "fonts/fraunces.ttf"),
            Family("JetBrains Mono", "fonts/jetbrains_mono.ttf"),
        )
    }
}
