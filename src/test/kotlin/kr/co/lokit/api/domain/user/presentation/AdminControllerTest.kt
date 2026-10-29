package kr.co.lokit.api.domain.user.presentation

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.domain.couple.application.CoupleCommandService
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.couple.application.port.`in`.CoupleInviteUseCase
import kr.co.lokit.api.domain.couple.domain.InviteCodeIssueReadModel
import kr.co.lokit.api.domain.user.infrastructure.UserEntity
import kr.co.lokit.api.domain.user.infrastructure.UserJpaRepository
import kr.co.lokit.api.fixture.createCouple
import kr.co.lokit.api.fixture.createUserEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals

/**
 * 순수 단위 테스트. createCouplePartner 는 초대 코드 입력 없이 커플 연결까지 끝내야 한다.
 * 연결은 실제 초대 흐름(파트너 발급 → 호출자 합류)을 그대로 태워 부수효과(프로필 이미지·캐시)를 재사용한다.
 */
class AdminControllerTest {
    private val userJpaRepository: UserJpaRepository = mock()
    private val coupleCommandService: CoupleCommandService = mock()
    private val coupleInviteUseCase: CoupleInviteUseCase = mock()
    private val coupleRepository: CoupleRepositoryPort = mock()
    private val jwtTokenProvider: JwtTokenProvider = mock()

    private val controller =
        AdminController(
            userJpaRepository = userJpaRepository,
            createCoupleUseCase = coupleCommandService,
            coupleInviteUseCase = coupleInviteUseCase,
            refreshTokenRepository = mock(),
            coupleJpaRepository = mock(),
            albumJpaRepository = mock(),
            albumBoundsJpaRepository = mock(),
            coupleRepository = coupleRepository,
            albumRepository = mock(),
            photoRepository = mock(),
            commentRepository = mock(),
            emoticonRepository = mock(),
            jwtTokenProvider = jwtTokenProvider,
            cacheManager = mock(),
            adminKey = "admin",
        )

    private val httpRequest = MockHttpServletRequest().apply { remoteAddr = CLIENT_IP }

    @BeforeEach
    fun setUp() {
        whenever(userJpaRepository.findById(USER_ID)).thenReturn(Optional.of(createUserEntity(id = USER_ID)))
        whenever(userJpaRepository.findByEmail(PARTNER_EMAIL))
            .thenReturn(createUserEntity(id = PARTNER_ID, email = PARTNER_EMAIL))
        whenever(userJpaRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }
        whenever(jwtTokenProvider.generateAccessToken(any())).thenReturn("access")
        whenever(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh")
        whenever(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(60_000L)
    }

    @Test
    fun `커플이 없으면 파트너가 발급한 초대 코드로 호출자를 자동 합류시킨다`() {
        whenever(coupleRepository.findByUserId(USER_ID)).thenReturn(null)
        whenever(coupleInviteUseCase.generateInviteCode(PARTNER_ID))
            .thenReturn(InviteCodeIssueReadModel(inviteCode = INVITE_CODE, expiresAt = LocalDateTime.now()))

        val response = controller.createCouplePartner(USER_ID, httpRequest)

        verify(coupleCommandService).createIfNone(any(), anyOrNull())
        verify(coupleInviteUseCase).generateInviteCode(PARTNER_ID)
        verify(coupleInviteUseCase).joinByInviteCode(USER_ID, INVITE_CODE, CLIENT_IP)
        assertEquals(PARTNER_EMAIL, response.partnerEmail)
        assertEquals("access", response.tokens.accessToken)
    }

    @Test
    fun `이미 테스트 파트너와 연결돼 있으면 다시 합류시키지 않고 토큰만 발급한다`() {
        whenever(coupleRepository.findByUserId(USER_ID))
            .thenReturn(createCouple(id = 5L, userIds = listOf(USER_ID, PARTNER_ID)))

        val response = controller.createCouplePartner(USER_ID, httpRequest)

        verify(coupleInviteUseCase, never()).generateInviteCode(any())
        verify(coupleInviteUseCase, never()).joinByInviteCode(any(), any(), any())
        assertEquals(PARTNER_EMAIL, response.partnerEmail)
    }

    @Test
    fun `다른 사람과 연결돼 있으면 연결을 덮어쓰지 않고 거절한다`() {
        whenever(coupleRepository.findByUserId(USER_ID))
            .thenReturn(createCouple(id = 5L, userIds = listOf(USER_ID, OTHER_USER_ID)))

        assertThrows<BusinessException.InviteAlreadyCoupledException> {
            controller.createCouplePartner(USER_ID, httpRequest)
        }

        verify(coupleInviteUseCase, never()).generateInviteCode(any())
        verify(coupleInviteUseCase, never()).joinByInviteCode(any(), any(), any())
    }

    companion object {
        private const val USER_ID = 1L
        private const val PARTNER_ID = 2L
        private const val OTHER_USER_ID = 3L
        private const val PARTNER_EMAIL = "dev.partner.1@example.com"
        private const val INVITE_CODE = "123456"
        private const val CLIENT_IP = "10.0.0.1"
    }
}
