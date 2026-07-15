package kr.co.lokit.api.domain.map.presentation.mapping

import kr.co.lokit.api.domain.map.domain.*
import kr.co.lokit.api.domain.map.dto.*

fun MapMeReadModel.toLegacyResponse(): LegacyMapMeResponse =
    LegacyMapMeResponse(
        location = location.toResponse(),
        boundingBox = boundingBox.toResponse(),
        totalHistoryCount = totalHistoryCount,
        albums = albums.asList().map { it.toResponse() },
        dataVersion = dataVersion,
        clusters = clusters?.asList()?.map { it.toResponse() },
        photos = photos?.asList()?.map { it.toResponse() },
        profileImageUrl = profileImageUrl,
    )

fun MapMeReadModel.toResponse(): MapMeResponse =
    MapMeResponse(
        boundingBox = boundingBox.toResponse(),
        totalHistoryCount = totalHistoryCount,
        albums = albums.asList().map { it.toResponse() },
        dataVersion = dataVersion,
        clusters = clusters?.asList()?.map { it.toResponse() },
        photos = photos?.asList()?.map { it.toResponse() },
        profileImageUrl = profileImageUrl,
        userId = userId,
    )

fun ClusterPhotos.toResponse(): List<ClusterPhotoResponse> = asList().map { it.toResponse() }

fun AlbumMapInfoReadModel.toResponse(): AlbumMapInfoResponse =
    AlbumMapInfoResponse(
        albumId = albumId,
        centerLongitude = centerLongitude,
        centerLatitude = centerLatitude,
        boundingBox = boundingBox?.toResponse(),
    )

fun LocationInfoReadModel.toResponse(): LocationInfoResponse =
    LocationInfoResponse(
        address = address,
        roadName = roadName,
        placeName = placeName,
        regionName = regionName,
    )

fun PlaceSearchReadModel.toResponse(): PlaceSearchResponse =
    PlaceSearchResponse(places = places.asList().map { it.toResponse() })

private fun ClusterReadModel.toResponse(): ClusterResponse =
    ClusterResponse(
        clusterId = clusterId,
        count = count,
        thumbnailUrl = thumbnailUrl,
        longitude = longitude,
        latitude = latitude,
        takenAt = takenAt,
    )

private fun MapPhotoReadModel.toResponse(): MapPhotoResponse =
    MapPhotoResponse(
        id = id,
        thumbnailUrl = thumbnailUrl,
        longitude = longitude,
        latitude = latitude,
        takenAt = takenAt,
    )

private fun ClusterPhotoReadModel.toResponse(): ClusterPhotoResponse =
    ClusterPhotoResponse(
        id = id,
        url = url,
        longitude = longitude,
        latitude = latitude,
        takenAt = takenAt,
        address = address,
    )

private fun BoundingBoxReadModel.toResponse(): BoundingBoxResponse =
    BoundingBoxResponse(
        west = west,
        south = south,
        east = east,
        north = north,
    )

private fun PlaceReadModel.toResponse(): PlaceResponse =
    PlaceResponse(
        placeName = placeName,
        address = address,
        roadAddress = roadAddress,
        longitude = longitude,
        latitude = latitude,
        category = category,
    )

private fun AlbumThumbnailsReadModel.toResponse(): HomeResponse.Companion.AlbumThumbnails =
    HomeResponse.Companion.AlbumThumbnails(
        id = id,
        title = title,
        photoCount = photoCount,
        thumbnailUrls = thumbnailUrls.asList(),
    )
