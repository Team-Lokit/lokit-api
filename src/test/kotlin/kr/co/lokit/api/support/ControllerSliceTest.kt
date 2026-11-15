package kr.co.lokit.api.support

import kr.co.lokit.api.common.permission.PermissionService
import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.album.application.port.`in`.CreateAlbumUseCase
import kr.co.lokit.api.domain.album.application.port.`in`.GetAlbumUseCase
import kr.co.lokit.api.domain.album.application.port.`in`.UpdateAlbumUseCase
import kr.co.lokit.api.domain.album.presentation.AlbumController
import kr.co.lokit.api.domain.calendar.application.port.`in`.GetCalendarUseCase
import kr.co.lokit.api.domain.calendar.presentation.CalendarController
import kr.co.lokit.api.domain.couple.application.CoupleCommandService
import kr.co.lokit.api.domain.couple.application.CoupleCookieStatusResolver
import kr.co.lokit.api.domain.couple.application.port.`in`.CoupleInviteUseCase
import kr.co.lokit.api.domain.couple.application.port.`in`.CreateCoupleUseCase
import kr.co.lokit.api.domain.couple.application.port.`in`.DisconnectCoupleUseCase
import kr.co.lokit.api.domain.couple.application.port.`in`.ReconnectCoupleUseCase
import kr.co.lokit.api.domain.couple.presentation.CoupleController
import kr.co.lokit.api.domain.map.application.port.`in`.GetMapUseCase
import kr.co.lokit.api.domain.map.application.port.`in`.SearchLocationUseCase
import kr.co.lokit.api.domain.map.presentation.MapController
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationInboxUseCase
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationSettingsUseCase
import kr.co.lokit.api.domain.notification.application.port.`in`.RegisterDeviceTokenUseCase
import kr.co.lokit.api.domain.notification.presentation.DeviceTokenController
import kr.co.lokit.api.domain.notification.presentation.NotificationController
import kr.co.lokit.api.domain.notification.presentation.NotificationSettingsController
import kr.co.lokit.api.domain.photo.application.port.`in`.CommentUseCase
import kr.co.lokit.api.domain.photo.application.port.`in`.CreatePhotoUseCase
import kr.co.lokit.api.domain.photo.application.port.`in`.EmoticonUseCase
import kr.co.lokit.api.domain.photo.application.port.`in`.GetPhotoDetailUseCase
import kr.co.lokit.api.domain.photo.application.port.`in`.UpdatePhotoUseCase
import kr.co.lokit.api.domain.photo.presentation.CommentController
import kr.co.lokit.api.domain.photo.presentation.PhotoController
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.domain.user.application.OAuthLoginServiceRegistry
import kr.co.lokit.api.domain.user.application.port.`in`.GetMyPageUseCase
import kr.co.lokit.api.domain.user.application.port.`in`.UpdateMyPageUseCase
import kr.co.lokit.api.domain.user.application.port.`in`.WithdrawUseCase
import kr.co.lokit.api.domain.user.infrastructure.oauth.apple.AppleOAuthProperties
import kr.co.lokit.api.domain.user.infrastructure.oauth.kakao.KakaoOAuthProperties
import kr.co.lokit.api.domain.user.presentation.AuthController
import kr.co.lokit.api.domain.user.presentation.MyPageController
import kr.co.lokit.api.domain.user.presentation.UserController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.cache.CacheManager
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc

/**
 * 컨트롤러 슬라이스 테스트 공통 베이스.
 *
 * 테스트 클래스마다 `@WebMvcTest(X::class)` + 서로 다른 `@MockitoBean` 조합을 쓰면 클래스 수만큼
 * Spring 컨텍스트가 따로 뜨고 캐시에 쌓인다(느리고, 단일 fork 에서는 -Xmx512m 를 넘겨 OOM).
 * 모든 컨트롤러와 목을 여기 한 곳에 모아 **컨텍스트 하나**를 공유한다.
 *
 * - 목은 매 테스트 후 자동 reset(MockReset.AFTER)되므로 클래스 간 스텁이 새지 않는다.
 * - 새 컨트롤러 테스트: 컨트롤러를 [WebMvcTest.controllers] 에, 필요한 목을 아래 필드에 추가하고 상속한다.
 *   서브클래스에 `@MockitoBean` / `@Import` / `@TestPropertySource` 를 붙이면 컨텍스트가 다시 갈라진다.
 * - 보안 설정(SecurityConfig)을 실제로 올려야 하는 테스트는 별도 컨텍스트로 둔다(AuthControllerLogoutSecurityTest).
 */
@WebMvcTest(
    controllers = [
        AlbumController::class,
        AuthController::class,
        CalendarController::class,
        CommentController::class,
        CoupleController::class,
        DeviceTokenController::class,
        MapController::class,
        MyPageController::class,
        NotificationController::class,
        NotificationSettingsController::class,
        PhotoController::class,
        UserController::class,
    ],
)
abstract class ControllerSliceTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    // 공통(인증/쿠키)
    @MockitoBean lateinit var compositeAuthenticationResolver: CompositeAuthenticationResolver
    @MockitoBean lateinit var authService: AuthService
    @MockitoBean lateinit var jwtTokenProvider: JwtTokenProvider
    @MockitoBean lateinit var cookieProperties: CookieProperties
    @MockitoBean lateinit var cookieGenerator: CookieGenerator
    @MockitoBean lateinit var permissionService: PermissionService
    @MockitoBean lateinit var cacheManager: CacheManager

    // album
    @MockitoBean lateinit var createAlbumUseCase: CreateAlbumUseCase
    @MockitoBean lateinit var getAlbumUseCase: GetAlbumUseCase
    @MockitoBean lateinit var updateAlbumUseCase: UpdateAlbumUseCase

    // auth / user
    @MockitoBean lateinit var loginServiceRegistry: OAuthLoginServiceRegistry
    @MockitoBean lateinit var appleOAuthProperties: AppleOAuthProperties
    @MockitoBean lateinit var kakaoOAuthProperties: KakaoOAuthProperties
    @MockitoBean lateinit var updateMyPageUseCase: UpdateMyPageUseCase
    @MockitoBean lateinit var getMyPageUseCase: GetMyPageUseCase
    @MockitoBean lateinit var withdrawUseCase: WithdrawUseCase

    // calendar
    @MockitoBean lateinit var getCalendarUseCase: GetCalendarUseCase

    // couple
    @MockitoBean lateinit var coupleCookieStatusResolver: CoupleCookieStatusResolver
    @MockitoBean lateinit var createCoupleUseCase: CreateCoupleUseCase
    @MockitoBean lateinit var disconnectCoupleUseCase: DisconnectCoupleUseCase
    @MockitoBean lateinit var reconnectCoupleUseCase: ReconnectCoupleUseCase
    @MockitoBean lateinit var coupleInviteUseCase: CoupleInviteUseCase
    @MockitoBean lateinit var coupleCommandService: CoupleCommandService

    // map
    @MockitoBean lateinit var getMapUseCase: GetMapUseCase
    @MockitoBean lateinit var searchLocationUseCase: SearchLocationUseCase

    // notification
    @MockitoBean lateinit var notificationInboxUseCase: NotificationInboxUseCase
    @MockitoBean lateinit var notificationSettingsUseCase: NotificationSettingsUseCase
    @MockitoBean lateinit var registerDeviceTokenUseCase: RegisterDeviceTokenUseCase

    // photo / comment
    @MockitoBean lateinit var createPhotoUseCase: CreatePhotoUseCase
    @MockitoBean lateinit var getPhotoDetailUseCase: GetPhotoDetailUseCase
    @MockitoBean lateinit var updatePhotoUseCase: UpdatePhotoUseCase
    @MockitoBean lateinit var commentUseCase: CommentUseCase
    @MockitoBean lateinit var emoticonUseCase: EmoticonUseCase
}
