package com.orbis.app.core.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local-first provider library. Credentials are intentionally not modeled here;
 * style URLs that contain secrets should be supplied by a future secure credential layer.
 */
class ProviderRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("providers", Context.MODE_PRIVATE)

    val demoProvider = MapProvider(
        id = "maplibre-demo",
        title = "MapLibre Demo",
        kind = MapProviderKind.STREET,
        styleUri = "https://demotiles.maplibre.org/style.json",
        capabilities = MapProviderCapabilities(
            minZoom = 0.0,
            maxNativeZoom = null,
            tileSize = null,
            retina = null,
            offlineDownloadAllowed = false,
            cacheAllowed = true,
            attribution = "MapLibre demo tiles / OpenStreetMap data",
        ),
    )

    init {
        migrateLegacySingleProviderIfNeeded()
    }

    fun listAll(): List<MapProvider> = listOf(demoProvider) + readConfigured()

    fun listConfigured(): List<MapProvider> = readConfigured()

    fun getActive(): MapProvider {
        val providers = listAll()
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        return providers.firstOrNull { it.id == activeId }
            ?: readConfigured().lastOrNull()
            ?: demoProvider
    }

    fun setActive(providerId: String) {
        require(listAll().any { it.id == providerId }) { "Unknown map provider." }
        prefs.edit().putString(KEY_ACTIVE_ID, providerId).apply()
    }

    fun saveCustomStyle(
        title: String,
        styleUri: String,
        kind: MapProviderKind,
        maxNativeZoom: Double?,
    ) {
        val normalizedUri = styleUri.trim()
        require(normalizedUri.startsWith("https://") || normalizedUri.startsWith("http://")) {
            "Provider style URI must be HTTP(S)."
        }
        require(title.isNotBlank()) { "Provider name must not be blank." }
        require(maxNativeZoom == null || maxNativeZoom in 0.0..MapLibreController.RENDERER_MAX_DISPLAY_ZOOM) {
            "Native max zoom must be between 0 and ${MapLibreController.RENDERER_MAX_DISPLAY_ZOOM}."
        }

        val existing = readConfigured().toMutableList()
        val sameUriIndex = existing.indexOfFirst { it.styleUri == normalizedUri }
        val provider = MapProvider(
            id = if (sameUriIndex >= 0) existing[sameUriIndex].id else UUID.randomUUID().toString(),
            title = title.trim(),
            kind = kind,
            styleUri = normalizedUri,
            capabilities = MapProviderCapabilities(
                maxNativeZoom = maxNativeZoom,
                offlineDownloadAllowed = false,
                cacheAllowed = true,
            ),
            userConfigured = true,
        )

        if (sameUriIndex >= 0) existing[sameUriIndex] = provider else existing += provider
        writeConfigured(existing)
        prefs.edit().putString(KEY_ACTIVE_ID, provider.id).apply()
    }

    fun remove(providerId: String) {
        if (providerId == demoProvider.id) return
        val remaining = readConfigured().filterNot { it.id == providerId }
        writeConfigured(remaining)
        if (prefs.getString(KEY_ACTIVE_ID, null) == providerId) {
            prefs.edit().putString(KEY_ACTIVE_ID, demoProvider.id).apply()
        }
    }

    fun resetToDemo() {
        prefs.edit().putString(KEY_ACTIVE_ID, demoProvider.id).apply()
    }

    private fun readConfigured(): List<MapProvider> {
        val raw = prefs.getString(KEY_PROVIDER_LIBRARY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    parseProvider(json)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeConfigured(providers: List<MapProvider>) {
        val array = JSONArray()
        providers.forEach { provider -> array.put(providerToJson(provider)) }
        prefs.edit().putString(KEY_PROVIDER_LIBRARY, array.toString()).apply()
    }

    private fun providerToJson(provider: MapProvider) = JSONObject().apply {
        put("id", provider.id)
        put("title", provider.title)
        put("kind", provider.kind.name)
        put("styleUri", provider.styleUri)
        provider.capabilities.maxNativeZoom?.let { put("maxNativeZoom", it) }
        provider.capabilities.tileSize?.let { put("tileSize", it) }
        provider.capabilities.retina?.let { put("retina", it) }
        put("offlineDownloadAllowed", provider.capabilities.offlineDownloadAllowed)
        put("cacheAllowed", provider.capabilities.cacheAllowed)
        provider.capabilities.attribution?.let { put("attribution", it) }
    }

    private fun parseProvider(json: JSONObject): MapProvider? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        val styleUri = json.optString("styleUri").takeIf {
            it.startsWith("https://") || it.startsWith("http://")
        } ?: return null
        val kind = runCatching { MapProviderKind.valueOf(json.optString("kind")) }
            .getOrDefault(MapProviderKind.CUSTOM)
        val maxNative = if (json.has("maxNativeZoom")) json.optDouble("maxNativeZoom") else null
        val tileSize = if (json.has("tileSize")) json.optInt("tileSize") else null
        val retina = if (json.has("retina")) json.optBoolean("retina") else null
        return MapProvider(
            id = id,
            title = title,
            kind = kind,
            styleUri = styleUri,
            capabilities = MapProviderCapabilities(
                maxNativeZoom = maxNative,
                tileSize = tileSize,
                retina = retina,
                offlineDownloadAllowed = json.optBoolean("offlineDownloadAllowed", false),
                cacheAllowed = json.optBoolean("cacheAllowed", true),
                attribution = json.optString("attribution").takeIf { it.isNotBlank() },
            ),
            userConfigured = true,
        )
    }

    private fun migrateLegacySingleProviderIfNeeded() {
        if (prefs.contains(KEY_PROVIDER_LIBRARY)) return
        val uri = prefs.getString(LEGACY_STYLE_URI, null) ?: return
        val title = prefs.getString(LEGACY_TITLE, null) ?: return
        val maxZoom = prefs.getString(LEGACY_MAX_NATIVE_ZOOM, null)?.toDoubleOrNull()
        val kind = prefs.getString(LEGACY_KIND, null)?.let {
            runCatching { MapProviderKind.valueOf(it) }.getOrNull()
        } ?: MapProviderKind.CUSTOM
        if (!(uri.startsWith("https://") || uri.startsWith("http://"))) return

        val provider = MapProvider(
            id = UUID.randomUUID().toString(),
            title = title,
            kind = kind,
            styleUri = uri,
            capabilities = MapProviderCapabilities(maxNativeZoom = maxZoom),
            userConfigured = true,
        )
        writeConfigured(listOf(provider))
        prefs.edit()
            .putString(KEY_ACTIVE_ID, provider.id)
            .remove(LEGACY_TITLE)
            .remove(LEGACY_STYLE_URI)
            .remove(LEGACY_KIND)
            .remove(LEGACY_MAX_NATIVE_ZOOM)
            .apply()
    }

    private companion object {
        const val KEY_PROVIDER_LIBRARY = "provider_library_v2"
        const val KEY_ACTIVE_ID = "active_provider_id"

        const val LEGACY_TITLE = "title"
        const val LEGACY_STYLE_URI = "style_uri"
        const val LEGACY_KIND = "kind"
        const val LEGACY_MAX_NATIVE_ZOOM = "max_native_zoom"
    }
}
