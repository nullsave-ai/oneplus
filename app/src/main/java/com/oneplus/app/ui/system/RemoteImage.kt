package com.oneplus.app.ui.system

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.os.Build
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.oneplus.app.App
import com.oneplus.app.data.Img
import com.oneplus.app.data.Store
import com.oneplus.app.player.isAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val app = LocalContext.current.applicationContext as App
    val bitmap by produceState<Bitmap?>(Images.cached(url), url) {
        if (value == null && isAllowed(url)) value = Images.load(url, app)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) Image(image, null, modifier, contentScale = contentScale)
}

fun trimImages(level: Int) = Images.trim(level)

private object Images {
    private val lru = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private const val MaxBytes = 16_000_000L
    private const val DiskLimit = 120_000_000L
    private const val TouchMs = 600_000L
    private val TargetWidth = if (WeakDevice) 480 else 720
    private val gate = Semaphore(if (WeakDevice) 3 else 6)
    private val config = when {
        Build.VERSION.SDK_INT >= 26 -> Bitmap.Config.HARDWARE
        WeakDevice -> Bitmap.Config.RGB_565
        else -> Bitmap.Config.ARGB_8888
    }
    private var swept = false
    private val inflight = ConcurrentHashMap<String, Mutex>()

    fun trim(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) lru.evictAll()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) lru.trimToSize(lru.size() / 2)
    }

    fun cached(url: String): Bitmap? = lru.get(url)

    suspend fun load(url: String, app: App): Bitmap? {
        val lock = inflight.getOrPut(url) { Mutex() }
        return try {
            lock.withLock { lru.get(url) ?: gate.withPermit { withContext(Dispatchers.IO) { decode(url, app) } } }
        } finally { inflight.remove(url, lock) }
    }

    private fun decode(url: String, app: App): Bitmap? =
            lru.get(url) ?: runCatching {
                val store = app.db.store()
                if (!swept) { swept = true; File(app.cacheDir, "img").deleteRecursively() }
                val dir = File(app.cacheDir, "i").apply { mkdirs() }
                val file = File(dir, name(url))
                val now = System.currentTimeMillis()
                val row = store.img(url)
                if (row != null && file.exists()) {
                    if (now - row.used > TouchMs) store.touch(url, now)
                } else if ((0..1).any { fetch(url, file) }) {
                    store.put(Img(url, file.length(), now))
                    trim(store, dir)
                } else return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= TargetWidth) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = config })
                    ?: run { file.delete(); null }
            }.getOrNull()?.also { lru.put(url, it) }

    private fun fetch(url: String, file: File): Boolean {
        val tmp = File(file.path + ".tmp")
        return runCatching {
            var target = url
            for (hop in 0..5) {
                val c = URL(target).openConnection() as HttpURLConnection
                c.connectTimeout = 8_000; c.readTimeout = 12_000; c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
                c.setRequestProperty("Accept", "image/*")
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location")
                    c.disconnect()
                    if (next.isNullOrBlank()) return@runCatching false
                    target = URL(URL(target), next).toString()
                    continue
                }
                if (code != 200) { c.disconnect(); return@runCatching false }
                val ok = try {
                    c.inputStream.use { s -> tmp.outputStream().use { o -> s.copyTo(o) } }
                    tmp.length() in 1..MaxBytes
                } finally { c.disconnect() }
                return@runCatching ok && tmp.renameTo(file)
            }
            false
        }.getOrDefault(false).also { if (!it) tmp.delete() }
    }

    private fun name(url: String) = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    @Synchronized
    private fun trim(store: Store, dir: File) {
        var total = store.total()
        if (total <= DiskLimit) return
        while (total > DiskLimit * 4 / 5) {
            val old = store.oldest().ifEmpty { return }
            old.forEach { File(dir, name(it.url)).delete(); total -= it.bytes }
            store.drop(old.map { it.url })
        }
    }
}
