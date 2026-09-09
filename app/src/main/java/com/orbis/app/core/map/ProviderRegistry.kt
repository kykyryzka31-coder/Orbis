package com.orbis.app.core.map

import android.content.Context

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
        )
    )

    fun getActive(): MapProvider {
        val uri = prefs.getString(KEY_STYLE_URI, null)
        val title = prefs.getString(KEY_TITLE, null)
        val maxZoom = prefs.getString(KEY_MAX_NATIVE_ZOOM, null)?.toDoubleOrNull()
        val kind = prefs.getString(KEY_KIND, null)?.let {
            runCatching { MapProviderKind.valueOf(it) }.getOrNull()
        }

        return if (!uri.isNullOrBlank() && !title.isNullOrBlank()) {
            MapProvider(
                id = "custom",
                title = title,
                kind = kind ?: MapProviderKind.CUSTOM,
                styleUri = uri,
                capabilities = MapProviderCapabilities(
                    maxNativeZoom = maxZoom,
                    offlineDownloadAllowed = false,
                    cacheAllowed = true,
                ),
                userConfigured = true,
            )
        } else {
            demoProvider
        }
    }

    fun saveCustomStyle(
        title: String,
        styleUri: String,
        kind: MapProviderKind,
        maxNativeZoom: Double?,
    ) {
        require(styleUri.startsWith("https://") || styleUri.startsWith("http://")) {
            "Provider style URI must be HTTP(S)."
        }
        prefs.edit()
            .putString(KEY_TITLE, title.trim())
            .putString(KEY_STYLE_URI, styleUri.trim())
            .putString(KEY_KIND, kind.name)
            .putString(KEY_MAX_NATIVE_ZOOM, maxNativeZoom?.toString())
            .apply()
    }

    fun resetToDemo() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TITLE = "title"
        const val KEY_STYLE_URI = "style_uri"
        const val KEY_KIND = "kind"
        const val KEY_MAX_NATIVE_ZOOM = "max_native_zoom"
    }
}
