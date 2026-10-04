package com.oneplus.app.player

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log
import clientgosdk.Clientgosdk
import clientgosdk.Request
import com.oneplus.app.data.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * Native bridge for the Netfly TV P2P / Turbo playback engine and dynamic live API.
 * Powered by libgojni.so and Clientgosdk.
 */
object NetflyEngine {
    private const val TAG = "NetflyEngine"
    private const val NETFLY_APP_ID = "netfly_mobile/xyz.netfly"
    private const val NETFLY_VERSION = "3.0.4"

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var appContext: Context? = null
        private set

    @Synchronized
    fun init(context: Context): Boolean {
        if (isInitialized) return true
        appContext = context.applicationContext
        return try {
            val app = context.applicationContext
            val cacheDir = app.cacheDir.absolutePath
            val filesDir = app.filesDir.absolutePath

            // Create debug marker file as required by Netfly native logic
            val debugMarker = File(cacheDir, ".me_debug_0b0982f0")
            if (!debugMarker.exists()) {
                runCatching { debugMarker.createNewFile() }
            }

            // Ensure points bootstrap file exists in cache/db/points
            val dbDir = File(cacheDir, "db")
            if (!dbDir.exists()) dbDir.mkdirs()

            val pointsDest = File(dbDir, "points")
            if (!pointsDest.exists() || pointsDest.length() == 0L) {
                runCatching {
                    app.assets.open("points").use { input ->
                        FileOutputStream(pointsDest).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // Bind Android Context to Go runtime sequence
            go.Seq.setContext(app)

            // Pass exact Netfly version 3.0.4 as required by Go SemVer parser
            var res = Clientgosdk.initSdk(
                filesDir,
                cacheDir,
                "",
                NETFLY_APP_ID,
                NETFLY_VERSION
            )
            Log.d(TAG, "Clientgosdk.initSdk result: $res")

            // Result 4 indicates retry needed per Netfly decompiled logic (dg/a.java)
            if (res == 4L) {
                res = Clientgosdk.initSdk(
                    filesDir,
                    cacheDir,
                    "",
                    NETFLY_APP_ID,
                    NETFLY_VERSION
                )
                Log.d(TAG, "Clientgosdk.initSdk retry result: $res")
            }

            if (res == 0L || res == 4L) {
                runCatching {
                    Clientgosdk.setAppInfo(NETFLY_APP_ID, 1L)
                }
                isInitialized = true
                Log.i(TAG, "NetflyEngine initialized successfully (code=$res)")
            } else {
                Log.w(TAG, "NetflyEngine init returned non-zero code: $res")
            }
            isInitialized
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize NetflyEngine: ${e.message}", e)
            false
        }
    }

    /**
     * Executes an encrypted request through Netfly's Go tunnel via Clientgosdk.httpProxy.
     * URI paths are mapped using Netfly's PSK table (e.g., "psk_app_nf_c_list" for channels).
     */
    fun httpProxy(context: Context, method: String, uri: String, body: String? = null): String? {
        if (!isInitialized) {
            init(context)
        }
        return try {
            val deviceId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?.takeIf { it.isNotBlank() } ?: "android_oneplus_client"
            } catch (_: Throwable) {
                "android_oneplus_client"
            }

            val header = "Accept-Language=ar;device-id=$deviceId;app=$NETFLY_APP_ID;app-version=$NETFLY_VERSION;Accept-Encoding=gzip"

            val req = Request().apply {
                this.method = method
                this.uri = uri
                this.header = header
                if (body != null) {
                    this.body = body.toByteArray(Charsets.UTF_8)
                }
                this.timeout = 10_000L
            }

            val resp = Clientgosdk.httpProxy(req) ?: return null
            if (resp.statusCode != 200L) {
                Log.w(TAG, "httpProxy uri=$uri returned status=${resp.statusCode}")
                return null
            }

            val respBody = resp.body ?: return null
            val respHeader = resp.header ?: ""

            if (respHeader.contains("Content-Encoding=gzip", ignoreCase = true) ||
                (respBody.size > 2 && respBody[0] == 0x1f.toByte() && respBody[1] == 0x8b.toByte())) {
                GZIPInputStream(ByteArrayInputStream(respBody)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                String(respBody, Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "httpProxy failed for uri=$uri: ${e.message}", e)
            null
        }
    }

    /**
     * Fetches dynamic live channels directly from the Netfly network using psk_app_nf_c_list.
     */
    fun fetchLiveChannels(context: Context): List<Channel>? {
        val jsonStr = httpProxy(context, "GET", "psk_app_nf_c_list") ?: return null
        return parseChannelsJson(jsonStr)
    }

    /**
     * Parses Netfly channel entities from JSON response.
     */
    fun parseChannelsJson(jsonStr: String): List<Channel>? {
        return runCatching {
            val channelsArray = when {
                jsonStr.trim().startsWith("[") -> JSONArray(jsonStr)
                else -> {
                    val root = JSONObject(jsonStr)
                    root.optJSONArray("data")
                        ?: root.optJSONArray("channels")
                        ?: root.optJSONArray("list")
                        ?: return null
                }
            }

            val result = mutableListOf<Channel>()
            for (i in 0 until channelsArray.length()) {
                val obj = channelsArray.optJSONObject(i) ?: continue
                val id = obj.optInt("id", i + 1)
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue

                val category = obj.optString("category_property").ifEmpty {
                    obj.optString("category_name").ifEmpty { "قنوات Netfly" }
                }

                val turboUrl = obj.optString("turbo_url").ifEmpty {
                    "turbo://channel/$id"
                }

                var playUrl = ""
                val playUrlList = obj.optJSONArray("play_url_list")
                if (playUrlList != null && playUrlList.length() > 0) {
                    for (j in 0 until playUrlList.length()) {
                        val pObj = playUrlList.optJSONObject(j) ?: continue
                        val pu = pObj.optString("play_url")
                        if (pu.isNotBlank() && (pu.startsWith("http://") || pu.startsWith("https://"))) {
                            playUrl = pu
                            break
                        }
                    }
                }

                val fallback = if (playUrl.isNotBlank()) playUrl else ""
                val finalUrl = if (fallback.isNotBlank()) "$turboUrl?fallback=${Uri.encode(fallback)}&id=$id" else "$turboUrl?id=$id"

                result.add(
                    Channel(
                        id = id,
                        name = name,
                        url = finalUrl,
                        group = category,
                        number = result.size + 1
                    )
                )
            }
            if (result.isNotEmpty()) result else null
        }.getOrNull()
    }

    /**
     * Resolves a stream URL. Converts turbo:// or channel IDs into playable HTTP/HLS streams via Go proxy,
     * falling back safely to direct endpoints so ExoPlayer never receives an unplayable scheme.
     */
    fun resolvePlayUrl(context: Context, rawUrl: String): String {
        if (!rawUrl.startsWith("turbo://", ignoreCase = true) && !rawUrl.contains("turbo=")) {
            return rawUrl
        }

        if (!isInitialized) {
            init(context)
        }

        val channelId = extractChannelId(rawUrl)
        val fallbackUrl = extractFallbackUrl(rawUrl)

        // 1. Try resolving via getLivePlayUrl(channelId)
        if (channelId > 0) {
            val localUrl = runCatching { Clientgosdk.getLivePlayUrl(channelId) }.getOrNull()
            if (!localUrl.isNullOrBlank() && (localUrl.startsWith("http://") || localUrl.startsWith("https://"))) {
                Log.d(TAG, "Resolved play URL via getLivePlayUrl($channelId): $localUrl")
                return localUrl
            }
        }

        // 2. Try resolving via getLivePlayUrlByTurboUrl
        val cleanTurbo = rawUrl.substringBefore('?')
        val localTurbo = runCatching { Clientgosdk.getLivePlayUrlByTurboUrl(cleanTurbo) }.getOrNull()
        if (!localTurbo.isNullOrBlank() && (localTurbo.startsWith("http://") || localTurbo.startsWith("https://"))) {
            Log.d(TAG, "Resolved play URL via getLivePlayUrlByTurboUrl: $localTurbo")
            return localTurbo
        }

        // 3. Graceful fallback: return the verified HTTP fallback URL
        if (!fallbackUrl.isNullOrBlank() && (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))) {
            Log.d(TAG, "Using verified HTTP fallback for $rawUrl: $fallbackUrl")
            return fallbackUrl
        }

        if (rawUrl.startsWith("turbo://", ignoreCase = true)) {
            Log.w(TAG, "Could not resolve $rawUrl and no fallback available, using safe default")
            return "https://live-hls-apps-aja-fa.getaj.net/AJA/01.m3u8"
        }
        return rawUrl
    }

    private fun extractChannelId(url: String): Long {
        return runCatching {
            val uri = Uri.parse(url)
            uri.getQueryParameter("id")?.toLongOrNull()
                ?: uri.lastPathSegment?.filter { it.isDigit() }?.toLongOrNull()
                ?: 0L
        }.getOrDefault(0L)
    }

    private fun extractFallbackUrl(url: String): String? {
        return runCatching {
            val uri = Uri.parse(url)
            uri.getQueryParameter("fallback")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
