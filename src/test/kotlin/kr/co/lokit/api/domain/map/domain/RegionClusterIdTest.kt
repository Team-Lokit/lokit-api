package kr.co.lokit.api.domain.map.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegionClusterIdTest {
    @Test
    fun `레벨별 접두사로 포맷한다`() {
        assertEquals("sd_11", RegionClusterId(RegionLevel.SIDO, RegionCode.of("11")).format())
        assertEquals("sgg_41110", RegionClusterId(RegionLevel.SIGUNGU, RegionCode.of("41110")).format())
        assertEquals(
            "emd_11680101",
            RegionClusterId(RegionLevel.EUPMYEONDONG, RegionCode.of("11680101")).format(),
        )
    }

    @Test
    fun `지역 클러스터 ID를 파싱한다`() {
        assertEquals(
            RegionClusterId(RegionLevel.SIGUNGU, RegionCode.of("41110")),
            RegionClusterId.parse("sgg_41110"),
        )

        listOf(
            RegionClusterId(RegionLevel.SIDO, RegionCode.of("11")),
            RegionClusterId(RegionLevel.SIGUNGU, RegionCode.of("41110")),
            RegionClusterId(RegionLevel.EUPMYEONDONG, RegionCode.of("11680101")),
        ).forEach { id ->
            assertEquals(id, RegionClusterId.parse(id.format()))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["z14_24661_7867", "z14_1_2_mz14500", "sgg_4111", "sd_11680", "emd_1168010a", "xx_11"])
    fun `지역 형식이 아니면 null을 반환한다`(clusterId: String) {
        assertNull(RegionClusterId.parse(clusterId))
    }
}
