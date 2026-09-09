package com.orbis.app.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orbis.app.core.map.LocalRasterImporter
import com.orbis.app.core.map.MapLibreController
import com.orbis.app.core.map.MapProvider
import com.orbis.app.core.map.MapProviderKind
import com.orbis.app.core.map.ProviderRegistry
import com.orbis.app.core.map.ProviderMetadataResolver
import com.orbis.app.core.map.ZoomInfo
import com.orbis.app.data.project.ProjectRepository
import com.orbis.app.model.LocalRasterLayer
import com.orbis.app.model.ProjectSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                capabilities = com.orbis.app.core.map.MapProviderCapabilities(
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        MapStatusCard(
            provider = provider,
            resolvedNativeZoom = resolvedNativeZoom,
            resolvedTileSize = resolvedTileSize,
            maxDetail = maxDetail,
            displayedZoom = displayZoom,
            centerLatitude = centerLat,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 46.dp),
        )

        FloatingToolbar(
            onLayers = { showLayers = true },
            onProvider = { showProvider = true },
            onSave = {
                val camera = controller.cameraSnapshot()
                projectRepository.save(
                    ProjectSnapshot(
                        providerTitle = provider.title,
                        providerStyleUri = provider.styleUri,
                        providerKind = provider.kind,
                        providerMaxNativeZoom = resolvedNativeZoom,
                        maxDetailEnabled = maxDetail,
                        camera = camera,
                        rasterLayers = rasterLayers.toList(),
                    )
                )
                transientMessage = "Project saved locally"
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp),
        )

        transientMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 110.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2500)
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
                Spacer(Modifier.height(12.dp))
                Text(provider.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Base map",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))

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
                            "Prioritize ideal tiles and source detail",
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
                }.onFailure {
                    transientMessage = it.message ?: "Invalid provider"
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
    val resolution = ZoomInfo.displayedResolutionMetersPerPixel(
        centerLatitude,
        nativeZoom ?: displayedZoom,
        resolvedTileSize ?: 512,
    )
    val overzoomed = ZoomInfo.isOverzoomed(displayedZoom, resolvedNativeZoom)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(provider.title, style = MaterialTheme.typography.labelLarge)
            Text(
                if (maxDetail) "MAX DETAIL · ON" else "MAX DETAIL · OFF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(5.dp))
            Text("Displayed Zoom: ${"%.2f".format(displayedZoom)}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Native Zoom: ${resolvedNativeZoom?.let { "%.1f".format(it) } ?: "provider metadata unavailable"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Resolution: ${formatResolution(resolution)}", style = MaterialTheme.typography.bodySmall)
            if (overzoomed) {
                Text(
                    "OVERZOOM — no extra geographic detail",
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
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(22.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(layer.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${(layer.opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = layer.visible,
                onCheckedChange = { onChange(layer.copy(visible = it)) },
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove layer")
            }
        }
        Slider(
            value = layer.opacity,
            onValueChange = { onChange(layer.copy(opacity = it)) },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun ProviderDialog(
    current: MapProvider,
    onDismiss: () -> Unit,
    onUseDemo: () -> Unit,
    onSave: (String, String, MapProviderKind, Double?) -> Unit,
) {
    var title by remember { mutableStateOf(if (current.userConfigured) current.title else "My Satellite") }
    var uri by remember { mutableStateOf(if (current.userConfigured) current.styleUri else "") }
    var maxNative by remember {
        mutableStateOf(current.capabilities.maxNativeZoom?.toString() ?: "")
    }
    var isSatellite by remember { mutableStateOf(current.kind == MapProviderKind.SATELLITE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Use a licensed MapLibre Style JSON endpoint. No satellite provider is bundled or scraped.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Provider name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Style JSON URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxNative,
                    onValueChange = { maxNative = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Max native zoom (optional)") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isSatellite, onCheckedChange = { isSatellite = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Satellite imagery provider")
                }
                TextButton(onClick = onUseDemo) { Text("Use MapLibre demo") }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && uri.isNotBlank(),
                onClick = {
                    onSave(
                        title.trim(),
                        uri.trim(),
                        if (isSatellite) MapProviderKind.SATELLITE else MapProviderKind.CUSTOM,
                        maxNative.toDoubleOrNull(),
                    )
                },
            ) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
