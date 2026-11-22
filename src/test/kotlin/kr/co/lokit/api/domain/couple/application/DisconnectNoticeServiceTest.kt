package kr.co.lokit.api.domain.couple.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.fixture.createCouple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class DisconnectNoticeServiceTest {
    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

    @InjectMocks
    lateinit var service: DisconnectNoticeService

    private fun disconnectedByPartner(confirmedAt: LocalDateTime? = null) =
        createCouple(
            id = 10L,
            userIds = listOf(1L),
            status = CoupleStatus.DISCONNECTED,
            disconnectedAt = LocalDateTime.now().minusDays(1),
            disconnectedByUserId = 2L,
        ).copy(disconnectNoticeConfirmedAt = confirmedAt)

    @Test
    fun `상대가 끊었고 확인 전이면 알림창을 보여준다`() {
        `when`(coupleRepository.findByUserIdFresh(1L)).thenReturn(disconnectedByPartner())

        assertTrue(service.shouldShowDisconnectNotice(1L))
    }

    @Test
    fun `이미 확인했으면 알림창을 보여주지 않는다`() {
        `when`(coupleRepository.findByUserIdFresh(1L)).thenReturn(disconnectedByPartner(LocalDateTime.now()))

        assertFalse(service.shouldShowDisconnectNotice(1L))
    }

    @Test
    fun `내가 끊어 새 기본 커플에 있으면 알림창을 보여주지 않는다`() {
        `when`(coupleRepository.findByUserIdFresh(2L)).thenReturn(createCouple(id = 20L, userIds = listOf(2L)))

        assertFalse(service.shouldShowDisconnectNotice(2L))
    }

    @Test
    fun `커플이 없으면 알림창을 보여주지 않는다`() {
        `when`(coupleRepository.findByUserIdFresh(1L)).thenReturn(null)

        assertFalse(service.shouldShowDisconnectNotice(1L))
    }

    @Test
    fun `확인 처리하면 끊긴 커플에 확인 시각을 기록한다`() {
        `when`(coupleRepository.findByUserIdFresh(1L)).thenReturn(disconnectedByPartner())

        service.confirmDisconnectNotice(1L)

        verify(coupleRepository).confirmDisconnectNotice(10L)
    }

    @Test
    fun `보여줄 알림창이 없으면 확인 처리는 아무것도 하지 않는다`() {
        `when`(coupleRepository.findByUserIdFresh(1L)).thenReturn(disconnectedByPartner(LocalDateTime.now()))

        service.confirmDisconnectNotice(1L)

        verify(coupleRepository, never()).confirmDisconnectNotice(anyLong())
    }
}
