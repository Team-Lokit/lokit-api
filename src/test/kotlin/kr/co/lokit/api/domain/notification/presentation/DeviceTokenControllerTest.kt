package kr.co.lokit.api.domain.notification.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.notification.application.port.`in`.RegisterDeviceTokenUseCase
import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.domain.notification.dto.RegisterDeviceTokenRequest
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.fixture.createDeviceToken
import kr.co.lokit.api.fixture.userAuth
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DeviceTokenController::class)
class DeviceTokenControllerTest {

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
    lateinit var registerDeviceTokenUseCase: RegisterDeviceTokenUseCase

    @Test
    fun `디바이스 토큰 등록 성공`() {
        whenever(registerDeviceTokenUseCase.register(any(), any(), any()))
            .thenReturn(createDeviceToken(id = 1L, userId = 1L, token = "fcm-1"))

        mockMvc.perform(
            post("/device-tokens")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        RegisterDeviceTokenRequest(token = "fcm-1", platform = DevicePlatform.ANDROID),
                    ),
                ),
        )
            .andExpect(status().isNoContent)

        verify(registerDeviceTokenUseCase).register(
            userId = 1L,
            token = "fcm-1",
            platform = DevicePlatform.ANDROID,
        )
    }

    @Test
    fun `디바이스 토큰 등록 실패 - 토큰이 비어있음`() {
        mockMvc.perform(
            post("/device-tokens")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("token" to "", "platform" to "ANDROID"),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `지원하지 않는 플랫폼 값이면 400을 반환한다`() {
        mockMvc.perform(
            post("/device-tokens")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("token" to "fcm-1", "platform" to "WINDOWS"),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `인증되지 않은 사용자는 디바이스 토큰을 등록할 수 없다`() {
        mockMvc.perform(
            post("/device-tokens")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("token" to "fcm-1", "platform" to "ANDROID"),
                    ),
                ),
        )
            .andExpect(status().isUnauthorized)
    }
}
