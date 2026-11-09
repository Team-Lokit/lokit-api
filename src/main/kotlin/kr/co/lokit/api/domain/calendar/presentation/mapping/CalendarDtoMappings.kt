package kr.co.lokit.api.domain.calendar.presentation.mapping

import kr.co.lokit.api.domain.calendar.domain.Anniversary
import kr.co.lokit.api.domain.calendar.domain.CalendarDay
import kr.co.lokit.api.domain.calendar.domain.CalendarMonth
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import kr.co.lokit.api.domain.calendar.dto.AnniversaryResponse
import kr.co.lokit.api.domain.calendar.dto.CalendarDayPhotoResponse
import kr.co.lokit.api.domain.calendar.dto.CalendarDayPhotosResponse
import kr.co.lokit.api.domain.calendar.dto.CalendarDayResponse
import kr.co.lokit.api.domain.calendar.dto.CalendarMonthResponse
import kr.co.lokit.api.domain.calendar.dto.CalendarPhotoResponse
import java.time.LocalDate

fun CalendarMonth.toResponse(): CalendarMonthResponse =
    CalendarMonthResponse(
        yearMonth = yearMonth,
        days = days.map { it.toResponse() },
    )

fun CalendarDay.toResponse(): CalendarDayResponse =
    CalendarDayResponse(
        date = date,
        representativePhoto = representativePhoto?.toResponse(),
        anniversaries = anniversaries.map { it.toResponse() },
    )

fun CalendarPhoto.toResponse(): CalendarPhotoResponse = CalendarPhotoResponse(id = id, url = url, takenAt = takenAt)

fun Anniversary.toResponse(): AnniversaryResponse = AnniversaryResponse(type = type, value = value)

fun List<CalendarPhoto>.toDayPhotosResponse(date: LocalDate): CalendarDayPhotosResponse =
    CalendarDayPhotosResponse(
        date = date,
        photos = map { CalendarDayPhotoResponse(id = it.id, url = it.url, takenAt = it.takenAt, address = it.address) },
    )
