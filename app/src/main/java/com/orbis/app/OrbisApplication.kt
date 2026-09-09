package com.orbis.app

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

class OrbisApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Multi-backend MapLibre artifact auto-selects Vulkan where supported,
        // otherwise falls back to OpenGL ES.
        MapLibre.getInstance(this)

        // Must be configured before map/style use. Phase 1 raises the ambient
        // cache well above MapLibre's default while keeping a finite budget.
        OfflineManager.getInstance(this).setMaximumAmbientCacheSize(
            512L * 1024L * 1024L,
            null,
        )
    }
}
