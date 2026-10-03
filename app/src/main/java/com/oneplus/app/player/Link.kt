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
 * `URL|option=value&option=value` (the convention IPTV players use). Values may be percent-encoded (use %26 for a literal &).
 *  - `User-Agent`, `Referer`, `Origin`, `Cookie`, any other header name  -> request header (media, keys and license requests)
 *  - `drmScheme`  = widevine | playready | clearkey        (aliases: license_type)
 *  - `drmLicense` = license server URL (widevine/playready/clearkey), or for clearkey: `kid:key[,kid:key...]` in hex, or a ClearKey JSON  (alias: license_key)
 * Returns null when the URL is not allowed or the DRM part is unusable, so a broken link fails visibly instead of playing wrong.
 */
fun parseLink(raw: String): Link? {
    val base = raw.substringBefore('|').trim()
    if (!isAllowed(base)) return null
    val headers = linkedMapOf<String, String>()
    var scheme: String? = null
    var license: String? = null
    raw.substringAfter('|', "").split('&').forEach { kv ->
        val i = kv.indexOf('=')
        if (i <= 0) return@forEach
        val k = Uri.decode(kv.substring(0, i)).trim()
        val v = Uri.decode(kv.substring(i + 1)).trim().filter { it != '\r' && it != '\n' } // no header injection
        when (k.lowercase().replace("_", "").replace("-", "")) {
            "useragent" -> headers["User-Agent"] = v
            "referer", "referrer" -> headers["Referer"] = v
            "drmscheme", "licensetype" -> scheme = v.lowercase()
            "drmlicense", "licensekey" -> license = v
            else -> if (k.isNotEmpty() && k.all { it.isLetterOrDigit() || it == '-' }) headers[k] = v
        }
    }
    val s = scheme ?: return Link(base, headers, null)
    return Link(base, headers, buildDrm(s, license) ?: return null)
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

/** Inverse of [parseLink]: builds `URL|User-Agent=..&Referer=..&drmScheme=..&drmLicense=..` from separate fields (values percent-encoded). */
fun buildLink(url: String, userAgent: String, referer: String, drmScheme: String?, license: String): String {
    val o = buildList {
        if (userAgent.isNotBlank()) add("User-Agent=" + Uri.encode(userAgent.trim()))
        if (referer.isNotBlank()) add("Referer=" + Uri.encode(referer.trim()))
        if (drmScheme != null) {
            add("drmScheme=$drmScheme")
            if (license.isNotBlank()) add("drmLicense=" + Uri.encode(license.trim()))
        }
    }
    return if (o.isEmpty()) url.trim() else url.trim() + "|" + o.joinToString("&")
}
