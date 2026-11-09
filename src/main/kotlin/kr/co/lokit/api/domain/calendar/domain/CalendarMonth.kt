package kr.co.lokit.api.domain.calendar.domain

import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(
    val date: LocalDate,
    val representativePhoto: CalendarPhoto?,
    val anniversaries: List<Anniversary>,
)

data class CalendarMonth(
    val yearMonth: YearMonth,
    val days: List<CalendarDay>,
) {
    companion object {
        fun of(
            yearMonth: YearMonth,
            photos: List<CalendarPhoto>,
            anniversaries: List<Anniversary>,
        ): CalendarMonth {
            val representatives =
                photos
                    .filter { YearMonth.from(it.takenAt) == yearMonth }
                    .groupBy { it.takenAt.toLocalDate() }
                    .mapValues { (_, dayPhotos) -> dayPhotos.minWith(CalendarPhoto.LATEST_FIRST) }
            val anniversariesByDate =
                anniversaries
                    .filter { YearMonth.from(it.date) == yearMonth }
                    .groupBy { it.date }

            val days =
                (representatives.keys + anniversariesByDate.keys)
                    .sorted()
                    .map { date ->
                        CalendarDay(
                            date = date,
                            representativePhoto = representatives[date],
                            anniversaries = anniversariesByDate[date].orEmpty(),
                        )
                    }
            return CalendarMonth(yearMonth, days)
        }
    }
}
