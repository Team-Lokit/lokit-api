package kr.co.lokit.api.config

import kr.co.lokit.api.domain.map.application.port.ClusterPhotoProjection
import kr.co.lokit.api.domain.map.application.port.MapQueryPort
import kr.co.lokit.api.domain.map.application.port.PhotoProjection
import kr.co.lokit.api.domain.map.application.port.RegionCatalogPort
import kr.co.lokit.api.domain.map.application.port.RegionPhotoQueryPort
import kr.co.lokit.api.domain.map.application.port.RegionResolverPort
import kr.co.lokit.api.domain.map.domain.Region
import kr.co.lokit.api.domain.map.domain.RegionLevel
import kr.co.lokit.api.domain.map.domain.RegionPhotoAggregate
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestMapConfig {

    @Bean
    @Primary
    fun mapQueryPort(): MapQueryPort = object : MapQueryPort {
        override fun findPhotosWithinBBox(
            west: Double,
            south: Double,
            east: Double,
            north: Double,
            coupleId: Long?,
            albumId: Long?,
        ): List<PhotoProjection> = emptyList()

        override fun findPhotosInGridCell(
            west: Double,
            south: Double,
            east: Double,
            north: Double,
            coupleId: Long?,
        ): List<ClusterPhotoProjection> = emptyList()
    }

    @Bean
    @Primary
    fun regionResolverPort(): RegionResolverPort =
        object : RegionResolverPort {
            override fun resolveEupMyeonDongCode(
                longitude: Double,
                latitude: Double,
            ): String = "11680101"
        }

    @Bean
    @Primary
    fun regionCatalogPort(): RegionCatalogPort =
        object : RegionCatalogPort {
            override fun findAllRegions(): List<Region> = emptyList()
        }

    @Bean
    @Primary
    fun regionPhotoQueryPort(): RegionPhotoQueryPort =
        object : RegionPhotoQueryPort {
            override fun aggregateByRegion(
                level: RegionLevel,
                coupleId: Long,
                albumId: Long?,
            ): List<RegionPhotoAggregate> = emptyList()

            override fun findPhotosByRegionPrefixes(
                prefixes: List<String>,
                coupleId: Long,
            ): List<ClusterPhotoProjection> = emptyList()
        }
}
