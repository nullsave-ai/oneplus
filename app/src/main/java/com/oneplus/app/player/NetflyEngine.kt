package com.oneplus.app.player

import android.content.Context
import android.util.Log
import clientgosdk.Clientgosdk
import java.io.File
import java.io.FileOutputStream

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

            val res = Clientgosdk.initSdk(
                filesDir,
                cacheDir,
                "",
                "netfly_mobile/xyz.netfly",
                "3.0.4"
            )
            Log.d(TAG, "Clientgosdk.initSdk result: $res")

            runCatching {
                Clientgosdk.setAppInfo("netfly_mobile/xyz.netfly", 1)
            }

            isInitialized = (res == 0L || res == 4L)
            isInitialized
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize NetflyEngine: ${e.message}", e)
            false
        }
    }

    /**
     * Resolves a stream URL. If it's a turbo:// URL, converts it via the local proxy.
     */
    fun resolvePlayUrl(context: Context, rawUrl: String): String {
        if (!rawUrl.startsWith("turbo://", ignoreCase = true)) {
            return rawUrl
        }
        return try {
            if (!isInitialized) {
                init(context)
            }
            val localUrl = Clientgosdk.getLivePlayUrlByTurboUrl(rawUrl)
            if (!localUrl.isNullOrBlank()) {
                Log.d(TAG, "Resolved turbo URL to local stream: $localUrl")
                localUrl
            } else {
                Log.w(TAG, "getLivePlayUrlByTurboUrl returned null or empty for: $rawUrl")
                rawUrl
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error resolving turbo URL: ${e.message}", e)
            rawUrl
        }
    }
}
