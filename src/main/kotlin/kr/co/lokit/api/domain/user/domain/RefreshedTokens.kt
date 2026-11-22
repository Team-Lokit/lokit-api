package kr.co.lokit.api.domain.user.domain

/**
 * 리프레시 결과. refreshToken 이 null 이면 이미 다른 요청이 교체한 토큰으로 유예 시간 안에 들어온 경우이며,
 * 액세스 토큰만 새로 발급한다 — 리프레시 쿠키는 먼저 교체한 요청이 내려준 값을 그대로 둬야 한다.
 */
data class RefreshedTokens(
    val accessToken: String,
    val refreshToken: String?,
)
