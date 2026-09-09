package com.orbis.app.core.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Independently resolves native zoom/tile-size metadata from a MapLibre Style JSON
 * and HTTP(S) TileJSON sources. Unknown metadata stays unknown; it is never guessed.
 */
class ProviderMetadataResolver {
    data class ResolvedMetadata(
        val maxNativeZoom: Double?,
        val tileSize: Int?,
    )

    suspend fun resolve(provider: MapProvider): ResolvedMetadata = withContext(Dispatchers.IO) {
        val declared = provider.capabilities
        val style = fetchJson(provider.styleUri) ?: return@withContext ResolvedMetadata(
            maxNativeZoom = declared.maxNativeZoom,
            tileSize = declared.tileSize,
        )

        val sources = style.optJSONObject("sources")
            ?: return@withContext ResolvedMetadata(declared.maxNativeZoom, declared.tileSize)

        val zooms = mutableListOf<Double>()
        val tileSizes = mutableListOf<Int>()

        val keys = sources.keys()
        while (keys.hasNext()) {
            val source = sources.optJSONObject(keys.next()) ?: continue
            source.optDoubleOrNull("maxzoom")?.let(zooms::add)
            source.optIntOrNull("tileSize")?.let(tileSizes::add)

            val tileJsonUrl = source.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }
            if (tileJsonUrl != null) {
                val tileJson = fetchJson(resolveAgainst(provider.styleUri, tileJsonUrl))
                tileJson?.optDoubleOrNull("maxzoom")?.let(zooms::add)
                tileJson?.optIntOrNull("tileSize")?.let(tileSizes::add)
            }
        }

        ResolvedMetadata(
            maxNativeZoom = declared.maxNativeZoom ?: zooms.maxOrNull(),
            tileSize = declared.tileSize ?: tileSizes.maxOrNull(),
        )
    }

    private fun fetchJson(url: String): JSONObject? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 7_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveAgainst(base: String, child: String): String = runCatching {
        URI(base).resolve(child).toString()
    }.getOrDefault(child)
}

private fun JSONObject.optDoubleOrNull(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    val value = optDouble(name, Double.NaN)
    return value.takeUnless { it.isNaN() }
}

private fun JSONObject.optIntOrNull(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    val value = optInt(name, Int.MIN_VALUE)
    return value.takeUnless { it == Int.MIN_VALUE }
}
