package kr.co.lokit.api.domain.user.presentation

import kr.co.lokit.api.common.constants.DomainCookie
import kr.co.lokit.api.fixture.userAuth
import kr.co.lokit.api.support.ControllerSliceTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class UserControllerTest : ControllerSliceTest() {
    @Test
    fun `회원 탈퇴 성공`() {
        whenever(cookieGenerator.clearAccessTokenCookie(any())).thenReturn(
            ResponseCookie.from(DomainCookie.ACCESS_TOKEN.value, "").maxAge(0).build(),
        )
        whenever(cookieGenerator.clearRefreshTokenCookie(any())).thenReturn(
            ResponseCookie.from(DomainCookie.REFRESH_TOKEN.value, "").maxAge(0).build(),
        )
        whenever(cookieGenerator.clearCoupleStatusCookie(any())).thenReturn(
            ResponseCookie.from(DomainCookie.COUPLE_STATUS.value, "").maxAge(0).build(),
        )

        mockMvc
            .perform(
                delete("/users/me")
                    .with(authentication(userAuth()))
                    .with(csrf()),
            ).andExpect(status().isNoContent)
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `인증되지 않은 사용자는 탈퇴할 수 없다`() {
        mockMvc
            .perform(
                delete("/users/me")
                    .with(csrf()),
            ).andExpect(status().isUnauthorized)
    }
}
