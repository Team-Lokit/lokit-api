package kr.co.lokit.api.domain.calendar.presentation

import kr.co.lokit.api.common.annotation.CurrentUserId
import kr.co.lokit.api.domain.calendar.application.port.`in`.GetCalendarUseCase
import kr.co.lokit.api.domain.calendar.dto.CalendarDayPhotosResponse
import kr.co.lokit.api.domain.calendar.dto.CalendarMonthResponse
import kr.co.lokit.api.domain.calendar.presentation.mapping.toDayPhotosResponse
import kr.co.lokit.api.domain.calendar.presentation.mapping.toResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth

@RestController
@RequestMapping("calendar")
class CalendarController(
    private val getCalendarUseCase: GetCalendarUseCase,
) : CalendarApi {
    @GetMapping
    override fun getMonth(
        @CurrentUserId userId: Long,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") yearMonth: YearMonth,
    ): CalendarMonthResponse = getCalendarUseCase.getMonth(userId, yearMonth).toResponse()

    @GetMapping("days/{date}/photos")
    override fun getDayPhotos(
        @CurrentUserId userId: Long,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): CalendarDayPhotosResponse = getCalendarUseCase.getDayPhotos(userId, date).toDayPhotosResponse(date)
}
