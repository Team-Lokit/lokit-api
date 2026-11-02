package kr.co.lokit.api.domain.calendar.domain

import java.time.LocalDate

enum class AnniversaryType {
    /** 100일 단위 기념일 (100일, 200일, ...) */
    DAY_COUNT,

    /** 매년 같은 월·일 (1주년, 2주년, ...) */
    YEARLY,
}

data class Anniversary(
    val date: LocalDate,
    val type: AnniversaryType,
    val value: Int,
)
