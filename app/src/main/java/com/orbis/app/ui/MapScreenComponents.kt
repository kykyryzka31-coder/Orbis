package com.orbis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orbis.app.core.geo.CoordinateFormatter
import com.orbis.app.core.geo.MeasurementFormatter
import com.orbis.app.core.geo.MeasurementSession
import com.orbis.app.core.map.MapProvider
import com.orbis.app.core.map.MapProviderKind
import com.orbis.app.core.map.ZoomInfo
import com.orbis.app.model.LocalRasterLayer
import com.orbis.app.model.MapPoint
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt

internal enum class MeasurementMode {
    DISTANCE,
    AREA,
}

@Composable
internal fun MapStatusCard(
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
            Text("Zoom ${"%.2f".format(displayedZoom)}", style = MaterialTheme.typography.bodySmall)
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
internal fun FloatingToolbar(
    onLayers: () -> Unit,
    onProvider: () -> Unit,
    onMeasure: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(22.dp))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FilledIconButton(onClick = onLayers, shape = CircleShape) {
            Icon(Icons.Default.Layers, contentDescription = "Layers")
        }
        IconButton(onClick = onProvider) {
            Icon(Icons.Default.Map, contentDescription = "Map provider")
        }
        TextButton(onClick = onMeasure) {
            Text("MEASURE")
        }
        IconButton(onClick = onSave) {
            Icon(Icons.Default.Save, contentDescription = "Save project")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayersSheet(
    provider: MapProvider,
    rasterLayers: List<LocalRasterLayer>,
    points: List<MapPoint>,
    maxDetail: Boolean,
    onDismiss: () -> Unit,
    onImportRaster: () -> Unit,
    onLayerChange: (LocalRasterLayer) -> Unit,
    onLayerRemove: (LocalRasterLayer) -> Unit,
    onMoveLayerUp: (LocalRasterLayer) -> Unit,
    onMoveLayerDown: (LocalRasterLayer) -> Unit,
    onBlink: (LocalRasterLayer) -> Unit,
    onPointRemove: (MapPoint) -> Unit,
    onMaxDetailChange: (Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("LAYERS · TOP FIRST", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Text(provider.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Base map · ${points.size} saved points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            rasterLayers.forEachIndexed { index, layer ->
                LayerRow(
                    layer = layer,
                    canMoveUp = index > 0,
                    canMoveDown = index < rasterLayers.lastIndex,
                    onMoveUp = { onMoveLayerUp(layer) },
                    onMoveDown = { onMoveLayerDown(layer) },
                    onBlink = { onBlink(layer) },
                    onChange = onLayerChange,
                    onRemove = { onLayerRemove(layer) },
                )
            }

            OutlinedButton(onClick = onImportRaster, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ADD RASTER MAP")
            }

            if (points.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("POINTS", style = MaterialTheme.typography.labelLarge)
                points.forEach { point ->
                    PointRow(point = point, onRemove = { onPointRemove(point) })
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MAX DETAIL")
                    Text(
                        "Prefer ideal native tiles over lower LOD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = maxDetail, onCheckedChange = onMaxDetailChange)
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: LocalRasterLayer,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onBlink: () -> Unit,
    onChange: (LocalRasterLayer) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(layer.displayName, style = MaterialTheme.typography.bodyLarge)
                val detail = buildString {
                    append(layer.format.name)
                    layer.maxNativeZoom?.let { append(" · native Z${"%.1f".format(it)}") }
                    layer.tileSize?.let { append(" · ${it}px") }
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move layer up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move layer down")
            }
        }
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Quick",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OPACITY_PRESETS.forEach { preset ->
                TextButton(onClick = { onChange(layer.copy(opacity = preset)) }) {
                    Text("${(preset * 100).roundToInt()}%")
                }
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBlink, enabled = layer.visible) {
                    Text("BLINK")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove layer")
                }
            }
        }
    }
}

@Composable
private fun PointRow(
    point: MapPoint,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(point.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                CoordinateFormatter.decimal(point.latitude, point.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove point")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoordinateSheet(
    coordinate: LatLng,
    onCopy: () -> Unit,
    onAddPoint: () -> Unit,
    onMeasureFromHere: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("COORDINATE", style = MaterialTheme.typography.labelLarge)
            Text(
                CoordinateFormatter.decimal(coordinate.latitude, coordinate.longitude),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "WGS84 · Decimal degrees",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                CoordinateFormatter.dms(coordinate.latitude, coordinate.longitude),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = onAddPoint, modifier = Modifier.fillMaxWidth()) {
                Text("ADD POINT")
            }
            OutlinedButton(onClick = onMeasureFromHere, modifier = Modifier.fillMaxWidth()) {
                Text("MEASURE FROM HERE")
            }
            TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Text("COPY COORDINATES")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun MeasurementPanel(
    session: MeasurementSession,
    mode: MeasurementMode,
    revision: Int,
    onModeChange: (MeasurementMode) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reading revision makes Compose refresh values from the mutable non-Compose session.
    @Suppress("UNUSED_VARIABLE")
    val observedRevision = revision

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("MEASURE", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${session.points.size} points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onModeChange(MeasurementMode.DISTANCE) }) {
                    Text(if (mode == MeasurementMode.DISTANCE) "✓ DISTANCE" else "DISTANCE")
                }
                TextButton(onClick = { onModeChange(MeasurementMode.AREA) }) {
                    Text(if (mode == MeasurementMode.AREA) "✓ AREA" else "AREA")
                }
            }

            if (mode == MeasurementMode.DISTANCE) {
                Text(
                    MeasurementFormatter.distance(session.totalDistanceMeters),
                    style = MaterialTheme.typography.titleMedium,
                )
                session.lastSegmentDistanceMeters?.let { segment ->
                    val bearing = session.lastBearingDegrees?.let(MeasurementFormatter::bearing)
                    Text(
                        "Last ${MeasurementFormatter.distance(segment)}${bearing?.let { " · $it" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val area = session.polygonAreaSquareMeters
                val perimeter = session.polygonPerimeterMeters
                Text(
                    area?.let(MeasurementFormatter::area) ?: "Add at least 3 points",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (perimeter != null) {
                    Text(
                        "Perimeter ${MeasurementFormatter.distance(perimeter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    TextButton(onClick = onUndo, enabled = session.points.isNotEmpty()) { Text("UNDO") }
                    TextButton(onClick = onClear, enabled = session.points.isNotEmpty()) { Text("CLEAR") }
                }
                TextButton(onClick = onClose) { Text("CLOSE") }
            }
        }
    }
}

@Composable
internal fun ProviderDialog(
    current: MapProvider,
    providers: List<MapProvider>,
    onDismiss: () -> Unit,
    onSelect: (MapProvider) -> Unit,
    onAutoBest: () -> Unit,
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
                    "Use licensed MapLibre Style JSON endpoints. Orbis compares real native zoom metadata instead of treating overzoom as extra detail.",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (providers.any { it.kind == MapProviderKind.SATELLITE }) {
                    OutlinedButton(onClick = onAutoBest, modifier = Modifier.fillMaxWidth()) {
                        Text("AUTO BEST SATELLITE")
                    }
                }

                Text("Saved maps", style = MaterialTheme.typography.labelMedium)
                providers.forEach { saved ->
                    TextButton(onClick = { onSelect(saved) }, modifier = Modifier.fillMaxWidth()) {
                        val marker = if (saved.styleUri == current.styleUri) "✓ " else ""
                        val detail = saved.capabilities.maxNativeZoom
                            ?.let { " · Z${"%.1f".format(it)}" }
                            .orEmpty()
                        Text("$marker${saved.title}$detail")
                    }
                }

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
                    Switch(checked = isSatellite, onCheckedChange = { isSatellite = it })
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

private val OPACITY_PRESETS = listOf(0.35f, 0.65f, 1.0f)
