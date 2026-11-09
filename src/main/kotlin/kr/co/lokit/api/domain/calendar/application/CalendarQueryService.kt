package kr.co.lokit.api.domain.calendar.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import kr.co.lokit.api.domain.calendar.application.port.CalendarPhotoQueryPort
import kr.co.lokit.api.domain.calendar.application.port.`in`.GetCalendarUseCase
import kr.co.lokit.api.domain.calendar.domain.Anniversary
import kr.co.lokit.api.domain.calendar.domain.CalendarMonth
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import kr.co.lokit.api.domain.calendar.domain.CoupleAnniversaries
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.couple.domain.Couple
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

@Service
class CalendarQueryService(
    private val coupleRepository: CoupleRepositoryPort,
    private val calendarPhotoQueryPort: CalendarPhotoQueryPort,
) : GetCalendarUseCase {
    @Transactional(readOnly = true)
    override fun getMonth(
        userId: Long,
        yearMonth: YearMonth,
    ): CalendarMonth {
        val couple = requireCouple(userId)
        val photos =
            calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(
                coupleId = couple.id,
                from = yearMonth.atDay(1).atStartOfDay(),
                toExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(),
            )
        return CalendarMonth.of(yearMonth, photos, anniversariesOf(couple, yearMonth))
    }

    @Transactional(readOnly = true)
    override fun getDayPhotos(
        userId: Long,
        date: LocalDate,
    ): List<CalendarPhoto> {
        val couple = requireCouple(userId)
        val photos =
            calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(
                coupleId = couple.id,
                from = date.atStartOfDay(),
                toExclusive = date.plusDays(1).atStartOfDay(),
            )
        return CalendarPhoto.sortLatestFirst(photos)
    }

    /** 마이페이지와 같은 규칙: 연결된 2인 커플이고 커플이 된 날짜가 있을 때만 기념일을 만든다. */
    private fun anniversariesOf(
        couple: Couple,
        yearMonth: YearMonth,
    ): List<Anniversary> {
        val firstMetDate = couple.firstMetDate ?: return emptyList()
        if (!couple.isConnectedAndFull()) return emptyList()
        return CoupleAnniversaries(firstMetDate).within(yearMonth)
    }

    private fun requireCouple(userId: Long): Couple =
        coupleRepository.findByUserId(userId)
            ?: throw BusinessException.CoupleNotFoundException(
                errors = errorDetailsOf(ErrorField.USER_ID to userId),
            )
}
