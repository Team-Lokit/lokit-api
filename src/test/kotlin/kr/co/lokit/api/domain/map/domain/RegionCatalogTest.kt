package kr.co.lokit.api.domain.map.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RegionCatalogTest {
    private val catalog =
        RegionCatalog(
            listOf(
                region("41", "경기도", null),
                region("41110", "수원시", "41"),
                region("41111", "수원시 장안구", "41110"),
                region("41113", "수원시 권선구", "41110"),
                region("11", "서울특별시", null),
                region("11680", "강남구", "11"),
                region("11680101", "역삼동", "11680"),
            ),
        )

    @Test
    fun `일반구는 시 코드로 승격한다`() {
        assertEquals(RegionCode.of("41110"), catalog.clusterCodeOf(RegionCode.of("41111")))
    }

    @Test
    fun `일반구가 없는 시군구·미지 코드·시도·읍면동은 그대로다`() {
        assertEquals(RegionCode.of("11680"), catalog.clusterCodeOf(RegionCode.of("11680")))
        assertEquals(RegionCode.of("99999"), catalog.clusterCodeOf(RegionCode.of("99999")))
        assertEquals(RegionCode.of("11"), catalog.clusterCodeOf(RegionCode.of("11")))
        assertEquals(RegionCode.of("11680101"), catalog.clusterCodeOf(RegionCode.of("11680101")))
    }

    @Test
    fun `시의 구성원 prefix에는 자신과 일반구가 포함된다`() {
        val members = catalog.memberPrefixes(RegionCode.of("41110"))

        assertEquals(RegionCode.of("41110"), members.first())
        assertEquals(
            setOf(RegionCode.of("41110"), RegionCode.of("41111"), RegionCode.of("41113")),
            members.toSet(),
        )
        assertEquals(listOf(RegionCode.of("11680")), catalog.memberPrefixes(RegionCode.of("11680")))
        assertEquals(listOf(RegionCode.of("11680101")), catalog.memberPrefixes(RegionCode.of("11680101")))
        assertEquals(listOf(RegionCode.of("99990")), catalog.memberPrefixes(RegionCode.of("99990")))
    }

    private fun region(
        code: String,
        name: String,
        parentCode: String?,
    ) = Region(
        code = RegionCode.of(code),
        name = name,
        parentCode = parentCode?.let { RegionCode.of(it) },
        centerLongitude = 127.0,
        centerLatitude = 37.5,
        envelope = BBox(126.9, 37.4, 127.1, 37.6),
    )
}
