package kr.co.lokit.api.domain.calendar.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import kr.co.lokit.api.domain.album.application.port.AlbumRepositoryPort
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
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class CalendarQueryService(
    private val coupleRepository: CoupleRepositoryPort,
    private val albumRepository: AlbumRepositoryPort,
    private val calendarPhotoQueryPort: CalendarPhotoQueryPort,
) : GetCalendarUseCase {
    @Transactional(readOnly = true)
    override fun getMonth(
        userId: Long,
        yearMonth: YearMonth,
        albumId: Long?,
    ): CalendarMonth {
        val couple = requireCouple(userId)
        val photos =
            findPhotos(
                couple = couple,
                userId = userId,
                albumId = albumId,
                from = yearMonth.atDay(1).atStartOfDay(),
                toExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(),
            )
        return CalendarMonth.of(yearMonth, photos, anniversariesOf(couple, yearMonth))
    }

    @Transactional(readOnly = true)
    override fun getDayPhotos(
        userId: Long,
        date: LocalDate,
        albumId: Long?,
    ): List<CalendarPhoto> {
        val couple = requireCouple(userId)
        val photos =
            findPhotos(
                couple = couple,
                userId = userId,
                albumId = albumId,
                from = date.atStartOfDay(),
                toExclusive = date.plusDays(1).atStartOfDay(),
            )
        return CalendarPhoto.sortLatestFirst(photos)
    }

    /** 기본 앨범은 커플의 전체 사진을 보여주므로, albumId 가 없거나 기본 앨범이면 커플 범위로 조회한다. */
    private fun findPhotos(
        couple: Couple,
        userId: Long,
        albumId: Long?,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<CalendarPhoto> {
        val targetAlbumId = albumId?.let { resolveNonDefaultAlbumId(couple, userId, it) }
        return if (targetAlbumId == null) {
            calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(couple.id, from, toExclusive)
        } else {
            calendarPhotoQueryPort.findByAlbumIdAndTakenAtRange(targetAlbumId, from, toExclusive)
        }
    }

    /** 커플의 앨범인지 검증하고, 기본 앨범이면 null 을 돌려준다. */
    private fun resolveNonDefaultAlbumId(
        couple: Couple,
        userId: Long,
        albumId: Long,
    ): Long? {
        val album =
            albumRepository.findById(albumId)
                ?: throw BusinessException.ResourceNotFoundException(
                    errors = errorDetailsOf(ErrorField.ALBUM_ID to albumId),
                )
        if (album.coupleId != couple.id) {
            throw BusinessException.ForbiddenException(
                errors = errorDetailsOf(ErrorField.USER_ID to userId, ErrorField.ALBUM_ID to albumId),
            )
        }
        return if (album.isDefault) null else album.id
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
