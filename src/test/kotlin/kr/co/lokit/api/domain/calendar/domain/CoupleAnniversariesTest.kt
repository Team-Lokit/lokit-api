package kr.co.lokit.api.domain.calendar.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoupleAnniversariesTest {
    private val firstMetDate = LocalDate.of(2026, 1, 1)
    private val anniversaries = CoupleAnniversaries(firstMetDate)

    @Test
    fun `커플이 된 날이 1일째다`() {
        assertEquals(1L, anniversaries.dayNumberOf(firstMetDate))
        assertEquals(100L, anniversaries.dayNumberOf(LocalDate.of(2026, 4, 10)))
    }

    @Test
    fun `커플이 된 날로부터 99일 뒤가 100일이다`() {
        val result = anniversaries.within(YearMonth.of(2026, 4))

        assertEquals(listOf(Anniversary(LocalDate.of(2026, 4, 10), AnniversaryType.DAY_COUNT, 100)), result)
    }

    @Test
    fun `이후 100일 단위로 기념일을 표시한다`() {
        val result = anniversaries.within(YearMonth.of(2026, 7))

        assertEquals(listOf(Anniversary(LocalDate.of(2026, 7, 19), AnniversaryType.DAY_COUNT, 200)), result)
    }

    @Test
    fun `커플이 된 달에는 1일째를 기념일로 표시하지 않는다`() {
        assertTrue(anniversaries.within(YearMonth.of(2026, 1)).isEmpty())
    }

    @Test
    fun `커플이 된 날 이전 달에는 기념일이 없다`() {
        assertTrue(anniversaries.within(YearMonth.of(2025, 12)).isEmpty())
    }

    @Test
    fun `매년 같은 월일에 N주년을 표시한다`() {
        val result = anniversaries.within(YearMonth.of(2028, 1))

        assertTrue(result.contains(Anniversary(LocalDate.of(2028, 1, 1), AnniversaryType.YEARLY, 2)))
    }

    @Test
    fun `100일 간격은 한 달보다 길어 한 달에 100일 단위 기념일은 최대 한 번이다`() {
        val sample = CoupleAnniversaries(LocalDate.of(2026, 3, 2))
        val months = (0L until 60L).map { YearMonth.of(2026, 3).plusMonths(it) }

        months.forEach { month ->
            assertTrue(sample.within(month).count { it.type == AnniversaryType.DAY_COUNT } <= 1)
        }
    }

    @Test
    fun `커플이 된 날짜가 미래면 그 이전 달에는 기념일이 없다`() {
        val future = CoupleAnniversaries(LocalDate.of(2030, 5, 5))

        assertTrue(future.within(YearMonth.of(2026, 11)).isEmpty())
    }

    @Test
    fun `윤일에 커플이 되었다면 평년에는 2월 28일에 주년을 표시한다`() {
        val leap = CoupleAnniversaries(LocalDate.of(2024, 2, 29))

        assertTrue(leap.within(YearMonth.of(2025, 2)).contains(Anniversary(LocalDate.of(2025, 2, 28), AnniversaryType.YEARLY, 1)))
        assertTrue(leap.within(YearMonth.of(2028, 2)).contains(Anniversary(LocalDate.of(2028, 2, 29), AnniversaryType.YEARLY, 4)))
    }

    @Test
    fun `결과는 날짜순으로 정렬된다`() {
        val result = anniversaries.within(YearMonth.of(2027, 1))

        assertEquals(result.sortedBy { it.date }, result)
    }
}
