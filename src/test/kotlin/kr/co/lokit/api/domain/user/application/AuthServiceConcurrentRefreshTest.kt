package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.security.RefreshTokenHasher
import kr.co.lokit.api.domain.notification.application.port.`in`.DeleteDeviceTokensUseCase
import kr.co.lokit.api.domain.user.domain.RefreshedTokens
import kr.co.lokit.api.domain.user.infrastructure.JpaRefreshTokenRepository
import kr.co.lokit.api.domain.user.infrastructure.JpaUserRepository
import kr.co.lokit.api.domain.user.infrastructure.RefreshTokenJpaRepository
import kr.co.lokit.api.domain.user.infrastructure.UserJpaRepository
import kr.co.lokit.api.fixture.createUserEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 같은 리프레시 토큰으로 동시에 갱신해도 모든 요청이 인증에 성공하고, 새 리프레시 토큰은 정확히 하나만 발급되는지 검증한다.
 * 실제 커밋·행 잠금이 필요하므로 테스트 트랜잭션을 끄고 직접 정리한다.
 * (JpaSliceTest 와 import 구성이 달라 컨텍스트를 따로 쓴다)
 */
@DataJpaTest
@Import(AuthService::class, JpaRefreshTokenRepository::class, JpaUserRepository::class, JwtTokenProvider::class, RefreshTokenHasher::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceConcurrentRefreshTest {
    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var refreshTokenRepository: JpaRefreshTokenRepository

    @Autowired
    lateinit var refreshTokenJpaRepository: RefreshTokenJpaRepository

    @Autowired
    lateinit var userJpaRepository: UserJpaRepository

    @MockitoBean
    lateinit var deleteDeviceTokensUseCase: DeleteDeviceTokensUseCase

    @AfterEach
    fun cleanUp() {
        refreshTokenJpaRepository.deleteAllInBatch()
        userJpaRepository.deleteAllInBatch()
    }

    @Test
    fun `같은 리프레시 토큰으로 동시에 갱신해도 모두 성공하고 새 리프레시 토큰은 하나만 발급된다`() {
        val user = userJpaRepository.save(createUserEntity(email = "concurrent@test.com"))
        refreshTokenRepository.save(user.nonNullId(), "old-token", LocalDateTime.now().plusDays(30))

        val threads = 8
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val futures =
            (1..threads).map {
                pool.submit<RefreshedTokens?> {
                    ready.countDown()
                    start.await()
                    authService.refreshIfValid("old-token")
                }
            }
        ready.await()
        start.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertTrue(results.all { it != null }, "모든 동시 요청이 액세스 토큰을 받아야 한다")
        val issuedRefreshTokens = results.mapNotNull { it?.refreshToken }
        assertEquals(1, issuedRefreshTokens.size, "새 리프레시 토큰은 한 요청만 발급해야 한다")

        // 발급된 새 토큰은 DB 에 실제로 저장돼 있어 다음 갱신에 쓸 수 있어야 한다
        val next = authService.refreshIfValid(issuedRefreshTokens.single())
        assertNotNull(next?.refreshToken)
    }
}
