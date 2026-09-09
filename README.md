# Orbis

Orbis is a professional mobile mapping workspace for Android, with iOS planned later. It is being built as an independent outdoor/GIS/offline-navigation product rather than a visual or architectural copy of another application.

## Current vertical slice

The repository now implements a working map-core and the first GIS interaction slice:

- Kotlin + Jetpack Compose Android shell.
- MapLibre Native 13.6.0 GPU renderer using the Vulkan/OpenGL multi-backend artifact.
- Map-provider abstraction with a zero-key MapLibre demo source for development.
- Local library of user-configured licensed MapLibre Style JSON providers.
- Native zoom / tile-size metadata resolution from HTTP Style JSON and TileJSON when metadata exists.
- Separate displayed zoom vs native source zoom; overzoom is explicitly labelled as not adding geographic detail.
- `MAX DETAIL` mode that prefers native tiles instead of silently substituting lower LOD imagery.
- `Auto Best Satellite` selection based on real native detail metadata rather than display overzoom.
- Real local raster PMTiles and raster MBTiles import through Android Storage Access Framework.
- App-private map storage with on-demand archive rendering and no image recompression during import.
- PMTiles v3 header inspection for native zoom and raster/vector tile-type validation without scanning the full archive.
- MBTiles raster validation, tile-size detection, and native max-zoom detection.
- Multiple raster overlays with visibility, continuous opacity, 35/65/100% comparison presets, top-first ordering, and quick reorder controls.
- One-tap `BLINK` comparison that temporarily hides an overlay without changing saved project state.
- Local project autosave for provider, camera, detail mode, raster layers, layer ordering, opacity, and saved points.
- Tap coordinate inspector with WGS84 decimal degrees, DMS formatting, and copy action.
- Persistent saved map points rendered as a GeoJSON circle layer and restored from SQLite.
- Geodesic distance, polyline length, and initial-bearing math with JVM tests.
- Live GeoJSON measurement-path renderer (line + vertices); interactive measurement UI is the current integration target.
- GitHub Actions build/lint/unit-test CI.
- Android emulator launch smoke test.

No non-functional GPS/Tools buttons are shown. Features that are not implemented are not represented as working controls.

## Not implemented yet

- PGD importer.
- GeoTIFF / COG / GeoPackage import.
- Full measurement interaction UI, saved measurements, and area measurement.
- Saved-point rename/category/photo workflows and full point library UI.
- GNSS and background track recording.
- Swipe / Difference comparison modes.
- Georeferencing and Precision Alignment.
- Drawing tools and persistent lines/polygons.
- Provider offline-download packages and offline-area manager.
- DEM / hillshade / slope / contours.
- GPX / KML / KMZ / GeoJSON import/export workflows.
- High-resolution map export.

## Build baseline

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Kotlin 2.4.20
- Compose BOM 2026.06.00
- JDK 17
- compileSdk / targetSdk 36
- minSdk 26

The Gradle wrapper JAR is intentionally not generated in the source archive yet. CI installs pinned Gradle 9.6.0 directly. Once a normal Gradle environment is available, generate and commit the standard wrapper with `gradle wrapper --gradle-version 9.6.0 --distribution-type bin`.

## Provider policy

Orbis does not ship scraped or unlicensed satellite endpoints. During development the app can use MapLibre's demo style. A real satellite provider must be configured with a licensed Style JSON/API according to that provider's terms, including its caching/offline policy.

## Native-detail policy

Displayed zoom and real source detail are separate concepts in Orbis. The renderer may allow display zoom beyond a provider's native maximum, but the UI must never call overzoom new geographic detail. If a provider exposes deeper native tiles, Orbis should request and display them; if it does not, the app must say so rather than invent resolution.

## Local raster policy

Local PMTiles and MBTiles are kept as map archives and rendered on demand. Import must not flatten an archive into a screenshot, load a multi-gigabyte map fully into memory, or recompress imagery merely to display it. Unsupported archive variants should fail explicitly.

## PGD policy

PGD interoperability is a priority but must never be faked. Implementation should use lawful clean-room interoperability from user-owned sample files and independently observed format behavior. Orbis must not copy another application's source code or bypass encryption/DRM.

## Next implementation target

Finish the first GIS interaction loop:

`tap map -> inspect coordinate -> save point / measure distance -> render result -> autosave project`

Then continue raster coverage with GeoTIFF/COG/GeoPackage and start serious PGD format probing only when representative user-owned samples are available.
