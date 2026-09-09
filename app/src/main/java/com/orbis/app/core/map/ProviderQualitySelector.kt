package com.orbis.app.core.map

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.ln

/**
 * Chooses the source that can provide the most real map detail.
 * Displayed/overzoom levels are deliberately excluded from scoring.
 */
class ProviderQualitySelector(
    private val metadataResolver: ProviderMetadataResolver,
) {
    data class Candidate(
        val provider: MapProvider,
        val metadata: ProviderMetadataResolver.ResolvedMetadata,
    )

    suspend fun resolveCandidates(providers: List<MapProvider>): List<Candidate> = coroutineScope {
        providers.map { provider ->
            async { Candidate(provider, metadataResolver.resolve(provider)) }
        }.awaitAll()
    }

    fun chooseBest(candidates: List<Candidate>): Candidate? = candidates.maxWithOrNull { left, right ->
        compareCandidates(left, right)
    }

    suspend fun chooseBestSatellite(providers: List<MapProvider>): Candidate? {
        val satellites = providers.filter { it.kind == MapProviderKind.SATELLITE }
        if (satellites.isEmpty()) return null
        return chooseBest(resolveCandidates(satellites))
    }

    private fun compareCandidates(left: Candidate, right: Candidate): Int {
        val leftResolution = left.provider.capabilities.nativeResolutionMetersPerPixel
        val rightResolution = right.provider.capabilities.nativeResolutionMetersPerPixel
        if (leftResolution != null && rightResolution != null && leftResolution != rightResolution) {
            // Smaller meters-per-pixel means more physical source detail.
            return rightResolution.compareTo(leftResolution)
        }

        val detailComparison = effectiveNativeDetail(left).compareTo(effectiveNativeDetail(right))
        if (detailComparison != 0) return detailComparison

        val retinaComparison = (left.provider.capabilities.retina == true)
            .compareTo(right.provider.capabilities.retina == true)
        if (retinaComparison != 0) return retinaComparison

        // Stable final tie-breaker so repeated Auto Best runs do not jump randomly.
        return left.provider.id.compareTo(right.provider.id)
    }

    /**
     * Converts native zoom + tile dimensions to a common WebMercator pixel-density scale.
     * A 512px tile at Z22 has the same nominal pixel density as a 256px tile at Z23.
     */
    private fun effectiveNativeDetail(candidate: Candidate): Double {
        val zoom = candidate.metadata.maxNativeZoom ?: return Double.NEGATIVE_INFINITY
        val tileSize = (candidate.metadata.tileSize ?: candidate.provider.capabilities.tileSize ?: 256)
            .coerceAtLeast(1)
        val tileDensityZoom = ln(tileSize / 256.0) / ln(2.0)
        return zoom + tileDensityZoom
    }
}
