package com.oneplus.app.player

import android.content.Context
import android.provider.Settings
import android.util.Log
import clientgosdk.Clientgosdk
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Bridge for the Netfly P2P / Turbo playback engine powered by libgojni.so.
 */
object NetflyEngine {
    private const val TAG = "NetflyEngine"
    private var isInitialized = false

    @Synchronized
    fun init(context: Context): Boolean {
        if (isInitialized) return true
        return try {
            val cacheDir = context.cacheDir.absolutePath
            val filesDir = context.filesDir.absolutePath

            // Create marker if needed
            val debugMarker = File(cacheDir, ".me_debug_0b0982f0")
            if (!debugMarker.exists()) {
                debugMarker.createNewFile()
            }

            // Ensure points file exists in cache/db/points
            val dbDir = File(cacheDir, "db")
            if (!dbDir.exists()) dbDir.mkdirs()

            val pointsDest = File(dbDir, "points")
            if (!pointsDest.exists() || pointsDest.length() == 0L) {
                context.assets.open("points").use { input ->
                    FileOutputStream(pointsDest).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            go.Seq.setContext(context.applicationContext)

            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().replace("-", "").take(16)
            } catch (_: Throwable) {
                UUID.randomUUID().toString().replace("-", "").take(16)
            }

            var res = Clientgosdk.initSdk(
                filesDir,
                cacheDir,
                "",
                "netfly_mobile/xyz.netfly",
                androidId
            )
            Log.d(TAG, "Clientgosdk.initSdk result: $res")

            if (res == 4L) {
                // Retry as per Netfly dg/a.java logic
                res = Clientgosdk.initSdk(
                    filesDir,
                    cacheDir,
                    "",
                    "netfly_mobile/xyz.netfly",
                    androidId
                )
                Log.d(TAG, "Clientgosdk.initSdk retry result: $res")
            }

            if (res == 0L || res == 4L) {
                runCatching {
                    Clientgosdk.setAppInfo("netfly_mobile/xyz.netfly", 1)
                }
                isInitialized = true
            }
            isInitialized
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize NetflyEngine: ${e.message}", e)
            false
        }
    }

    /**
     * Resolves a stream URL. If it's a turbo:// URL, converts it via the local Netfly Go proxy.
     */
    fun resolvePlayUrl(context: Context, rawUrl: String): String {
        if (!rawUrl.startsWith("turbo://", ignoreCase = true)) {
            return rawUrl
        }
        return try {
            if (!isInitialized) {
                init(context)
            }

            // 1. Try resolving via getLivePlayUrlByTurboUrl
            var localUrl = runCatching { Clientgosdk.getLivePlayUrlByTurboUrl(rawUrl) }.getOrNull()
            if (!localUrl.isNullOrBlank()) {
                Log.d(TAG, "Resolved turbo URL via getLivePlayUrlByTurboUrl: $localUrl")
                return localUrl
            }

            // 2. Try resolving via channelId if present in URL
            val channelId = extractChannelId(rawUrl)
            if (channelId > 0) {
                localUrl = runCatching { Clientgosdk.getLivePlayUrl(channelId) }.getOrNull()
                if (!localUrl.isNullOrBlank()) {
                    Log.d(TAG, "Resolved turbo URL via getLivePlayUrl($channelId): $localUrl")
                    return localUrl
                }
            }

            Log.w(TAG, "Netfly engine could not resolve: $rawUrl")
            rawUrl
        } catch (e: Throwable) {
            Log.e(TAG, "Error resolving turbo URL: ${e.message}", e)
            rawUrl
        }
    }

    private fun extractChannelId(url: String): Long {
        return runCatching {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("id")?.toLongOrNull()
                ?: uri.lastPathSegment?.filter { it.isDigit() }?.toLongOrNull()
                ?: 0L
        }.getOrDefault(0L)
    }
}
