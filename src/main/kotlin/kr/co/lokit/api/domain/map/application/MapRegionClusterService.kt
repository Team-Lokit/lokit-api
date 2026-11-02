package kr.co.lokit.api.domain.map.application

import kr.co.lokit.api.config.cache.CacheNames
import kr.co.lokit.api.domain.map.application.mapping.toClusterPhotoReadModels
import kr.co.lokit.api.domain.map.application.port.RegionPhotoQueryPort
import kr.co.lokit.api.domain.map.domain.BBox
import kr.co.lokit.api.domain.map.domain.ClusterPhotos
import kr.co.lokit.api.domain.map.domain.Clusters
import kr.co.lokit.api.domain.map.domain.RegionCatalog
import kr.co.lokit.api.domain.map.domain.RegionClusterAssembler
import kr.co.lokit.api.domain.map.domain.RegionClusterId
import kr.co.lokit.api.domain.map.domain.RegionClusterSummary
import kr.co.lokit.api.domain.map.domain.RegionLevel
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

@Service
class MapRegionClusterService(
    private val regionPhotoQueryPort: RegionPhotoQueryPort,
    private val regionCatalogProvider: RegionCatalogProvider,
    private val mapPhotosCacheService: MapPhotosCacheService,
    private val cacheManager: CacheManager,
) {
    data class CachedRegionSummaries(
        val summaries: List<RegionClusterSummary>,
    )

    fun getClusters(
        level: RegionLevel,
        bbox: BBox,
        coupleId: Long?,
        albumId: Long?,
    ): Clusters {
        if (coupleId == null) return Clusters.empty()
        val catalog = regionCatalogProvider.get()
        val cache = cacheManager.getCache(CacheNames.MAP_REGIONS)
        val summaries =
            if (cache == null || catalog.isEmpty()) {
                loadSummaries(level, coupleId, albumId, catalog)
            } else {
                val key =
                    MapCacheKeyFactory.buildRegionKey(
                        level,
                        coupleId,
                        albumId,
                        mapPhotosCacheService.getVersion(coupleId),
                    )
                cache.get(key, CachedRegionSummaries::class.java)?.summaries
                    ?: loadSummaries(level, coupleId, albumId, catalog).also { cache.put(key, CachedRegionSummaries(it)) }
            }
        return RegionClusterAssembler.toVisibleClusters(summaries, bbox, catalog)
    }

    fun getClusterPhotos(
        clusterId: RegionClusterId,
        coupleId: Long?,
    ): ClusterPhotos {
        if (coupleId == null) return ClusterPhotos.empty()
        val prefixes = regionCatalogProvider.get().memberPrefixes(clusterId.code).map { it.value }
        return ClusterPhotos.of(
            regionPhotoQueryPort.findPhotosByRegionPrefixes(prefixes, coupleId).toClusterPhotoReadModels(),
        )
    }

    private fun loadSummaries(
        level: RegionLevel,
        coupleId: Long,
        albumId: Long?,
        catalog: RegionCatalog,
    ): List<RegionClusterSummary> =
        RegionClusterAssembler.merge(
            level,
            regionPhotoQueryPort.aggregateByRegion(level, coupleId, albumId),
            catalog,
        )
}
