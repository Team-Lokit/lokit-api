package kr.co.lokit.api.domain.notification.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationSettingsUseCase
import kr.co.lokit.api.domain.notification.domain.NotificationSettings
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.fixture.userAuth
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(NotificationSettingsController::class)
class NotificationSettingsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    val objectMapper: ObjectMapper = ObjectMapper()

    @MockitoBean
    lateinit var compositeAuthenticationResolver: CompositeAuthenticationResolver

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    @MockitoBean
    lateinit var cookieProperties: CookieProperties

    @MockitoBean
    lateinit var cookieGenerator: CookieGenerator

    @MockitoBean
    lateinit var notificationSettingsUseCase: NotificationSettingsUseCase

    // F1
    @Test
    fun `설정을 저장한 적 없는 사용자도 모든 종류의 값을 명시적으로 내려받는다`() {
        whenever(notificationSettingsUseCase.getSettings(1L))
            .thenReturn(NotificationSettings.defaultsFor(1L))

        mockMvc.perform(
            get("/notification-settings")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.masterEnabled").value(true))
            .andExpect(jsonPath("$.data.types.COMMENT").value(true))
            .andExpect(jsonPath("$.data.types.REACTION").value(true))
            .andExpect(jsonPath("$.data.types.UPLOAD").value(true))
    }

    // F2
    @Test
    fun `마스터만 보내면 종류별 변경 없이 유스케이스를 호출한다`() {
        whenever(notificationSettingsUseCase.updateSettings(any(), anyOrNull(), any()))
            .thenReturn(NotificationSettings(userId = 1L, masterEnabled = false))

        mockMvc.perform(
            patch("/notification-settings")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("masterEnabled" to false))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.masterEnabled").value(false))

        verify(notificationSettingsUseCase).updateSettings(
            userId = 1L,
            masterEnabled = false,
            typeToggles = emptyMap(),
        )
    }

    // F3
    @Test
    fun `종류만 보내면 마스터 변경 없이 유스케이스를 호출한다`() {
        whenever(notificationSettingsUseCase.updateSettings(any(), anyOrNull(), any()))
            .thenReturn(
                NotificationSettings(
                    userId = 1L,
                    masterEnabled = true,
                    disabledTypes = setOf(NotificationType.COMMENT),
                ),
            )

        mockMvc.perform(
            patch("/notification-settings")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("types" to mapOf("COMMENT" to false)),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.masterEnabled").value(true))
            .andExpect(jsonPath("$.data.types.COMMENT").value(false))
            .andExpect(jsonPath("$.data.types.REACTION").value(true))

        verify(notificationSettingsUseCase).updateSettings(
            userId = 1L,
            masterEnabled = null,
            typeToggles = mapOf(NotificationType.COMMENT to false),
        )
    }

    // F4
    @Test
    fun `빈 바디도 200이고 현재 설정을 그대로 내려준다`() {
        whenever(notificationSettingsUseCase.updateSettings(any(), anyOrNull(), any()))
            .thenReturn(
                NotificationSettings(
                    userId = 1L,
                    masterEnabled = true,
                    disabledTypes = setOf(NotificationType.UPLOAD),
                ),
            )

        mockMvc.perform(
            patch("/notification-settings")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.masterEnabled").value(true))
            .andExpect(jsonPath("$.data.types.COMMENT").value(true))
            .andExpect(jsonPath("$.data.types.UPLOAD").value(false))

        verify(notificationSettingsUseCase).updateSettings(
            userId = 1L,
            masterEnabled = null,
            typeToggles = emptyMap(),
        )
    }

    // F5 — 계약 Q6: 400 인지 500 인지 실행으로 확인해야 한다. 여기서는 400 을 기대로 고정한다.
    @Test
    fun `알 수 없는 알림 종류 키를 보내면 400을 반환한다`() {
        mockMvc.perform(
            patch("/notification-settings")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("types" to mapOf("FOO" to false)),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    // F6
    @Test
    fun `인증되지 않은 사용자는 설정을 조회할 수 없다`() {
        mockMvc.perform(get("/notification-settings"))
            .andExpect(status().isUnauthorized)
    }

    // F7
    @Test
    fun `인증되지 않은 사용자는 설정을 변경할 수 없다`() {
        mockMvc.perform(
            patch("/notification-settings")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("masterEnabled" to false))),
        )
            .andExpect(status().isUnauthorized)
    }
}
