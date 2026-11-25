package kr.co.lokit.api.domain.calendar.application.port.`in`

import kr.co.lokit.api.domain.calendar.domain.CalendarMonth
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import java.time.LocalDate
import java.time.YearMonth

/** albumId 가 없거나 기본 앨범이면 커플의 전체 사진, 아니면 해당 앨범의 사진만 대상으로 한다. */
interface GetCalendarUseCase {
    fun getMonth(
        userId: Long,
        yearMonth: YearMonth,
        albumId: Long? = null,
    ): CalendarMonth

    fun getDayPhotos(
        userId: Long,
        date: LocalDate,
        albumId: Long? = null,
    ): List<CalendarPhoto>
}
