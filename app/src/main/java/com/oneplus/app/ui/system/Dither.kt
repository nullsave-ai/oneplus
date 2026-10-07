package com.oneplus.app.ui.system

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

private val NoiseBrush: Brush by lazy {
    val n = 64
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) { if (rnd.nextBoolean()) 0x02FFFFFF else 0x02000000 }
    ShaderBrush(BitmapShader(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
}

fun DrawScope.drawDither() = drawRect(NoiseBrush)
