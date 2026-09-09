# Orbis implementation status

| Area | Status | Notes |
|---|---|---|
| Android app shell | Implemented | Kotlin + Compose |
| Native GPU map | Implemented | MapLibre 13.6.0 multi-backend Vulkan/OpenGL |
| Provider abstraction | Implemented (Phase 1) | Style JSON provider + capabilities |
| Satellite | Adapter ready | Requires licensed provider URL |
| Native zoom discovery | Implemented (HTTP Style/TileJSON) | Unknown metadata remains unknown |
| MAX DETAIL | Implemented (initial) | Tile prefetch/parent overscale/cache tuning |
| Local raster | PMTiles implemented | File-based range/on-demand source |
| Layer visibility/opacity | Implemented | Realtime |
| SQLite project save/restore | Implemented | Camera/provider/layers |
| Unit tests | Implemented | Detail/zoom invariants |
| CI compile/lint/tests | Added | GitHub Actions |
| Emulator smoke test | Added | API 35 launch test |
| PGD | Not implemented | Requires lawful independent format analysis + real samples |
| GeoTIFF/COG | Not implemented | Phase 2 |
| MBTiles/GeoPackage | Not implemented | Phase 2 |
| Compare | Not implemented | Phase 4 |
| Georeference | Not implemented | Phase 4 |
| GPS/GNSS | Not implemented | Phase 5 |
| Offline packages | Not implemented | Phase 6 |
| DEM/terrain | Not implemented | Phase 7 |
