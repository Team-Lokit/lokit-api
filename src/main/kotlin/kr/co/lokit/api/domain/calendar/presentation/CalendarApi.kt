package kr.co.lokit.api.domain.calendar.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kr.co.lokit.api.domain.calendar.dto.CalendarDayPhotosResponse
import kr.co.lokit.api.domain.calendar.dto.CalendarMonthResponse
import java.time.LocalDate
import java.time.YearMonth

@SecurityRequirement(name = "Authorization")
@Tag(name = "Calendar", description = "캘린더 API")
interface CalendarApi {
    @Operation(
        summary = "월간 캘린더 조회",
        description = """
            해당 월에 사진 또는 기념일이 있는 날짜 목록을 조회합니다.

            - 사진은 기준 시각(촬영 시각, 없으면 업로드 시각)의 날짜에 배치됩니다.
            - 대표 사진은 같은 날짜 사진 중 기준 시각 최신(같으면 나중에 등록된) 1장입니다.
            - 기념일은 커플이 된 날짜를 1일째로 계산한 100일 단위 기념일과 N주년입니다.
              커플이 된 날짜가 없거나 연결된 커플이 아니면 생성하지 않습니다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = CalendarMonthResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "연월 형식 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
            ApiResponse(responseCode = "404", description = "커플을 찾을 수 없음 (COUPLE_003)", content = [Content()]),
        ],
    )
    fun getMonth(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회 연월 (yyyy-MM)", example = "2026-11", required = true, schema = Schema(type = "string"))
        yearMonth: YearMonth,
    ): CalendarMonthResponse

    @Operation(
        summary = "날짜별 사진 목록 조회",
        description = """
            해당 날짜의 사진을 기준 시각 최신순으로 조회합니다.
            첫 번째 사진은 월간 캘린더의 대표 사진과 같습니다. 사진 상세는 GET /photos/{id} 로 조회합니다.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = CalendarDayPhotosResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "날짜 형식 오류", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
            ApiResponse(responseCode = "404", description = "커플을 찾을 수 없음 (COUPLE_003)", content = [Content()]),
        ],
    )
    fun getDayPhotos(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "날짜 (yyyy-MM-dd)", example = "2026-11-02", required = true)
        date: LocalDate,
    ): CalendarDayPhotosResponse
}
