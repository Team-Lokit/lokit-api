package kr.co.lokit.api.domain.calendar.domain

import java.time.LocalDateTime

/** takenAt: 캘린더 배치 기준 시각 (촬영 시각, 없으면 업로드 시각으로 저장됨) */
data class CalendarPhoto(
    val id: Long,
    val url: String,
    val takenAt: LocalDateTime,
) {
    companion object {
        /** 기준 시각 최신순, 같으면 id 내림차순. 월간 대표 사진과 날짜별 목록의 첫 장이 항상 일치하도록 한 곳에서 정의한다. */
        val LATEST_FIRST: Comparator<CalendarPhoto> =
            compareByDescending<CalendarPhoto> { it.takenAt }.thenByDescending { it.id }

        fun sortLatestFirst(photos: List<CalendarPhoto>): List<CalendarPhoto> = photos.sortedWith(LATEST_FIRST)
    }
}
