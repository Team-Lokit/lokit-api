package kr.co.lokit.api.domain.map.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegionLevelTest {
    @ParameterizedTest
    @ValueSource(doubles = [0.0, 5.0, 7.99])
    fun `줌 8 미만은 시도 레벨이다`(zoom: Double) {
        assertEquals(RegionLevel.SIDO, RegionLevel.forZoom(zoom))
    }

    @ParameterizedTest
    @ValueSource(doubles = [8.0, 10.5, 11.99])
    fun `줌 8 이상 12 미만은 시군구 레벨이다`(zoom: Double) {
        assertEquals(RegionLevel.SIGUNGU, RegionLevel.forZoom(zoom))
    }

    @ParameterizedTest
    @ValueSource(doubles = [12.0, 14.99])
    fun `줌 12 이상 15 미만은 읍면동 레벨이다`(zoom: Double) {
        assertEquals(RegionLevel.EUPMYEONDONG, RegionLevel.forZoom(zoom))
    }

    @ParameterizedTest
    @ValueSource(doubles = [15.0, 22.0])
    fun `줌 15 이상은 레벨이 없다`(zoom: Double) {
        assertNull(RegionLevel.forZoom(zoom))
    }

    @ParameterizedTest
    @ValueSource(doubles = [Double.NaN, -1.0, Double.NEGATIVE_INFINITY])
    fun `유한하지 않거나 음수인 줌은 시도 레벨이다`(zoom: Double) {
        assertEquals(RegionLevel.SIDO, RegionLevel.forZoom(zoom))
    }
}
