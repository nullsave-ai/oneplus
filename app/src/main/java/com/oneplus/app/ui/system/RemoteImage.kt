package com.oneplus.app.ui.system

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
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
import com.oneplus.app.data.local.AppDatabase
import com.oneplus.app.data.local.ImageEntity
import com.oneplus.app.player.isAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(Images.cached(url), url) {
        if (value == null && isAllowed(url)) value = Images.load(url, context)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(image, null, modifier, contentScale = contentScale)
    } else {
        fallback()
    }
}

fun trimImages(level: Int) = Images.trim(level)

fun preloadImages(urls: List<String>, context: Context) = Images.preload(urls, context.applicationContext)

fun preloadImages(urls: List<String>, cacheDir: File) {
    // Legacy convenience stub
}

private object Images {
    private val lru = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(1024 * 32)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private const val MaxBytes = 16_000_000L
    private const val DiskLimit = 160_000_000L
    private const val TouchMs = 600_000L
    private val TargetWidth = 720
    private val gate = Semaphore(32)
    private val config = Bitmap.Config.RGB_565
    private var diskBytes = -1L
    private val inflight = ConcurrentHashMap<String, Mutex>()

    fun trim(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) lru.evictAll()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) lru.trimToSize(lru.size() / 2)
    }

    fun cached(url: String): Bitmap? = lru.get(url)

    suspend fun load(url: String, context: Context): Bitmap? {
        val mem = lru.get(url)
        if (mem != null) return mem

        val db = AppDatabase.get(context)
        // 1. Fast check in Room Database
        val record = db.images().find(url)
        if (record != null) {
            val file = File(record.filePath)
            if (file.exists() && file.length() > 0) {
                val decoded = withContext(Dispatchers.IO) { decodeFile(file, url) }
                if (decoded != null) {
                    val now = System.currentTimeMillis()
                    if (now - record.updatedAt > TouchMs) {
                        withContext(Dispatchers.IO) { db.images().touch(url, now) }
                    }
                    return decoded
                }
            }
        }

        // 2. Fetch, decode and store in Room
        val lock = inflight.getOrPut(url) { Mutex() }
        return try {
            lock.withLock {
                lru.get(url) ?: gate.withPermit {
                    withContext(Dispatchers.IO) { decodeAndStore(url, context.cacheDir, db) }
                }
            }
        } finally { inflight.remove(url, lock) }
    }

    fun preload(urls: List<String>, context: Context) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(context)
            val dir = File(context.cacheDir, "img").apply { mkdirs() }
            for (u in urls.take(60)) {
                if (u.isBlank() || !isAllowed(u) || lru.get(u) != null) continue
                val existing = db.images().find(u)
                if (existing != null && File(existing.filePath).exists()) continue
                val f = File(dir, name(u))
                if (!f.exists() || f.length() == 0L) {
                    gate.withPermit {
                        if (fetch(u, f)) {
                            db.images().insert(ImageEntity(u, f.absolutePath, f.length()))
                        }
                    }
                }
            }
        }
    }

    private fun decodeFile(file: File, url: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TargetWidth) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = config })
            ?.also { lru.put(url, it) }
    }.getOrNull()

    private suspend fun decodeAndStore(url: String, cache: File, db: AppDatabase): Bitmap? =
        lru.get(url) ?: runCatching {
            val dir = File(cache, "img").apply { mkdirs() }
            val file = File(dir, name(url))
            if (file.exists() && file.length() > 0) {
                val now = System.currentTimeMillis()
                if (now - file.lastModified() > TouchMs) file.setLastModified(now)
            } else if (fetch(url, file)) {
                account(dir, file.length())
            } else return@runCatching null

            val bmp = decodeFile(file, url)
            if (bmp != null) {
                db.images().insert(ImageEntity(url, file.absolutePath, file.length()))
                bmp
            } else {
                file.delete()
                null
            }
        }.getOrNull()

    private fun fetch(url: String, file: File): Boolean {
        // Fast path: Try ultra-compressed edge CDN WebP for instant load (19 KB instead of 2.5 MB)
        val optimized = if (url.contains("wsrv.nl") || !url.startsWith("http")) null
            else "https://wsrv.nl/?url=" + URLEncoder.encode(url, "UTF-8") + "&w=480&q=82&output=webp"
        if (optimized != null && downloadHttp(optimized, file)) return true
        return downloadHttp(url, file)
    }

    private fun downloadHttp(url: String, file: File): Boolean {
        val tmp = File(file.path + ".tmp")
        return runCatching {
            var target = url
            for (hop in 0..4) {
                val c = URL(target).openConnection() as HttpURLConnection
                c.connectTimeout = 3_500
                c.readTimeout = 5_000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
                c.setRequestProperty("Accept", "image/*,*/*;q=0.8")
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
    private fun account(dir: File, added: Long) {
        if (diskBytes < 0) diskBytes = dir.listFiles().orEmpty().sumOf { it.length() }
        else diskBytes += added
        if (diskBytes <= DiskLimit) return
        var total = diskBytes
        for (f in dir.listFiles().orEmpty().sortedBy { it.lastModified() }) {
            if (total <= DiskLimit * 4 / 5) break
            total -= f.length()
            f.delete()
        }
        diskBytes = total
    }
}
