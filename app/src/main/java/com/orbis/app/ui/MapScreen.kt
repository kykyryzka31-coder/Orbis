package com.orbis.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.orbis.app.core.map.LocalRasterImporter
import com.orbis.app.core.map.MapLibreController
import com.orbis.app.core.map.MapProvider
import com.orbis.app.core.map.MapProviderCapabilities
import com.orbis.app.core.map.MapProviderKind
import com.orbis.app.core.map.ProviderMetadataResolver
import com.orbis.app.core.map.ProviderRegistry
import com.orbis.app.core.map.ZoomInfo
import com.orbis.app.data.project.ProjectRepository
import com.orbis.app.model.LocalRasterLayer
import com.orbis.app.model.ProjectSnapshot
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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

    var mapReady by remember { mutableStateOf(false) }
    var styleReady by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showProvider by remember { mutableStateOf(false) }
    var displayZoom by remember { mutableDoubleStateOf(0.0) }
    var centerLat by remember { mutableDoubleStateOf(0.0) }
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
        controller.loadProvider(provider, rasterLayers.toList()) {
            controller.setMaxDetail(maxDetail)
            restored?.camera?.let(controller::restoreCamera)
            styleReady = true
            displayZoom = controller.displayedZoom()
            centerLat = controller.centerLatitude()
        }
    }

    LaunchedEffect(maxDetail, styleReady) {
        if (styleReady) controller.setMaxDetail(maxDetail)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { importer.importPmTiles(uri) }
                .onSuccess { layer ->
                    rasterLayers += layer
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

        FloatingToolbar(
            onLayers = { showLayers = true },
            onProvider = { showProvider = true },
            onSave = {
                projectRepository.save(
                    ProjectSnapshot(
                        providerTitle = provider.title,
                        providerStyleUri = provider.styleUri,
                        providerKind = provider.kind,
                        providerMaxNativeZoom = resolvedNativeZoom,
                        maxDetailEnabled = maxDetail,
                        camera = controller.cameraSnapshot(),
                        rasterLayers = rasterLayers.toList(),
                    )
                )
                transientMessage = "Project saved locally"
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
                kotlinx.coroutines.delay(2200)
                if (transientMessage == message) transientMessage = null
            }
        }
    }

    if (showLayers) {
        ModalBottomSheet(onDismissRequest = { showLayers = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text("LAYERS", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                Text(provider.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Base map",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                rasterLayers.forEachIndexed { index, layer ->
                    LayerRow(
                        layer = layer,
                        onChange = { updated ->
                            rasterLayers[index] = updated
                            controller.updateRaster(updated)
                        },
                        onRemove = {
                            controller.removeRaster(layer)
                            rasterLayers.remove(layer)
                        },
                    )
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ADD RASTER PMTILES")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("MAX DETAIL")
                        Text(
                            "Prefer ideal native tiles over lower LOD",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = maxDetail, onCheckedChange = { maxDetail = it })
                }
            }
        }
    }

    if (showProvider) {
        ProviderDialog(
            current = provider,
            onDismiss = { showProvider = false },
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
}

@Composable
private fun MapStatusCard(
    provider: MapProvider,
    resolvedNativeZoom: Double?,
    resolvedTileSize: Int?,
    maxDetail: Boolean,
    displayedZoom: Double,
    centerLatitude: Double,
    modifier: Modifier = Modifier,
) {
    val nativeZoom = ZoomInfo.effectiveNativeZoom(displayedZoom, resolvedNativeZoom)
    val tileScale = if (nativeZoom != null && resolvedTileSize != null) {
        ZoomInfo.displayedResolutionMetersPerPixel(
            centerLatitude,
            nativeZoom,
            resolvedTileSize,
        )
    } else {
        null
    }
    val overzoomed = ZoomInfo.isOverzoomed(displayedZoom, resolvedNativeZoom)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(provider.title, style = MaterialTheme.typography.labelLarge)
            Text(
                if (maxDetail) "MAX DETAIL · ON" else "MAX DETAIL · OFF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Zoom ${"%.2f".format(displayedZoom)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Native ${resolvedNativeZoom?.let { "%.1f".format(it) } ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (tileScale != null) {
                Text(
                    "Tile scale ~${formatResolution(tileScale)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (overzoomed) {
                Text(
                    "OVERZOOM · no extra geographic detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatResolution(meters: Double): String = when {
    meters < 0.01 -> "${(meters * 1000).roundToInt()} mm/px"
    meters < 1.0 -> "${"%.2f".format(meters)} m/px"
    else -> "${"%.1f".format(meters)} m/px"
}

@Composable
private fun FloatingToolbar(
    onLayers: () -> Unit,
    onProvider: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(22.dp),
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FilledIconButton(onClick = onLayers, shape = CircleShape) {
            Icon(Icons.Default.Layers, contentDescription = "Layers")
        }
        IconButton(onClick = onProvider) {
            Icon(Icons.Default.Map, contentDescription = "Map provider")
        }
        IconButton(onClick = onSave) {
            Icon(Icons.Default.Save, contentDescription = "Save project")
        }
    }
}

@Composable
private fun LayerRow(
    layer: LocalRasterLayer,
    onChange: (LocalRasterLayer) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
    ) {
        Text(layer.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "Opacity ${(layer.opacity * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = layer.opacity,
            onValueChange = { onChange(layer.copy(opacity = it)) },
            valueRange = 0f..1f,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (layer.visible) "Visible" else "Hidden")
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = layer.visible,
                    onCheckedChange = { onChange(layer.copy(visible = it)) },
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove layer")
            }
        }
    }
}

@Composable
private fun ProviderDialog(
    current: MapProvider,
    onDismiss: () -> Unit,
    onUseDemo: () -> Unit,
    onSave: (String, String, MapProviderKind, Double?) -> Unit,
) {
    var title by remember {
        mutableStateOf(if (current.userConfigured) current.title else "My Satellite")
    }
    var uri by remember {
        mutableStateOf(if (current.userConfigured) current.styleUri else "")
    }
    var maxNative by remember {
        mutableStateOf(current.capabilities.maxNativeZoom?.toString() ?: "")
    }
    var isSatellite by remember {
        mutableStateOf(current.kind == MapProviderKind.SATELLITE)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Use a licensed MapLibre Style JSON endpoint. Orbis will read source metadata and use the deepest native zoom it exposes.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Style JSON URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxNative,
                    onValueChange = { maxNative = it },
                    label = { Text("Native max zoom override (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Satellite imagery")
                    Switch(
                        checked = isSatellite,
                        onCheckedChange = { isSatellite = it },
                    )
                }
                TextButton(onClick = onUseDemo) {
                    Text("USE MAPLIBRE DEMO")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.trim().ifBlank { "Custom map" },
                        uri.trim(),
                        if (isSatellite) MapProviderKind.SATELLITE else MapProviderKind.CUSTOM,
                        maxNative.toDoubleOrNull(),
                    )
                },
                enabled = uri.startsWith("https://") || uri.startsWith("http://"),
            ) {
                Text("SAVE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        },
    )
}
