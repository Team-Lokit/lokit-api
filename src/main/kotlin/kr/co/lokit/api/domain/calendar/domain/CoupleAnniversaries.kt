package kr.co.lokit.api.domain.calendar.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 커플이 된 날짜를 1일째로 계산한다.
 * - N일 기념일: firstMetDate + (N - 1)일. 100일 단위만 표시한다.
 * - k주년: firstMetDate.plusYears(k). 윤일(2/29) 시작이면 평년에는 2/28 이 된다.
 */
class CoupleAnniversaries(
    private val firstMetDate: LocalDate,
) {
    fun dayNumberOf(date: LocalDate): Long = ChronoUnit.DAYS.between(firstMetDate, date) + 1

    fun within(month: YearMonth): List<Anniversary> = (dayCounts(month) + yearlies(month)).sortedBy { it.date }

    private fun dayCounts(month: YearMonth): List<Anniversary> {
        val last = month.atEndOfMonth()
        val firstDayNumber = maxOf(dayNumberOf(month.atDay(1)), DAY_COUNT_UNIT)
        val startN = ceilToUnit(firstDayNumber)
        return generateSequence(startN) { it + DAY_COUNT_UNIT }
            .map { n -> Anniversary(firstMetDate.plusDays(n - 1), AnniversaryType.DAY_COUNT, n.toInt()) }
            .takeWhile { !it.date.isAfter(last) }
            .toList()
    }

    private fun yearlies(month: YearMonth): List<Anniversary> {
        val years = month.year - firstMetDate.year
        if (years < 1 || month.month != firstMetDate.month) return emptyList()
        return listOf(Anniversary(firstMetDate.plusYears(years.toLong()), AnniversaryType.YEARLY, years))
    }

    private fun ceilToUnit(n: Long): Long = Math.ceilDiv(n, DAY_COUNT_UNIT) * DAY_COUNT_UNIT

    companion object {
        private const val DAY_COUNT_UNIT = 100L
    }
}
