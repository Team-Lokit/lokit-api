package kr.co.lokit.api.domain.calendar.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarMonthTest {
    private val month = YearMonth.of(2026, 11)

    private fun photo(
        id: Long,
        takenAt: LocalDateTime,
    ) = CalendarPhoto(id = id, url = "https://example.com/$id.jpg", takenAt = takenAt)

    @Test
    fun `같은 날짜의 사진 중 기준 시각이 가장 최신인 사진이 대표 사진이다`() {
        val photos =
            listOf(
                photo(1L, LocalDateTime.of(2026, 11, 2, 9, 0)),
                photo(2L, LocalDateTime.of(2026, 11, 2, 21, 0)),
                photo(3L, LocalDateTime.of(2026, 11, 2, 13, 0)),
            )

        val result = CalendarMonth.of(month, photos, emptyList())

        assertEquals(1, result.days.size)
        assertEquals(2L, result.days.single().representativePhoto?.id)
    }

    @Test
    fun `기준 시각이 같으면 id가 큰 사진이 대표 사진이다`() {
        val sameTime = LocalDateTime.of(2026, 11, 2, 9, 0)
        val photos = listOf(photo(5L, sameTime), photo(9L, sameTime))

        val result = CalendarMonth.of(month, photos, emptyList())

        assertEquals(9L, result.days.single().representativePhoto?.id)
    }

    @Test
    fun `사진과 기념일이 있는 날짜만 날짜순으로 담는다`() {
        val photos =
            listOf(
                photo(1L, LocalDateTime.of(2026, 11, 20, 9, 0)),
                photo(2L, LocalDateTime.of(2026, 11, 3, 0, 0)),
            )
        val anniversary = Anniversary(LocalDate.of(2026, 11, 10), AnniversaryType.DAY_COUNT, 300)

        val result = CalendarMonth.of(month, photos, listOf(anniversary))

        assertEquals(
            listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20)),
            result.days.map { it.date },
        )
        val anniversaryDay = result.days[1]
        assertNull(anniversaryDay.representativePhoto)
        assertEquals(listOf(anniversary), anniversaryDay.anniversaries)
    }

    @Test
    fun `사진과 기념일이 같은 날이면 한 날짜로 합친다`() {
        val photos = listOf(photo(1L, LocalDateTime.of(2026, 11, 10, 23, 59)))
        val anniversary = Anniversary(LocalDate.of(2026, 11, 10), AnniversaryType.YEARLY, 1)

        val result = CalendarMonth.of(month, photos, listOf(anniversary))

        val day = result.days.single()
        assertEquals(1L, day.representativePhoto?.id)
        assertEquals(listOf(anniversary), day.anniversaries)
    }

    @Test
    fun `해당 월 밖의 사진과 기념일은 무시한다`() {
        val photos = listOf(photo(1L, LocalDateTime.of(2026, 12, 1, 0, 0)))
        val anniversary = Anniversary(LocalDate.of(2026, 10, 31), AnniversaryType.DAY_COUNT, 100)

        val result = CalendarMonth.of(month, photos, listOf(anniversary))

        assertEquals(emptyList(), result.days)
    }

    @Test
    fun `날짜별 사진은 기준 시각 최신순, 같으면 id 내림차순으로 정렬한다`() {
        val t = LocalDateTime.of(2026, 11, 2, 9, 0)
        val photos = listOf(photo(1L, t), photo(3L, t.plusHours(1)), photo(2L, t))

        assertEquals(listOf(3L, 2L, 1L), CalendarPhoto.sortLatestFirst(photos).map { it.id })
    }
}
