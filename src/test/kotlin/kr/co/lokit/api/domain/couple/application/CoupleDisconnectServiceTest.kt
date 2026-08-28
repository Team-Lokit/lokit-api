package kr.co.lokit.api.domain.couple.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.couple.application.port.`in`.CreateCoupleUseCase
import kr.co.lokit.api.domain.notification.application.port.`in`.CancelPendingUploadNotificationsUseCase
import kr.co.lokit.api.fixture.createCouple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

@ExtendWith(MockitoExtension::class)
class CoupleDisconnectServiceTest {
    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

    @Mock
    lateinit var createCoupleUseCase: CreateCoupleUseCase

    @Mock
    lateinit var cacheManager: CacheManager

    @Mock
    lateinit var cache: Cache

    @Mock
    lateinit var cancelPendingUploadNotificationsUseCase: CancelPendingUploadNotificationsUseCase

    @InjectMocks
    lateinit var coupleDisconnectService: CoupleDisconnectService

    @Test
    fun `커플 연결을 끊을 수 있다`() {
        val couple = createCouple(
            id = 1L,
            name = "우리 커플",
            userIds = listOf(1L, 2L),
            status = CoupleStatus.CONNECTED,
        )
        `when`(coupleRepository.findByUserId(1L)).thenReturn(couple)
//        `when`(cacheManager.getCache("userCouple")).thenReturn(cache)

        coupleDisconnectService.disconnect(1L)

        verify(coupleRepository).disconnect(1L, 1L)
        verify(coupleRepository).removeCoupleUser(1L)
        verify(coupleRepository, never()).deleteById(1L)
        verify(createCoupleUseCase).createIfNone(createCouple(name = "default"), 1L)
//        verify(cache).evict(1L)
//        verify(cache).evict(2L)
    }

    @Test
    fun `커플 연결을 끊으면 대기 중인 업로드 알림이 취소된다`() {
        val couple = createCouple(
            id = 1L,
            name = "우리 커플",
            userIds = listOf(1L, 2L),
            status = CoupleStatus.CONNECTED,
        )
        `when`(coupleRepository.findByUserId(1L)).thenReturn(couple)
        whenever(cancelPendingUploadNotificationsUseCase.cancelByCoupleId(any())).thenReturn(0)

        coupleDisconnectService.disconnect(1L)

        verify(cancelPendingUploadNotificationsUseCase).cancelByCoupleId(couple.id)
    }

    @Test
    fun `커플이 없으면 취소를 호출하지 않는다`() {
        `when`(coupleRepository.findByUserId(1L)).thenReturn(null)

        assertThrows<BusinessException.CoupleNotFoundException> {
            coupleDisconnectService.disconnect(1L)
        }

        verify(cancelPendingUploadNotificationsUseCase, never()).cancelByCoupleId(any())
    }

    @Test
    fun `커플이 없으면 예외가 발생한다`() {
        `when`(coupleRepository.findByUserId(1L)).thenReturn(null)

        assertThrows<BusinessException.CoupleNotFoundException> {
            coupleDisconnectService.disconnect(1L)
        }
    }

    @Test
    fun `이미 연결 해제된 커플이면 예외가 발생한다`() {
        val couple = createCouple(
            id = 1L,
            name = "우리 커플",
            userIds = listOf(1L),
            status = CoupleStatus.DISCONNECTED,
            disconnectedByUserId = 1L,
        )
        `when`(coupleRepository.findByUserId(1L)).thenReturn(couple)

        assertThrows<BusinessException.CoupleAlreadyDisconnectedException> {
            coupleDisconnectService.disconnect(1L)
        }
    }

    @Test
    fun `이미 연결 해제된 상태에서 남아있는 사용자는 추가 연결 끊기가 가능하다`() {
        val couple = createCouple(
            id = 1L,
            name = "우리 커플",
            userIds = listOf(2L),
            status = CoupleStatus.DISCONNECTED,
            disconnectedByUserId = 1L,
        )
        `when`(coupleRepository.findByUserId(2L)).thenReturn(couple)
//        `when`(cacheManager.getCache("userCouple")).thenReturn(cache)

        coupleDisconnectService.disconnect(2L)

        verify(coupleRepository).removeCoupleUser(2L)
        verify(coupleRepository).deleteById(1L)
        verify(createCoupleUseCase).createIfNone(createCouple(name = "default"), 2L)
//        verify(cache).evict(2L)
    }
}
