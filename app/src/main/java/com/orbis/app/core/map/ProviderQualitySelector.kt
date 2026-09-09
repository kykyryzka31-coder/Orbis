package com.orbis.app.core.map

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

    suspend fun resolveCandidates(providers: List<MapProvider>): List<Candidate> =
        providers.map { provider -> Candidate(provider, metadataResolver.resolve(provider)) }

    fun chooseBest(candidates: List<Candidate>): Candidate? = candidates.maxWithOrNull(
        compareBy<Candidate>(
            { it.metadata.maxNativeZoom ?: Double.NEGATIVE_INFINITY },
            { it.metadata.tileSize ?: 0 },
            { if (it.provider.capabilities.retina == true) 1 else 0 },
        )
    )

    suspend fun chooseBestSatellite(providers: List<MapProvider>): Candidate? {
        val satellites = providers.filter { it.kind == MapProviderKind.SATELLITE }
        if (satellites.isEmpty()) return null
        return chooseBest(resolveCandidates(satellites))
    }
}
