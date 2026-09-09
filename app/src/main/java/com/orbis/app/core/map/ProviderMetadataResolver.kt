package com.orbis.app.core.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Independently resolves zoom/tile metadata from a MapLibre Style JSON and its
 * TileJSON sources. Unknown values stay unknown; Orbis never invents native detail.
 */
class ProviderMetadataResolver {
    data class ResolvedMetadata(
        val minZoom: Double?,
        val maxNativeZoom: Double?,
        val tileSize: Int?,
    )

    suspend fun resolve(provider: MapProvider): ResolvedMetadata = withContext(Dispatchers.IO) {
        val declared = provider.capabilities
        val style = fetchJson(provider.styleUri) ?: return@withContext ResolvedMetadata(
            minZoom = declared.minZoom,
            maxNativeZoom = declared.maxNativeZoom,
            tileSize = declared.tileSize,
        )

        val sources = style.optJSONObject("sources")
            ?: return@withContext ResolvedMetadata(
                declared.minZoom,
                declared.maxNativeZoom,
                declared.tileSize,
            )

        val minZooms = mutableListOf<Double>()
        val maxZooms = mutableListOf<Double>()
        val tileSizes = mutableListOf<Int>()

        val keys = sources.keys()
        while (keys.hasNext()) {
            val source = sources.optJSONObject(keys.next()) ?: continue
            source.optDoubleOrNull("minzoom")?.let(minZooms::add)
            source.optDoubleOrNull("maxzoom")?.let(maxZooms::add)
            source.optIntOrNull("tileSize")?.let(tileSizes::add)

            // A Style JSON may reference TileJSON with an absolute or relative URL.
            // Resolve relative URLs against the style endpoint instead of silently
            // losing their native zoom metadata.
            val sourceUrl = source.optString("url").takeIf { it.isNotBlank() }
            val tileJsonUrl = sourceUrl?.let { resolveAgainst(provider.styleUri, it) }
                ?.takeIf(::isHttpUrl)

            if (tileJsonUrl != null) {
                val tileJson = fetchJson(tileJsonUrl)
                tileJson?.optDoubleOrNull("minzoom")?.let(minZooms::add)
                tileJson?.optDoubleOrNull("maxzoom")?.let(maxZooms::add)
                tileJson?.optIntOrNull("tileSize")?.let(tileSizes::add)
            }
        }

        ResolvedMetadata(
            // The provider-level declared minZoom is authoritative when explicitly
            // configured; otherwise use the least restrictive source minimum.
            minZoom = declared.minZoom.takeIf { it > 0.0 } ?: minZooms.minOrNull() ?: declared.minZoom,
            // A manually declared native max is an explicit provider override.
            maxNativeZoom = declared.maxNativeZoom ?: maxZooms.maxOrNull(),
            tileSize = declared.tileSize ?: tileSizes.maxOrNull(),
        )
    }

    private fun fetchJson(url: String): JSONObject? {
        if (!isHttpUrl(url)) return null
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

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://") || value.startsWith("http://")
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
