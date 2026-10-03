package com.oneplus.app.ui.system

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Gradient banding fix. 8-bit gradients over large soft areas (the ambient glow, hero banner, scrims) show visible
 * steps; a faint noise (about +-1 level) laid over them breaks the steps up. One 64x64 tile (16 KB) shared by everything,
 * drawn with a BitmapShader so it behaves the same on every API level (no AGSL path to maintain, no per-frame allocation).
 */
private val NoiseBrush: Brush by lazy {
    val n = 64
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) { if (rnd.nextBoolean()) 0x02FFFFFF else 0x02000000 } // white/black at alpha 2/255
    ShaderBrush(BitmapShader(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
}

/** Draw after a gradient, inside the same bounds. */
fun DrawScope.drawDither() = drawRect(NoiseBrush)
