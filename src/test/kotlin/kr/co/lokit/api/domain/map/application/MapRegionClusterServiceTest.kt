package kr.co.lokit.api.domain.map.application

import com.github.benmanes.caffeine.cache.Caffeine
import kr.co.lokit.api.config.cache.CacheNames
import kr.co.lokit.api.domain.map.application.mapping.toClusterPhotoReadModels
import kr.co.lokit.api.domain.map.application.port.ClusterPhotoProjection
import kr.co.lokit.api.domain.map.application.port.RegionPhotoQueryPort
import kr.co.lokit.api.domain.map.domain.BBox
import kr.co.lokit.api.domain.map.domain.Region
import kr.co.lokit.api.domain.map.domain.RegionCatalog
import kr.co.lokit.api.domain.map.domain.RegionClusterId
import kr.co.lokit.api.domain.map.domain.RegionCode
import kr.co.lokit.api.domain.map.domain.RegionLevel
import kr.co.lokit.api.domain.map.domain.RegionPhotoAggregate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class MapRegionClusterServiceTest {
    @Mock
    lateinit var regionPhotoQueryPort: RegionPhotoQueryPort

    @Mock
    lateinit var regionCatalogProvider: RegionCatalogProvider

    @Mock
    lateinit var mapPhotosCacheService: MapPhotosCacheService

    @Mock
    lateinit var cacheManager: CacheManager

    lateinit var mapRegions: CaffeineCache

    lateinit var service: MapRegionClusterService

    private val suwonEnvelope = BBox(west = 126.9, south = 37.2, east = 127.1, north = 37.35)

    private val catalog =
        RegionCatalog(
            listOf(
                region("41", null, BBox(west = 126.3, south = 36.9, east = 127.9, north = 38.3)),
                region("41110", "41", suwonEnvelope),
                region("41111", "41110", BBox(west = 126.95, south = 37.28, east = 127.05, north = 37.35)),
                region("41113", "41110", BBox(west = 126.9, south = 37.2, east = 127.0, north = 37.3)),
            ),
        )

    /** 수원시 envelope 과 교차한다. */
    private val visibleBBox = BBox(west = 126.8, south = 37.1, east = 127.2, north = 37.4)

    /** 부산 근처. 수원시 envelope 과 교차하지 않는다. */
    private val farBBox = BBox(west = 128.9, south = 35.0, east = 129.2, north = 35.3)

    private val t1 = LocalDateTime.of(2025, 1, 1, 12, 0)
    private val t2 = LocalDateTime.of(2025, 2, 1, 12, 0)

    private val suwonAggregates =
        listOf(
            RegionPhotoAggregate(regionCode = "41111", count = 2, photoId = 10L, thumbnailUrl = "old.jpg", takenAt = t1),
            RegionPhotoAggregate(regionCode = "41113", count = 3, photoId = 5L, thumbnailUrl = "new.jpg", takenAt = t2),
        )

    @BeforeEach
    fun setUp() {
        mapRegions = CaffeineCache(CacheNames.MAP_REGIONS, Caffeine.newBuilder().build())
        lenient().`when`(cacheManager.getCache(CacheNames.MAP_REGIONS)).thenReturn(mapRegions)
        lenient().`when`(regionCatalogProvider.get()).thenReturn(catalog)
        service =
            MapRegionClusterService(
                regionPhotoQueryPort,
                regionCatalogProvider,
                mapPhotosCacheService,
                cacheManager,
            )
    }

    @Test
    fun `지역 집계 결과 중 bbox에 보이는 클러스터를 반환한다`() {
        `when`(regionPhotoQueryPort.aggregateByRegion(RegionLevel.SIGUNGU, 1L, null)).thenReturn(suwonAggregates)
        `when`(mapPhotosCacheService.getVersion(1L)).thenReturn(0L)

        val result = service.getClusters(RegionLevel.SIGUNGU, visibleBBox, 1L, null).asList()

        assertEquals(1, result.size)
        assertEquals("sgg_41110", result[0].clusterId)
        assertEquals(5, result[0].count)
        assertEquals("new.jpg", result[0].thumbnailUrl)
    }

    @Test
    fun `coupleId가 없으면 포트를 호출하지 않고 빈 클러스터를 반환한다`() {
        val result = service.getClusters(RegionLevel.SIGUNGU, visibleBBox, null, null)

        assertTrue(result.isEmpty())
        verifyNoInteractions(regionPhotoQueryPort)
    }

    @Test
    fun `같은 버전이면 bbox가 달라도 포트를 한 번만 조회한다`() {
        `when`(regionPhotoQueryPort.aggregateByRegion(RegionLevel.SIGUNGU, 1L, null)).thenReturn(suwonAggregates)
        `when`(mapPhotosCacheService.getVersion(1L)).thenReturn(0L)

        val visible = service.getClusters(RegionLevel.SIGUNGU, visibleBBox, 1L, null)
        val far = service.getClusters(RegionLevel.SIGUNGU, farBBox, 1L, null)

        verify(regionPhotoQueryPort, times(1)).aggregateByRegion(RegionLevel.SIGUNGU, 1L, null)
        assertEquals(listOf("sgg_41110"), visible.asList().map { it.clusterId })
        assertTrue(far.isEmpty())
    }

    @Test
    fun `버전이 바뀌면 다시 조회한다`() {
        `when`(regionPhotoQueryPort.aggregateByRegion(RegionLevel.SIGUNGU, 1L, null)).thenReturn(suwonAggregates)
        `when`(mapPhotosCacheService.getVersion(1L)).thenReturn(0L, 1L)

        service.getClusters(RegionLevel.SIGUNGU, visibleBBox, 1L, null)
        service.getClusters(RegionLevel.SIGUNGU, visibleBBox, 1L, null)

        verify(regionPhotoQueryPort, times(2)).aggregateByRegion(RegionLevel.SIGUNGU, 1L, null)
    }

    @Test
    fun `카탈로그가 비어 있으면 집계 결과를 캐시하지 않는다`() {
        var regionDataLoaded = false
        `when`(regionCatalogProvider.get()).thenAnswer { if (regionDataLoaded) catalog else RegionCatalog.empty() }
        `when`(regionPhotoQueryPort.aggregateByRegion(RegionLevel.SIGUNGU, 1L, null)).thenReturn(suwonAggregates)
        `when`(mapPhotosCacheService.getVersion(1L)).thenReturn(0L)

        val beforeLoad = service.getClusters(RegionLevel.SIGUNGU, visibleBBox, 1L, null)
        regionDataLoaded = true
        val afterLoad = service.getClusters(RegionLevel.SIGUNGU, visibleBBox, 1L, null)

        assertTrue(beforeLoad.isEmpty())
        assertEquals(listOf("sgg_41110"), afterLoad.asList().map { it.clusterId })
    }

    @Test
    fun `캐시가 없어도 동작한다`() {
        `when`(cacheManager.getCache(CacheNames.MAP_REGIONS)).thenReturn(null)
        `when`(regionPhotoQueryPort.aggregateByRegion(RegionLevel.SIGUNGU, 1L, null)).thenReturn(suwonAggregates)
        lenient().`when`(mapPhotosCacheService.getVersion(1L)).thenReturn(0L)

        val result = service.getClusters(RegionLevel.SIGUNGU, visibleBBox, 1L, null).asList()

        assertEquals(listOf("sgg_41110"), result.map { it.clusterId })
        assertEquals(5, result[0].count)
    }

    @Test
    fun `지역 클러스터 사진은 구성원 prefix 전체로 조회한다`() {
        val photos =
            listOf(
                ClusterPhotoProjection(
                    id = 5L,
                    url = "new.jpg",
                    longitude = 127.0,
                    latitude = 37.25,
                    takenAt = t2,
                    address = "수원시 권선구",
                ),
                ClusterPhotoProjection(
                    id = 10L,
                    url = "old.jpg",
                    longitude = 127.0,
                    latitude = 37.30,
                    takenAt = t1,
                    address = "수원시 장안구",
                ),
            )
        `when`(regionPhotoQueryPort.findPhotosByRegionPrefixes(any(), eq(1L))).thenReturn(photos)

        val result =
            service.getClusterPhotos(RegionClusterId(RegionLevel.SIGUNGU, RegionCode.of("41110")), 1L)

        verify(regionPhotoQueryPort).findPhotosByRegionPrefixes(
            argThat { toSet() == setOf("41110", "41111", "41113") },
            eq(1L),
        )
        assertEquals(photos.toClusterPhotoReadModels(), result.asList())
    }

    @Test
    fun `coupleId가 없으면 지역 클러스터 사진 포트를 호출하지 않고 빈 결과를 반환한다`() {
        val result =
            service.getClusterPhotos(RegionClusterId(RegionLevel.SIGUNGU, RegionCode.of("41110")), null)

        assertTrue(result.isEmpty())
        verifyNoInteractions(regionPhotoQueryPort)
    }

    private fun region(
        code: String,
        parentCode: String?,
        envelope: BBox,
    ): Region =
        Region(
            code = RegionCode.of(code),
            name = code,
            parentCode = parentCode?.let { RegionCode.of(it) },
            centerLongitude = (envelope.west + envelope.east) / 2,
            centerLatitude = (envelope.south + envelope.north) / 2,
            envelope = envelope,
        )
}
