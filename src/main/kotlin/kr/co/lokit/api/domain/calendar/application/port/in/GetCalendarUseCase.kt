package kr.co.lokit.api.domain.calendar.application.port.`in`

import kr.co.lokit.api.domain.calendar.domain.CalendarMonth
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import java.time.LocalDate
import java.time.YearMonth

interface GetCalendarUseCase {
    fun getMonth(
        userId: Long,
        yearMonth: YearMonth,
    ): CalendarMonth

    fun getDayPhotos(
        userId: Long,
        date: LocalDate,
    ): List<CalendarPhoto>
}
