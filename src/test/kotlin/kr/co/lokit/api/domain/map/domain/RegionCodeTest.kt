package kr.co.lokit.api.domain.map.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class RegionCodeTest {
    @Test
    fun `코드 길이로 레벨이 정해진다`() {
        assertEquals(RegionLevel.SIDO, RegionCode.of("11").level)
        assertEquals(RegionLevel.SIGUNGU, RegionCode.of("41111").level)
        assertEquals(RegionLevel.EUPMYEONDONG, RegionCode.of("11680101").level)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1", "123", "1168010a", "1168010100"])
    fun `숫자 2·5·8자리가 아니면 예외가 발생한다`(value: String) {
        assertThrows<IllegalArgumentException> {
            RegionCode.of(value)
        }
    }

    @Test
    fun `상위 레벨로 절단할 수 있다`() {
        val code = RegionCode.of("11680101")

        assertEquals(RegionCode.of("11680"), code.truncate(RegionLevel.SIGUNGU))
        assertEquals(RegionCode.of("11"), code.truncate(RegionLevel.SIDO))
    }

    @Test
    fun `하위 레벨로 절단하면 예외가 발생한다`() {
        assertThrows<IllegalArgumentException> {
            RegionCode.of("11").truncate(RegionLevel.SIGUNGU)
        }
    }
}
