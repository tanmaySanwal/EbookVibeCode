package com.ebookvibe.tvreader.ui.reader

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Colour treatments for the page bitmap, applied at draw time (no re-render needed to switch).
 *
 * Dark mode is a "smart invert": inverting RGB turns white paper black and black text white, but
 * also turns blue skies orange and faces green. Rotating hue by 180° afterwards puts colours back
 * on their original hue while keeping the inverted lightness, so photos and charts look natural.
 * The result is then compressed slightly (off-white text on near-black) to cut projector glare.
 */
object PageColors {

    private val invert = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Hue rotation by 180° using the standard luminance-preserving matrix (cos = -1, sin = 0). */
    private val hueRotate180: FloatArray = run {
        val lr = 0.213f
        val lg = 0.715f
        val lb = 0.072f
        val cos = -1f
        floatArrayOf(
            lr + cos * (1 - lr), lg + cos * -lg, lb + cos * -lb, 0f, 0f,
            lr + cos * -lr, lg + cos * (1 - lg), lb + cos * -lb, 0f, 0f,
            lr + cos * -lr, lg + cos * -lg, lb + cos * (1 - lb), 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Maps 0..255 to [low]..[high] per channel. */
    private fun range(lowR: Float, lowG: Float, lowB: Float, highR: Float, highG: Float, highB: Float) =
        floatArrayOf(
            (highR - lowR) / 255f, 0f, 0f, 0f, lowR,
            0f, (highG - lowG) / 255f, 0f, 0f, lowG,
            0f, 0f, (highB - lowB) / 255f, 0f, lowB,
            0f, 0f, 0f, 1f, 0f,
        )

    /** Returns the matrix for "apply [first], then [then]" (both 4x5, row-major). */
    private fun concat(then: FloatArray, first: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = if (col == 4) then[row * 5 + 4] else 0f
                for (k in 0 until 4) v += then[row * 5 + k] * first[k * 5 + col]
                out[row * 5 + col] = v
            }
        }
        return out
    }

    // Light text (#E0E0E0-ish) on very dark grey (#141414): easier on the eyes than pure black/white.
    private val darkMatrix = concat(range(20f, 20f, 22f, 224f, 224f, 220f), concat(hueRotate180, invert))

    // Warm paper (#F4E7CC) with dark brown ink (#3A2C1C); pulls blue out of the projector's white.
    private val sepiaMatrix: FloatArray = run {
        // Light desaturation first so colour images harmonise with the tint.
        val s = 0.75f
        val lr = 0.213f * (1 - s)
        val lg = 0.715f * (1 - s)
        val lb = 0.072f * (1 - s)
        val desat = floatArrayOf(
            lr + s, lg, lb, 0f, 0f,
            lr, lg + s, lb, 0f, 0f,
            lr, lg, lb + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        concat(range(58f, 44f, 28f, 244f, 231f, 204f), desat)
    }

    private val dark = ColorFilter.colorMatrix(ColorMatrix(darkMatrix))
    private val sepia = ColorFilter.colorMatrix(ColorMatrix(sepiaMatrix))

    fun filterFor(theme: PageTheme): ColorFilter? = when (theme) {
        PageTheme.LIGHT -> null
        PageTheme.DARK -> dark
        PageTheme.SEPIA -> sepia
    }
}
