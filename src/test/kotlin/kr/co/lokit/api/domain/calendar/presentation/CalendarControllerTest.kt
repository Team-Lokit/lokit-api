package kr.co.lokit.api.domain.calendar.presentation

import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.calendar.application.port.`in`.GetCalendarUseCase
import kr.co.lokit.api.domain.calendar.domain.Anniversary
import kr.co.lokit.api.domain.calendar.domain.AnniversaryType
import kr.co.lokit.api.domain.calendar.domain.CalendarDay
import kr.co.lokit.api.domain.calendar.domain.CalendarMonth
import kr.co.lokit.api.domain.calendar.domain.CalendarPhoto
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.fixture.userAuth
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@WebMvcTest(CalendarController::class)
class CalendarControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

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
    lateinit var getCalendarUseCase: GetCalendarUseCase

    @Test
    fun `월간 캘린더를 조회한다`() {
        val month = YearMonth.of(2026, 11)
        whenever(getCalendarUseCase.getMonth(1L, month)).thenReturn(
            CalendarMonth(
                yearMonth = month,
                days =
                    listOf(
                        CalendarDay(
                            date = LocalDate.of(2026, 11, 2),
                            representativePhoto = CalendarPhoto(812L, "https://example.com/812.jpg", LocalDateTime.of(2026, 11, 2, 21, 10)),
                            anniversaries = emptyList(),
                        ),
                        CalendarDay(
                            date = LocalDate.of(2026, 11, 17),
                            representativePhoto = null,
                            anniversaries = listOf(Anniversary(LocalDate.of(2026, 11, 17), AnniversaryType.DAY_COUNT, 500)),
                        ),
                    ),
            ),
        )

        mockMvc
            .perform(get("/calendar").param("yearMonth", "2026-11").with(authentication(userAuth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.yearMonth").value("2026-11"))
            .andExpect(jsonPath("$.data.firstMetDate").doesNotExist())
            .andExpect(jsonPath("$.data.days[0].date").value("2026-11-02"))
            .andExpect(jsonPath("$.data.days[0].representativePhoto.id").value(812))
            .andExpect(jsonPath("$.data.days[0].representativePhoto.url").value("https://example.com/812.jpg"))
            .andExpect(jsonPath("$.data.days[0].representativePhoto.takenAt").value("2026-11-02T21:10:00"))
            .andExpect(jsonPath("$.data.days[0].photoCount").doesNotExist())
            .andExpect(jsonPath("$.data.days[0].anniversaries").isEmpty)
            .andExpect(jsonPath("$.data.days[1].representativePhoto").isEmpty)
            .andExpect(jsonPath("$.data.days[1].anniversaries[0].type").value("DAY_COUNT"))
            .andExpect(jsonPath("$.data.days[1].anniversaries[0].value").value(500))
            .andExpect(jsonPath("$.data.days[1].anniversaries[0].label").doesNotExist())
    }

    @Test
    fun `잘못된 연월 형식이면 400을 반환한다`() {
        mockMvc
            .perform(get("/calendar").param("yearMonth", "2026-13").with(authentication(userAuth())))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `연월이 없으면 400을 반환한다`() {
        mockMvc
            .perform(get("/calendar").with(authentication(userAuth())))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `날짜별 사진 목록을 최신순으로 조회한다`() {
        val date = LocalDate.of(2026, 11, 2)
        whenever(getCalendarUseCase.getDayPhotos(1L, date)).thenReturn(
            listOf(
                CalendarPhoto(812L, "https://example.com/812.jpg", LocalDateTime.of(2026, 11, 2, 21, 10)),
                CalendarPhoto(700L, "https://example.com/700.jpg", LocalDateTime.of(2026, 11, 2, 9, 0)),
            ),
        )

        mockMvc
            .perform(get("/calendar/days/2026-11-02/photos").with(authentication(userAuth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.date").value("2026-11-02"))
            .andExpect(jsonPath("$.data.photos[0].id").value(812))
            .andExpect(jsonPath("$.data.photos[1].id").value(700))
    }

    @Test
    fun `잘못된 날짜 형식이면 400을 반환한다`() {
        mockMvc
            .perform(get("/calendar/days/2026-02-30/photos").with(authentication(userAuth())))
            .andExpect(status().isBadRequest)
    }
}
