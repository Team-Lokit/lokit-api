package kr.co.lokit.api.domain.map.application.port

import java.time.LocalDateTime

data class PhotoProjection(
    val id: Long,
    val url: String,
    val longitude: Double,
    val latitude: Double,
    val takenAt: LocalDateTime,
)

data class ClusterPhotoProjection(
    val id: Long,
    val url: String,
    val longitude: Double,
    val latitude: Double,
    val takenAt: LocalDateTime,
    val address: String,
)
