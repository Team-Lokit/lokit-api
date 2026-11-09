package kr.co.lokit.api.domain.map.application.port

interface RegionResolverPort {
    fun resolveEupMyeonDongCode(
        longitude: Double,
        latitude: Double,
    ): String?
}
