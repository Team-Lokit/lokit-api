package kr.co.lokit.api.domain.map.domain

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class RegionClusterAssemblerTest {
    private val t1 = LocalDateTime.of(2026, 1, 1, 10, 0)
    private val t2 = LocalDateTime.of(2026, 1, 2, 10, 0)

    @Test
    fun `단일 구역은 카탈로그 중심점과 대표 사진으로 클러스터가 된다`() {
        val catalog = RegionCatalog(listOf(region("11", null, 126.98, 37.56)))

        val summaries =
            RegionClusterAssembler.merge(
                RegionLevel.SIDO,
                listOf(RegionPhotoAggregate("11", 3, 7L, "a.jpg", t1)),
                catalog,
            )
        val clusters = RegionClusterAssembler.toVisibleClusters(summaries, BBox.KOREA_BOUNDS, catalog)

        assertEquals(listOf(RegionClusterSummary(RegionCode.of("11"), 3, 7L, "a.jpg", t1)), summaries)
        assertEquals(
            listOf(ClusterReadModel("sd_11", 3, "a.jpg", 126.98, 37.56, t1, regionName = "region-11")),
            clusters.asList(),
        )
    }

    @Test
    fun `일반구 집계는 시 단위로 합쳐지고 최신 사진이 대표가 된다`() {
        val catalog = suwonCatalog()

        val summaries =
            RegionClusterAssembler.merge(
                RegionLevel.SIGUNGU,
                listOf(
                    RegionPhotoAggregate("41111", 2, 10L, "old.jpg", t1),
                    RegionPhotoAggregate("41113", 3, 5L, "new.jpg", t2),
                ),
                catalog,
            )
        val clusters = RegionClusterAssembler.toVisibleClusters(summaries, BBox.KOREA_BOUNDS, catalog)

        assertEquals(listOf(RegionClusterSummary(RegionCode.of("41110"), 5, 5L, "new.jpg", t2)), summaries)
        assertEquals(listOf("sgg_41110"), clusters.asList().map { it.clusterId })
    }

    @Test
    fun `구역 클러스터에는 카탈로그의 구역 이름이 담긴다`() {
        val catalog = suwonCatalog()

        val summaries =
            RegionClusterAssembler.merge(
                RegionLevel.SIGUNGU,
                listOf(RegionPhotoAggregate("41111", 2, 10L, "a.jpg", t1)),
                catalog,
            )
        val clusters = RegionClusterAssembler.toVisibleClusters(summaries, BBox.KOREA_BOUNDS, catalog)

        // 일반구(41111)로 집계돼도 시 단위로 승격되므로 시(41110)의 이름이 나간다
        assertEquals(listOf("region-41110"), clusters.asList().map { it.regionName })
    }

    @Test
    fun `촬영시각이 같으면 id가 큰 사진이 대표가 된다`() {
        val summaries =
            RegionClusterAssembler.merge(
                RegionLevel.SIGUNGU,
                listOf(
                    RegionPhotoAggregate("41111", 1, 10L, "ten.jpg", t1),
                    RegionPhotoAggregate("41113", 1, 11L, "eleven.jpg", t1),
                ),
                suwonCatalog(),
            )

        assertEquals(1, summaries.size)
        assertEquals(11L, summaries.single().photoId)
        assertEquals("eleven.jpg", summaries.single().thumbnailUrl)
    }

    @Test
    fun `카탈로그에 없는 코드와 레벨과 길이가 다른 코드는 제외된다`() {
        val catalog =
            RegionCatalog(
                listOf(
                    region("11", null),
                    region("11680", "11"),
                ),
            )

        val summaries =
            RegionClusterAssembler.merge(
                RegionLevel.SIDO,
                listOf(
                    RegionPhotoAggregate("11", 1, 1L, "ok.jpg", t1),
                    RegionPhotoAggregate("99", 1, 2L, "unknown.jpg", t1),
                    RegionPhotoAggregate("1168", 1, 3L, "invalid.jpg", t1),
                    RegionPhotoAggregate("11680", 1, 4L, "wrong-level.jpg", t1),
                ),
                catalog,
            )

        assertEquals(listOf(RegionCode.of("11")), summaries.map { it.code })
        assertEquals(1, summaries.single().count)
    }

    @Test
    fun `envelope이 bbox와 교차하는 구역만 보인다`() {
        val visible =
            region(
                "11680",
                "11",
                centerLongitude = 127.15,
                centerLatitude = 37.5,
                envelope = BBox(127.0, 37.4, 127.2, 37.6),
            )
        val hidden =
            region(
                "26110",
                "26",
                centerLongitude = 129.05,
                centerLatitude = 35.05,
                envelope = BBox(129.0, 35.0, 129.1, 35.1),
            )
        val catalog = RegionCatalog(listOf(visible, hidden))
        val bbox = BBox(126.9, 37.4, 127.1, 37.6)

        val clusters =
            RegionClusterAssembler.toVisibleClusters(
                listOf(
                    RegionClusterSummary(RegionCode.of("11680"), 2, 1L, "a.jpg", t1),
                    RegionClusterSummary(RegionCode.of("26110"), 4, 2L, "b.jpg", t1),
                ),
                bbox,
                catalog,
            )

        assertEquals(listOf("sgg_11680"), clusters.asList().map { it.clusterId })
        assertEquals(127.15, clusters.asList().single().longitude)
        assertEquals(37.5, clusters.asList().single().latitude)
    }

    private fun suwonCatalog() =
        RegionCatalog(
            listOf(
                region("41", null),
                region("41110", "41"),
                region("41111", "41110"),
                region("41113", "41110"),
            ),
        )

    private fun region(
        code: String,
        parentCode: String?,
        centerLongitude: Double = 127.0,
        centerLatitude: Double = 37.5,
        envelope: BBox = BBox(126.9, 37.4, 127.1, 37.6),
    ) = Region(
        code = RegionCode.of(code),
        name = "region-$code",
        parentCode = parentCode?.let { RegionCode.of(it) },
        centerLongitude = centerLongitude,
        centerLatitude = centerLatitude,
        envelope = envelope,
    )
}
