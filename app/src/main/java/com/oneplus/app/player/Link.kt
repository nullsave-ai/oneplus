package com.oneplus.app.player

import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class Drm(val uuid: UUID, val licenseUrl: String?, val local: ByteArray?)

class Link(val url: String, val headers: Map<String, String>, val drm: Drm?, val subtitleUrl: String? = null)

private val Schemes = setOf("http", "https", "rtsp", "rtsps", "cinema", "wecima")

fun isAllowed(u: String): Boolean {
    val uri = runCatching { Uri.parse(u.trim()) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in Schemes && !uri.host.isNullOrBlank()
}

fun parseLink(raw: String): Link? {
    val text = raw.trim()
    val parts = Parts()
    val base: String
    if (text.contains('\n') || BlockStart.containsMatchIn(text)) {
        var url = ""
        text.lines().forEach { line ->
            val i = line.indexOf(':')
            if (i <= 0) return@forEach
            val k = line.substring(0, i).trim()
            val v = line.substring(i + 1).trim()
            if (k.equals("url", true)) url = v else parts.put(k, v)
        }
        base = url
    } else {
        base = text.substringBefore('|').trim()
        val extra = text.substringAfter('|', "")
        if (extra.isNotEmpty()) {
            val items = extra.split('&')
            var i = 0
            while (i < items.size) {
                val kv = items[i]
                val eq = kv.indexOf('=')
                if (eq > 0) {
                    val k = Uri.decode(kv.substring(0, eq))
                    var v = Uri.decode(kv.substring(eq + 1))
                    // If v is a URL with signed parameters split by '&' (e.g. sub_ar=http...&Policy=...&Signature=...)
                    if (v.startsWith("http://") || v.startsWith("https://")) {
                        while (i + 1 < items.size && (!items[i + 1].contains("=") || items[i + 1].startsWith("Policy=") || items[i + 1].startsWith("Signature=") || items[i + 1].startsWith("Key-Pair-Id=") || items[i + 1].startsWith("sign=") || items[i + 1].startsWith("t="))) {
                            i++
                            v += "&" + items[i]
                        }
                    }
                    parts.put(k, v)
                }
                i++
            }
        }
    }
    if (!isAllowed(base)) return null
    val scheme = parts.scheme ?: if (parts.kid != null && parts.key != null) "clearkey" else return Link(base, parts.headers, null, parts.subtitleUrl)
    val license = parts.license ?: "${parts.kid}:${parts.key}"
    return Link(base, parts.headers, buildDrm(scheme, license) ?: return null, parts.subtitleUrl)
}

private val BlockStart = Regex("^url\\s*:", RegexOption.IGNORE_CASE)

private class Parts {
    val headers = linkedMapOf<String, String>()
    var scheme: String? = null
    var license: String? = null
    var kid: String? = null
    var key: String? = null
    var subtitleUrl: String? = null

    fun put(name: String, value: String) {
        val k = name.trim()
        val v = value.trim().filter { it != '\r' && it != '\n' }
        if (k.isEmpty() || v.isEmpty()) return
        when (k.lowercase().replace("_", "").replace("-", "").replace(" ", "")) {
            "useragent" -> headers["User-Agent"] = v
            "referer", "referrer" -> headers["Referer"] = v
            "cookie", "cookies" -> headers["Cookie"] = v
            "drmscheme", "licensetype" -> scheme = v.lowercase()
            "drmlicense", "licensekey" -> license = v
            "clearkeyid" -> kid = v
            "clearkeyval", "clearkeyvalue" -> key = v
            "subar", "sub", "subtitle", "caption" -> subtitleUrl = v
            else -> if (k.all { it.isLetterOrDigit() || it == '-' }) headers[k] = v
        }
    }
}

private fun buildDrm(scheme: String, license: String?): Drm? = when (scheme) {
    "widevine", "com.widevine.alpha" -> license?.takeIf(::isAllowed)?.let { Drm(C.WIDEVINE_UUID, it, null) }
    "playready", "com.microsoft.playready" -> license?.takeIf(::isAllowed)?.let { Drm(C.PLAYREADY_UUID, it, null) }
    "clearkey", "org.w3.clearkey" -> when {
        license == null -> null
        isAllowed(license) -> Drm(C.CLEARKEY_UUID, license, null)
        else -> clearKeyResponse(license)?.let { Drm(C.CLEARKEY_UUID, null, it) }
    }
    else -> null
}

private fun clearKeyResponse(s: String): ByteArray? {
    val t = s.trim()
    if (t.startsWith("{")) return runCatching { JSONObject(t); t.toByteArray() }.getOrNull()
    val keys = JSONArray()
    for (pair in t.split(',')) {
        val (kid, key) = pair.split(':').takeIf { it.size == 2 } ?: return null
        keys.put(JSONObject().put("kty", "oct").put("k", b64(key) ?: return null).put("kid", b64(kid) ?: return null))
    }
    return if (keys.length() == 0) null else JSONObject().put("keys", keys).put("type", "temporary").toString().toByteArray()
}

private fun b64(hex: String): String? {
    val h = hex.trim().replace("-", "")
    if (h.length != 32 || !h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    val bytes = ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
