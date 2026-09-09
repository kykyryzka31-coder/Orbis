# Orbis

Orbis is a professional mobile mapping workspace for Android, with iOS planned later. It is being built as an independent outdoor/GIS/offline-navigation product rather than a visual or architectural copy of another application.

## Current vertical slice

The repository currently implements the first real map-core slice:

- Kotlin + Jetpack Compose Android shell.
- MapLibre Native 13.6.0 GPU renderer using the Vulkan/OpenGL multi-backend artifact.
- Map-provider abstraction with a zero-key MapLibre demo source for development.
- User-configured licensed MapLibre Style JSON provider.
- Native zoom / tile-size metadata resolution from HTTP Style JSON and TileJSON when metadata exists.
- Separate displayed zoom vs native source zoom; overzoom is explicitly labelled as not adding geographic detail.
- `MAX DETAIL` mode with ideal-tile preference and a larger ambient cache budget.
- Real local raster PMTiles import through Android Storage Access Framework.
- App-private PMTiles storage and on-demand `pmtiles://file://` access.
- Real-time raster visibility and opacity.
- SQLite persistence for provider, camera, detail mode, and raster layer references.
- JVM tests for zoom/detail rules.
- GitHub Actions build/lint/unit-test CI.
- Android emulator launch smoke test.

No non-functional GPS/Tools buttons are shown. Features that are not implemented are not represented as working controls.

## Not implemented yet

- PGD importer.
- GeoTIFF / COG / MBTiles / GeoPackage import.
- GNSS and track recording.
- Compare / Swipe / Blink / Difference.
- Georeferencing and Precision Alignment.
- Drawing, markers, measurements.
- Provider offline-download packages.
- DEM / hillshade / slope / contours.
- GPX / KML / KMZ / GeoJSON workflows.
- High-resolution map export.

## Build baseline

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Kotlin 2.4.20
- Compose BOM 2026.08.00
- JDK 17
- compileSdk / targetSdk 37
- minSdk 26

The Gradle wrapper JAR is intentionally not generated in the source archive yet. CI installs pinned Gradle 9.6.0 directly. Once a normal Gradle environment is available, generate and commit the standard wrapper with `gradle wrapper --gradle-version 9.6.0 --distribution-type bin`.

## Provider policy

Orbis does not ship scraped or unlicensed satellite endpoints. During development the app can use MapLibre's demo style. A real satellite provider must be configured with a licensed Style JSON/API according to that provider's terms, including its caching/offline policy.

## PMTiles note

Local PMTiles are read through `pmtiles://file://`. This is a genuine range/on-demand source path rather than a screenshot or whole-raster decode. PMTiles compatibility still needs real-file coverage tests because different archive producers can expose renderer edge cases.

## Next implementation target

Phase 2 should harden the raster engine and add MBTiles plus GeoTIFF/COG before starting serious PGD interoperability work. PGD support must never be faked: unsupported variants should fail explicitly.
