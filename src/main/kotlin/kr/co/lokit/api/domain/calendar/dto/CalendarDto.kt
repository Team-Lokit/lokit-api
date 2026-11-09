package kr.co.lokit.api.domain.calendar.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.co.lokit.api.domain.calendar.domain.AnniversaryType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Schema(description = "월간 캘린더 응답")
data class CalendarMonthResponse(
    @Schema(description = "조회 연월", example = "2026-11", type = "string")
    val yearMonth: YearMonth,
    @Schema(description = "사진 또는 기념일이 있는 날짜 목록 (날짜 오름차순). 빈 날짜는 포함하지 않는다.")
    val days: List<CalendarDayResponse>,
)

@Schema(description = "캘린더 날짜")
data class CalendarDayResponse(
    @Schema(description = "날짜", example = "2026-11-02")
    val date: LocalDate,
    @Schema(description = "대표 사진 (기준 시각 최신 1장). 사진이 없으면 null", nullable = true)
    val representativePhoto: CalendarPhotoResponse?,
    @Schema(description = "기념일 목록. 없으면 빈 배열")
    val anniversaries: List<AnniversaryResponse>,
)

@Schema(description = "캘린더 사진")
data class CalendarPhotoResponse(
    @Schema(description = "사진 ID", example = "812")
    val id: Long,
    @Schema(description = "사진 URL", example = "https://bucket.s3.amazonaws.com/photos/1/image.jpg")
    val url: String,
    @Schema(description = "기준 시각 (촬영 시각, 없으면 업로드 시각)", example = "2026-11-02T21:10:00")
    val takenAt: LocalDateTime,
)

@Schema(description = "기념일. 표시 문구는 클라이언트가 type/value 로 만든다 (DAY_COUNT 100 → '100일', YEARLY 1 → '1주년')")
data class AnniversaryResponse(
    @Schema(description = "기념일 종류 (DAY_COUNT: 100일 단위, YEARLY: N주년)", example = "DAY_COUNT")
    val type: AnniversaryType,
    @Schema(description = "DAY_COUNT 면 일수, YEARLY 면 연차", example = "100")
    val value: Int,
)

@Schema(description = "날짜별 사진 목록 응답")
data class CalendarDayPhotosResponse(
    @Schema(description = "날짜", example = "2026-11-02")
    val date: LocalDate,
    @Schema(description = "기준 시각 최신순 사진 목록. 첫 번째 사진이 월간 캘린더의 대표 사진과 같다.")
    val photos: List<CalendarPhotoResponse>,
)
