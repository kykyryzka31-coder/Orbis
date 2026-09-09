package com.orbis.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orbis.app.core.geo.CoordinateFormatter
import com.orbis.app.core.geo.GeoMath
import com.orbis.app.core.geo.MeasurementSession
import com.orbis.app.core.map.LocalRasterImporter
import com.orbis.app.core.map.MapLibreController
import com.orbis.app.core.map.MapProvider
import com.orbis.app.core.map.MapProviderCapabilities
import com.orbis.app.core.map.ProviderMetadataResolver
import com.orbis.app.core.map.ProviderQualitySelector
import com.orbis.app.core.map.ProviderRegistry
import com.orbis.app.data.project.ProjectRepository
import com.orbis.app.model.LocalRasterLayer
import com.orbis.app.model.MapPoint
import com.orbis.app.model.ProjectSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.io.File
import java.util.UUID

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val providerRegistry = remember { ProviderRegistry(context) }
    val projectRepository = remember { ProjectRepository(context) }
    val importer = remember { LocalRasterImporter(context) }
    val controller = remember { MapLibreController() }
    val metadataResolver = remember { ProviderMetadataResolver() }
    val qualitySelector = remember { ProviderQualitySelector(metadataResolver) }
    val measurementSession = remember { MeasurementSession() }
    val restored = remember { projectRepository.load() }

    val restoredProvider = remember(restored) {
        restored?.let { snapshot ->
            MapProvider(
                id = "restored-provider",
                title = snapshot.providerTitle,
                kind = snapshot.providerKind,
                styleUri = snapshot.providerStyleUri,
                capabilities = MapProviderCapabilities(
                    maxNativeZoom = snapshot.providerMaxNativeZoom,
                    offlineDownloadAllowed = false,
                ),
                userConfigured = snapshot.providerStyleUri != providerRegistry.demoProvider.styleUri,
            )
        }
    }

    var provider by remember { mutableStateOf(restoredProvider ?: providerRegistry.getActive()) }
    var resolvedNativeZoom by remember { mutableStateOf(provider.capabilities.maxNativeZoom) }
    var resolvedTileSize by remember { mutableStateOf(provider.capabilities.tileSize) }
    var maxDetail by remember { mutableStateOf(restored?.maxDetailEnabled ?: true) }
    val rasterLayers = remember {
        mutableStateListOf<LocalRasterLayer>().apply {
            addAll(restored?.rasterLayers?.filter { File(it.filePath).exists() }.orEmpty())
        }
    }
    val points = remember {
        mutableStateListOf<MapPoint>().apply { addAll(restored?.points.orEmpty()) }
    }

    var mapReady by remember { mutableStateOf(false) }
    var styleReady by remember { mutableStateOf(false) }
    var restoredCameraApplied by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showProvider by remember { mutableStateOf(false) }
    var selectedCoordinate by remember { mutableStateOf<LatLng?>(null) }
    var measurementActive by remember { mutableStateOf(false) }
    var measurementMode by remember { mutableStateOf(MeasurementMode.DISTANCE) }
    var measurementRevision by remember { mutableIntStateOf(0) }
    var displayZoom by remember { mutableDoubleStateOf(0.0) }
    var centerLat by remember { mutableDoubleStateOf(0.0) }
    var cameraRevision by remember { mutableIntStateOf(0) }
    var transientMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(provider.styleUri) {
        val metadata = metadataResolver.resolve(provider)
        resolvedNativeZoom = metadata.maxNativeZoom
        resolvedTileSize = metadata.tileSize
    }

    val mapView = remember {
        MapView(context).also { view ->
            view.onCreate(Bundle())
            view.getMapAsync { map ->
                controller.bind(map)
                map.uiSettings.apply {
                    isCompassEnabled = true
                    isRotateGesturesEnabled = true
                    isTiltGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isAttributionEnabled = true
                    isLogoEnabled = true
                }
                map.addOnCameraIdleListener {
                    displayZoom = controller.displayedZoom()
                    centerLat = controller.centerLatitude()
                    cameraRevision += 1
                }
                map.addOnMapClickListener { point ->
                    if (measurementActive) {
                        measurementSession.add(
                            GeoMath.Coordinate(
                                latitude = point.latitude,
                                longitude = point.longitude,
                            )
                        )
                        controller.setMeasurementPath(
                            measurementSession.points,
                            closed = measurementMode == MeasurementMode.AREA,
                        )
                        measurementRevision += 1
                    } else {
                        selectedCoordinate = point
                    }
                    true
                }
                mapReady = true
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mapReady, provider.styleUri) {
        if (!mapReady) return@LaunchedEffect
        styleReady = false
        controller.setPoints(points.toList())
        controller.loadProvider(provider, rasterLayers.toList()) {
            controller.setMaxDetail(maxDetail)
            if (!restoredCameraApplied) {
                restored?.camera?.let(controller::restoreCamera)
                restoredCameraApplied = true
            }
            styleReady = true
            displayZoom = controller.displayedZoom()
            centerLat = controller.centerLatitude()
        }
    }

    LaunchedEffect(maxDetail, styleReady) {
        if (styleReady) controller.setMaxDetail(maxDetail)
    }

    LaunchedEffect(
        mapReady,
        provider.styleUri,
        resolvedNativeZoom,
        maxDetail,
        rasterLayers.toList(),
        points.toList(),
        cameraRevision,
    ) {
        if (!mapReady) return@LaunchedEffect
        delay(AUTOSAVE_DEBOUNCE_MS)
        val snapshot = ProjectSnapshot(
            providerTitle = provider.title,
            providerStyleUri = provider.styleUri,
            providerKind = provider.kind,
            providerMaxNativeZoom = resolvedNativeZoom,
            maxDetailEnabled = maxDetail,
            camera = controller.cameraSnapshot(),
            rasterLayers = rasterLayers.toList(),
            points = points.toList(),
        )
        runCatching {
            withContext(Dispatchers.IO) { projectRepository.save(snapshot) }
        }.onFailure { error ->
            transientMessage = "Autosave failed: ${error.message ?: "unknown error"}"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { importer.importRaster(uri) }
                .onSuccess { layer ->
                    rasterLayers.add(0, layer)
                    controller.addRaster(layer)
                    showLayers = true
                    transientMessage = "Imported ${layer.displayName}"
                }
                .onFailure { error ->
                    transientMessage = error.message ?: "Import failed"
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        MapStatusCard(
            provider = provider,
            resolvedNativeZoom = resolvedNativeZoom,
            resolvedTileSize = resolvedTileSize,
            maxDetail = maxDetail,
            displayedZoom = displayZoom,
            centerLatitude = centerLat,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 42.dp),
        )

        if (measurementActive) {
            MeasurementPanel(
                session = measurementSession,
                mode = measurementMode,
                revision = measurementRevision,
                onModeChange = { newMode ->
                    measurementMode = newMode
                    controller.setMeasurementPath(
                        measurementSession.points,
                        closed = newMode == MeasurementMode.AREA,
                    )
                },
                onUndo = {
                    measurementSession.undo()
                    controller.setMeasurementPath(
                        measurementSession.points,
                        closed = measurementMode == MeasurementMode.AREA,
                    )
                    measurementRevision += 1
                },
                onClear = {
                    measurementSession.clear()
                    controller.clearMeasurementPath()
                    measurementRevision += 1
                },
                onClose = {
                    measurementActive = false
                    measurementSession.clear()
                    controller.clearMeasurementPath()
                    measurementRevision += 1
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 92.dp),
            )
        }

        FloatingToolbar(
            onLayers = { showLayers = true },
            onProvider = { showProvider = true },
            onMeasure = {
                selectedCoordinate = null
                measurementSession.clear()
                measurementMode = MeasurementMode.DISTANCE
                measurementActive = true
                controller.clearMeasurementPath()
                measurementRevision += 1
            },
            onSave = {
                scope.launch {
                    val snapshot = ProjectSnapshot(
                        providerTitle = provider.title,
                        providerStyleUri = provider.styleUri,
                        providerKind = provider.kind,
                        providerMaxNativeZoom = resolvedNativeZoom,
                        maxDetailEnabled = maxDetail,
                        camera = controller.cameraSnapshot(),
                        rasterLayers = rasterLayers.toList(),
                        points = points.toList(),
                    )
                    runCatching {
                        withContext(Dispatchers.IO) { projectRepository.save(snapshot) }
                    }.onSuccess {
                        transientMessage = "Project saved locally"
                    }.onFailure { error ->
                        transientMessage = "Save failed: ${error.message ?: "unknown error"}"
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        )

        transientMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 108.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            LaunchedEffect(message) {
                delay(2200)
                if (transientMessage == message) transientMessage = null
            }
        }
    }

    if (showLayers) {
        LayersSheet(
            provider = provider,
            rasterLayers = rasterLayers.toList(),
            points = points.toList(),
            maxDetail = maxDetail,
            onDismiss = { showLayers = false },
            onImportRaster = {
                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onLayerChange = { updated ->
                val index = rasterLayers.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    rasterLayers[index] = updated
                    controller.updateRaster(updated)
                }
            },
            onLayerRemove = { layer ->
                controller.removeRaster(layer)
                rasterLayers.removeAll { it.id == layer.id }
            },
            onMoveLayerUp = { layer ->
                val index = rasterLayers.indexOfFirst { it.id == layer.id }
                if (index > 0) {
                    val moving = rasterLayers.removeAt(index)
                    rasterLayers.add(index - 1, moving)
                    controller.setRasterOrder(rasterLayers.toList())
                }
            },
            onMoveLayerDown = { layer ->
                val index = rasterLayers.indexOfFirst { it.id == layer.id }
                if (index in 0 until rasterLayers.lastIndex) {
                    val moving = rasterLayers.removeAt(index)
                    rasterLayers.add(index + 1, moving)
                    controller.setRasterOrder(rasterLayers.toList())
                }
            },
            onBlink = { layer ->
                scope.launch {
                    controller.setRasterPreviewHidden(layer.id, true)
                    delay(BLINK_COMPARE_MS)
                    controller.setRasterPreviewHidden(layer.id, false)
                }
            },
            onPointRemove = { point ->
                controller.removePoint(point.id)
                points.removeAll { it.id == point.id }
                transientMessage = "${point.name} removed"
            },
            onMaxDetailChange = { maxDetail = it },
        )
    }

    if (showProvider) {
        val providers = providerRegistry.listAll()
        ProviderDialog(
            current = provider,
            providers = providers,
            onDismiss = { showProvider = false },
            onSelect = { selected ->
                runCatching { providerRegistry.setActive(selected.id) }
                    .onSuccess {
                        provider = selected
                        showProvider = false
                    }
                    .onFailure { error ->
                        transientMessage = error.message ?: "Unable to select provider"
                    }
            },
            onAutoBest = {
                scope.launch {
                    transientMessage = "Checking native imagery detail…"
                    runCatching { qualitySelector.chooseBestSatellite(providerRegistry.listAll()) }
                        .onSuccess { candidate ->
                            if (candidate == null) {
                                transientMessage = "Add at least one satellite provider first"
                            } else {
                                providerRegistry.setActive(candidate.provider.id)
                                provider = candidate.provider
                                showProvider = false
                                val zoom = candidate.metadata.maxNativeZoom
                                    ?.let { "Z${"%.1f".format(it)}" }
                                    ?: "native zoom unknown"
                                val tile = candidate.metadata.tileSize?.let { " · ${it}px" }.orEmpty()
                                transientMessage = "Best imagery: ${candidate.provider.title} · $zoom$tile"
                            }
                        }
                        .onFailure { error ->
                            transientMessage = error.message ?: "Unable to compare imagery providers"
                        }
                }
            },
            onUseDemo = {
                providerRegistry.resetToDemo()
                provider = providerRegistry.demoProvider
                showProvider = false
            },
            onSave = { title, uri, kind, maxNativeZoom ->
                runCatching {
                    providerRegistry.saveCustomStyle(title, uri, kind, maxNativeZoom)
                }.onSuccess {
                    provider = providerRegistry.getActive()
                    showProvider = false
                }.onFailure { error ->
                    transientMessage = error.message ?: "Invalid provider"
                }
            },
        )
    }

    selectedCoordinate?.let { coordinate ->
        CoordinateSheet(
            coordinate = coordinate,
            onCopy = {
                val value = CoordinateFormatter.decimal(coordinate.latitude, coordinate.longitude)
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Coordinates", value))
                transientMessage = "Coordinates copied"
            },
            onAddPoint = {
                val point = MapPoint(
                    id = UUID.randomUUID().toString(),
                    name = "Point ${points.size + 1}",
                    latitude = coordinate.latitude,
                    longitude = coordinate.longitude,
                )
                points.add(0, point)
                controller.addPoint(point)
                selectedCoordinate = null
                transientMessage = "${point.name} added"
            },
            onMeasureFromHere = {
                measurementSession.clear()
                measurementSession.add(
                    GeoMath.Coordinate(
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                    )
                )
                measurementMode = MeasurementMode.DISTANCE
                measurementActive = true
                controller.setMeasurementPath(measurementSession.points)
                measurementRevision += 1
                selectedCoordinate = null
            },
            onDismiss = { selectedCoordinate = null },
        )
    }
}

private const val AUTOSAVE_DEBOUNCE_MS = 900L
private const val BLINK_COMPARE_MS = 700L
