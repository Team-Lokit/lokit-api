package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.fixture.createCouple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoupleConnectionCheckerTest {
    private val coupleRepository: CoupleRepositoryPort = mock()
    private val checker = CoupleConnectionChecker(coupleRepository)

    @Test
    fun `연결된 커플의 두 멤버면 true`() {
        whenever(coupleRepository.findByUserIdFresh(A)).thenReturn(createCouple(id = 1L, userIds = listOf(A, B)))

        assertTrue(checker.areConnectedPartners(A, B))
    }

    @Test
    fun `연결이 끊긴 커플이면 false`() {
        whenever(coupleRepository.findByUserIdFresh(A))
            .thenReturn(createCouple(id = 1L, userIds = listOf(A, B), status = CoupleStatus.DISCONNECTED))

        assertFalse(checker.areConnectedPartners(A, B))
    }

    @Test
    fun `상대가 이미 커플에서 빠졌으면 false`() {
        whenever(coupleRepository.findByUserIdFresh(A)).thenReturn(createCouple(id = 1L, userIds = listOf(A)))

        assertFalse(checker.areConnectedPartners(A, B))
    }

    @Test
    fun `다른 사람과 연결된 커플이면 false`() {
        whenever(coupleRepository.findByUserIdFresh(A)).thenReturn(createCouple(id = 2L, userIds = listOf(A, C)))

        assertFalse(checker.areConnectedPartners(A, B))
    }

    @Test
    fun `커플이 없으면 false`() {
        whenever(coupleRepository.findByUserIdFresh(A)).thenReturn(null)

        assertFalse(checker.areConnectedPartners(A, B))
    }

    companion object {
        private const val A = 1L
        private const val B = 2L
        private const val C = 3L
    }
}
