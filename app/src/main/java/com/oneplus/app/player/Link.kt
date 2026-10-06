package com.oneplus.app.player

import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** DRM: [licenseUrl] is asked for a license, or [local] already holds a ClearKey response built from keys given in the link. */
class Drm(val uuid: UUID, val licenseUrl: String?, val local: ByteArray?)

/** A playable link: the media URL plus the request headers and DRM it was given. */
class Link(val url: String, val headers: Map<String, String>, val drm: Drm?)

private val Schemes = setOf("http", "https", "rtsp", "rtsps")

/** Catalogue data and pasted text are untrusted: only network schemes with a host get through (never file://, content://, data:...). */
fun isAllowed(u: String): Boolean {
    val uri = runCatching { Uri.parse(u.trim()) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in Schemes && !uri.host.isNullOrBlank()
}

/**
 * Two ways to write a link (both give the same result):
 *  1. `URL|option=value&option=value` (the convention IPTV players use). Values may be percent-encoded (use %26 for a literal &).
 *  2. One `name: value` per line (a pasted block):  `url: ...`, `referer: ...`, `userAgent: ...`, `cookies: ...`, `clearKeyId: ...`, `clearKeyVal: ...`
 * Options (names are case-insensitive, `_` and `-` are ignored):
 *  - `User-Agent`, `Referer`, `Origin`, `Cookie` / `Cookies`, any other header name  -> request header (media, keys and license requests)
 *  - `drmScheme`  = widevine | playready | clearkey        (aliases: license_type)
 *  - `drmLicense` = license server URL (widevine/playready/clearkey), or for clearkey: `kid:key[,kid:key...]` in hex, or a ClearKey JSON  (alias: license_key)
 *  - `clearKeyId` + `clearKeyVal` = one ClearKey pair in hex (no scheme needed)
 * Empty values are ignored. Returns null when the URL is not allowed or the DRM part is unusable, so a broken link fails visibly instead of playing wrong.
 */
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
        text.substringAfter('|', "").split('&').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) parts.put(Uri.decode(kv.substring(0, i)), Uri.decode(kv.substring(i + 1)))
        }
    }
    if (!isAllowed(base)) return null
    val scheme = parts.scheme ?: if (parts.kid != null && parts.key != null) "clearkey" else return Link(base, parts.headers, null)
    val license = parts.license ?: "${parts.kid}:${parts.key}"
    return Link(base, parts.headers, buildDrm(scheme, license) ?: return null)
}

private val BlockStart = Regex("^url\\s*:", RegexOption.IGNORE_CASE)

private class Parts {
    val headers = linkedMapOf<String, String>()
    var scheme: String? = null
    var license: String? = null
    var kid: String? = null
    var key: String? = null

    fun put(name: String, value: String) {
        val k = name.trim()
        val v = value.trim().filter { it != '\r' && it != '\n' } // no header injection
        if (k.isEmpty() || v.isEmpty()) return
        when (k.lowercase().replace("_", "").replace("-", "").replace(" ", "")) {
            "useragent" -> headers["User-Agent"] = v
            "referer", "referrer" -> headers["Referer"] = v
            "cookie", "cookies" -> headers["Cookie"] = v
            "drmscheme", "licensetype" -> scheme = v.lowercase()
            "drmlicense", "licensekey" -> license = v
            "clearkeyid" -> kid = v
            "clearkeyval", "clearkeyvalue" -> key = v
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

/** ClearKey license answered locally: from `kid:key,kid:key` (hex) or from a ready-made JSON. */
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
