package kr.co.lokit.api.domain.calendar.infrastructure

import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import kr.co.lokit.api.domain.photo.infrastructure.PhotoEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import java.time.LocalDateTime

interface CalendarPhotoJpaRepository : Repository<PhotoEntity, Long> {
    @Query(
        "SELECT new kr.co.lokit.api.domain.calendar.domain.CalendarPhoto(p.id, p.url, p.takenAt, p.address)" +
            " FROM Photo p" +
            " WHERE p.coupleId = :coupleId AND p.takenAt >= :from AND p.takenAt < :toExclusive" +
            " ORDER BY p.takenAt DESC, p.id DESC",
    )
    fun findCalendarPhotos(
        coupleId: Long,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<CalendarPhoto>
}
