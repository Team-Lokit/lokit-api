package kr.co.lokit.api.domain.map.application.port

import kr.co.lokit.api.domain.map.domain.RegionLevel
import kr.co.lokit.api.domain.map.domain.RegionPhotoAggregate

interface RegionPhotoQueryPort {
    fun aggregateByRegion(
        level: RegionLevel,
        coupleId: Long,
        albumId: Long?,
    ): List<RegionPhotoAggregate>

    fun findPhotosByRegionPrefixes(
        prefixes: List<String>,
        coupleId: Long,
    ): List<ClusterPhotoProjection>
}
