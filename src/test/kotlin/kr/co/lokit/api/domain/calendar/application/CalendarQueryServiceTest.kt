package kr.co.lokit.api.domain.calendar.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.album.application.port.AlbumRepositoryPort
import kr.co.lokit.api.domain.calendar.application.port.CalendarPhotoQueryPort
import kr.co.lokit.api.domain.calendar.domain.Anniversary
import kr.co.lokit.api.domain.calendar.domain.AnniversaryType
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.fixture.createAlbum
import kr.co.lokit.api.fixture.createCouple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CalendarQueryServiceTest {
    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

    @Mock
    lateinit var albumRepository: AlbumRepositoryPort

    @Mock
    lateinit var calendarPhotoQueryPort: CalendarPhotoQueryPort

    @InjectMocks
    lateinit var service: CalendarQueryService

    private val userId = 1L
    private val coupleId = 10L
    private val month = YearMonth.of(2026, 4)
    private val monthStart = LocalDateTime.of(2026, 4, 1, 0, 0)
    private val nextMonthStart = LocalDateTime.of(2026, 5, 1, 0, 0)

    private fun photo(
        id: Long,
        takenAt: LocalDateTime,
    ) = CalendarPhoto(id, "https://example.com/$id.jpg", takenAt, "테헤란로")

    @Test
    fun `월간 조회는 해당 월 1일부터 다음 달 1일 전까지의 커플 사진으로 대표 사진을 만든다`() {
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(coupleId, monthStart, nextMonthStart))
            .willReturn(listOf(photo(2L, LocalDateTime.of(2026, 4, 3, 20, 0)), photo(1L, LocalDateTime.of(2026, 4, 3, 8, 0))))

        val result = service.getMonth(userId, month)

        verify(calendarPhotoQueryPort).findByCoupleIdAndTakenAtRange(coupleId, monthStart, nextMonthStart)
        assertEquals(month, result.yearMonth)
        assertEquals(2L, result.days.single().representativePhoto?.id)
    }

    @Test
    fun `커플이 된 날짜가 있고 연결된 커플이면 기념일을 포함한다`() {
        given(coupleRepository.findByUserId(userId)).willReturn(
            createCouple(id = coupleId, userIds = listOf(1L, 2L), firstMetDate = LocalDate.of(2026, 1, 1)),
        )
        given(calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(coupleId, monthStart, nextMonthStart)).willReturn(emptyList())

        val result = service.getMonth(userId, month)

        assertEquals(
            listOf(Anniversary(LocalDate.of(2026, 4, 10), AnniversaryType.DAY_COUNT, 100)),
            result.days.single().anniversaries,
        )
    }

    @Test
    fun `커플이 된 날짜가 없으면 기념일을 만들지 않는다`() {
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(coupleId, monthStart, nextMonthStart)).willReturn(emptyList())

        val result = service.getMonth(userId, month)

        assertTrue(result.days.isEmpty())
    }

    @Test
    fun `연결이 해제된 커플은 커플이 된 날짜가 있어도 기념일을 만들지 않는다`() {
        given(coupleRepository.findByUserId(userId)).willReturn(
            createCouple(
                id = coupleId,
                userIds = listOf(1L, 2L),
                status = CoupleStatus.DISCONNECTED,
                firstMetDate = LocalDate.of(2026, 1, 1),
            ),
        )
        given(calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(coupleId, monthStart, nextMonthStart)).willReturn(emptyList())

        val result = service.getMonth(userId, month)

        assertTrue(result.days.isEmpty())
    }

    @Test
    fun `상대가 없는 커플은 커플이 된 날짜가 있어도 기념일을 만들지 않는다`() {
        given(coupleRepository.findByUserId(userId)).willReturn(
            createCouple(id = coupleId, userIds = listOf(1L), firstMetDate = LocalDate.of(2026, 1, 1)),
        )
        given(calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(coupleId, monthStart, nextMonthStart)).willReturn(emptyList())

        val result = service.getMonth(userId, month)

        assertTrue(result.days.isEmpty())
    }

    @Test
    fun `날짜별 조회는 해당 날짜 0시부터 다음 날 0시 전까지의 사진을 최신순으로 반환한다`() {
        val date = LocalDate.of(2026, 4, 3)
        val t = LocalDateTime.of(2026, 4, 3, 9, 0)
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(coupleId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
            .willReturn(listOf(photo(1L, t), photo(3L, t.plusHours(1)), photo(2L, t)))

        val result = service.getDayPhotos(userId, date)

        assertEquals(listOf(3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun `커플이 없으면 예외가 발생한다`() {
        given(coupleRepository.findByUserId(userId)).willReturn(null)

        assertThrows<BusinessException.CoupleNotFoundException> { service.getMonth(userId, month) }
        assertThrows<BusinessException.CoupleNotFoundException> { service.getDayPhotos(userId, LocalDate.of(2026, 4, 3)) }
    }

    @Test
    fun `앨범을 지정하면 해당 앨범의 사진으로 월간 캘린더를 만든다`() {
        val albumId = 5L
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(albumRepository.findById(albumId)).willReturn(createAlbum(id = albumId, coupleId = coupleId))
        given(calendarPhotoQueryPort.findByAlbumIdAndTakenAtRange(albumId, monthStart, nextMonthStart))
            .willReturn(listOf(photo(7L, LocalDateTime.of(2026, 4, 3, 20, 0))))

        val result = service.getMonth(userId, month, albumId)

        verify(calendarPhotoQueryPort, never()).findByCoupleIdAndTakenAtRange(any(), any(), any())
        assertEquals(7L, result.days.single().representativePhoto?.id)
    }

    @Test
    fun `앨범을 지정해도 기념일은 포함한다`() {
        val albumId = 5L
        given(coupleRepository.findByUserId(userId)).willReturn(
            createCouple(id = coupleId, userIds = listOf(1L, 2L), firstMetDate = LocalDate.of(2026, 1, 1)),
        )
        given(albumRepository.findById(albumId)).willReturn(createAlbum(id = albumId, coupleId = coupleId))
        given(calendarPhotoQueryPort.findByAlbumIdAndTakenAtRange(albumId, monthStart, nextMonthStart)).willReturn(emptyList())

        val result = service.getMonth(userId, month, albumId)

        assertEquals(LocalDate.of(2026, 4, 10), result.days.single().date)
    }

    @Test
    fun `기본 앨범을 지정하면 커플의 전체 사진으로 조회한다`() {
        val defaultAlbumId = 4L
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(albumRepository.findById(defaultAlbumId))
            .willReturn(createAlbum(id = defaultAlbumId, coupleId = coupleId, isDefault = true))
        given(calendarPhotoQueryPort.findByCoupleIdAndTakenAtRange(coupleId, monthStart, nextMonthStart))
            .willReturn(listOf(photo(1L, LocalDateTime.of(2026, 4, 3, 8, 0))))

        val result = service.getMonth(userId, month, defaultAlbumId)

        verify(calendarPhotoQueryPort, never()).findByAlbumIdAndTakenAtRange(any(), any(), any())
        assertEquals(1L, result.days.single().representativePhoto?.id)
    }

    @Test
    fun `날짜별 조회도 앨범을 지정하면 해당 앨범의 사진만 최신순으로 반환한다`() {
        val albumId = 5L
        val date = LocalDate.of(2026, 4, 3)
        val t = LocalDateTime.of(2026, 4, 3, 9, 0)
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(albumRepository.findById(albumId)).willReturn(createAlbum(id = albumId, coupleId = coupleId))
        given(calendarPhotoQueryPort.findByAlbumIdAndTakenAtRange(albumId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
            .willReturn(listOf(photo(1L, t), photo(2L, t.plusHours(1))))

        val result = service.getDayPhotos(userId, date, albumId)

        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun `다른 커플의 앨범을 지정하면 예외가 발생한다`() {
        val albumId = 5L
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(albumRepository.findById(albumId)).willReturn(createAlbum(id = albumId, coupleId = 99L))

        assertThrows<BusinessException.ForbiddenException> { service.getMonth(userId, month, albumId) }
        assertThrows<BusinessException.ForbiddenException> { service.getDayPhotos(userId, LocalDate.of(2026, 4, 3), albumId) }
    }

    @Test
    fun `존재하지 않는 앨범을 지정하면 예외가 발생한다`() {
        val albumId = 5L
        given(coupleRepository.findByUserId(userId)).willReturn(createCouple(id = coupleId, userIds = listOf(1L, 2L)))
        given(albumRepository.findById(albumId)).willReturn(null)

        assertThrows<BusinessException.ResourceNotFoundException> { service.getMonth(userId, month, albumId) }
        assertThrows<BusinessException.ResourceNotFoundException> { service.getDayPhotos(userId, LocalDate.of(2026, 4, 3), albumId) }
    }
}
