# Orbis architecture

Orbis is a local-first professional mapping application. The current codebase is deliberately a small vertical slice rather than a mock of the final product.

## Current module boundaries

- `core/map` — provider contracts, native/detail metadata, MapLibre adapter, local raster import.
- `data/project` — transactional SQLite persistence for the active project.
- `model` — persisted map/project state.
- `ui` — Compose shell around a native MapLibre `MapView`.

## Renderer boundary

Application logic must not depend directly on a specific map SDK beyond the renderer adapter. `MapLibreController` is the current infrastructure adapter and should progressively implement a renderer-facing interface as additional engines are added.

## Detail rule

Displayed zoom and native source zoom are separate concepts. UI and measurements must never claim that overzoom creates additional geographic information.

## Local raster rule

Large raster data must be accessed on demand. The Phase 1 PMTiles path uses MapLibre's `pmtiles://file://` source and byte-range access. Whole-raster decode into RAM is not allowed.

## Future modules

Planned independent modules include `TileEngine`, `OfflineMapManager`, `GeoReferenceEngine`, `GPSEngine`, `TrackRecorder`, `DrawingEngine`, `MeasurementEngine`, `ElevationEngine`, and `ImportExportManager`.
