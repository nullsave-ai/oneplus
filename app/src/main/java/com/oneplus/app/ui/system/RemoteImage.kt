package com.oneplus.app.ui.system

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.oneplus.app.player.isAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(Images.cached(url), url) {
        if (value == null && isAllowed(url)) value = Images.load(url)
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = contentScale) }
}

private object Images {
    private val lru = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private const val MaxBytes = 4_000_000
    private const val TargetWidth = 720

    fun cached(url: String): Bitmap? = lru.get(url)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        lru.get(url) ?: runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36")
            c.instanceFollowRedirects = true
            val bytes = try {
                c.connectTimeout = 8_000; c.readTimeout = 10_000
                if (c.responseCode !in 200..299) return@runCatching null
                c.inputStream.use { s ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        if (out.size() + n > MaxBytes) return@runCatching null
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } finally { c.disconnect() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TargetWidth) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { lru.put(url, it) }
    }
}
