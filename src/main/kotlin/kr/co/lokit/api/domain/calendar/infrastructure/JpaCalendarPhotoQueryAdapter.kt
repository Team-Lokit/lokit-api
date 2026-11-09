package kr.co.lokit.api.domain.calendar.infrastructure

import kr.co.lokit.api.domain.calendar.application.port.CalendarPhotoQueryPort
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class JpaCalendarPhotoQueryAdapter(
    private val calendarPhotoJpaRepository: CalendarPhotoJpaRepository,
) : CalendarPhotoQueryPort {
    @Transactional(readOnly = true)
    override fun findByCoupleIdAndTakenAtRange(
        coupleId: Long,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<CalendarPhoto> = calendarPhotoJpaRepository.findCalendarPhotos(coupleId, from, toExclusive)
}
