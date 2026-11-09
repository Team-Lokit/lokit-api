package kr.co.lokit.api.domain.calendar.application.port

import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import java.time.LocalDateTime

interface CalendarPhotoQueryPort {
    /** [from, toExclusive) 구간의 커플 사진을 기준 시각 최신순(같으면 id 내림차순)으로 조회한다. */
    fun findByCoupleIdAndTakenAtRange(
        coupleId: Long,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<CalendarPhoto>
}
