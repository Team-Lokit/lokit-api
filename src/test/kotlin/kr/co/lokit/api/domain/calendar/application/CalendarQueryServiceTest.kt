package kr.co.lokit.api.domain.calendar.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.calendar.application.port.CalendarPhotoQueryPort
import kr.co.lokit.api.domain.calendar.domain.Anniversary
import kr.co.lokit.api.domain.calendar.domain.AnniversaryType
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.fixture.createCouple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.given
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
}
