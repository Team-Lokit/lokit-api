package kr.co.lokit.api.domain.user.presentation

import jakarta.servlet.http.HttpServletRequest
import kr.co.lokit.api.common.annotation.CurrentUserId
import kr.co.lokit.api.common.annotation.SyncCoupleStatusCookie
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.config.cache.clearAllCaches
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.domain.album.application.port.AlbumRepositoryPort
import kr.co.lokit.api.domain.album.domain.Album
import kr.co.lokit.api.domain.album.infrastructure.AlbumJpaRepository
import kr.co.lokit.api.domain.couple.application.CoupleCommandService
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.couple.application.port.`in`.CoupleInviteUseCase
import kr.co.lokit.api.domain.couple.domain.Couple
import kr.co.lokit.api.domain.couple.infrastructure.CoupleJpaRepository
import kr.co.lokit.api.domain.map.infrastructure.AlbumBoundsJpaRepository
import kr.co.lokit.api.domain.photo.application.port.CommentRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.EmoticonRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRepositoryPort
import kr.co.lokit.api.domain.user.domain.AuthTokens
import kr.co.lokit.api.domain.user.domain.User
import kr.co.lokit.api.domain.user.dto.AdminActionResponse
import kr.co.lokit.api.domain.user.dto.AdminCoupleMigrationResponse
import kr.co.lokit.api.domain.user.dto.AdminPartnerResponse
import kr.co.lokit.api.domain.user.dto.AdminUserSummaryResponse
import kr.co.lokit.api.domain.user.infrastructure.UserJpaRepository
import kr.co.lokit.api.domain.user.infrastructure.mapping.toDomain
import kr.co.lokit.api.domain.user.infrastructure.mapping.toEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("admin")
class AdminController(
    private val userJpaRepository: UserJpaRepository,
    private val createCoupleUseCase: CoupleCommandService,
    private val coupleInviteUseCase: CoupleInviteUseCase,
    private val refreshTokenRepository: RefreshTokenRepositoryPort,
    private val coupleJpaRepository: CoupleJpaRepository,
    private val albumJpaRepository: AlbumJpaRepository,
    private val albumBoundsJpaRepository: AlbumBoundsJpaRepository,
    private val coupleRepository: CoupleRepositoryPort,
    private val albumRepository: AlbumRepositoryPort,
    private val photoRepository: PhotoRepositoryPort,
    private val commentRepository: CommentRepositoryPort,
    private val emoticonRepository: EmoticonRepositoryPort,
    private val jwtTokenProvider: JwtTokenProvider,
    private val cacheManager: CacheManager,
    @Value("\${admin.key}") private val adminKey: String,
) : AdminApi {
    @GetMapping("users")
    override fun getUsers(
        @RequestHeader("X-Admin-Key") key: String,
    ): List<AdminUserSummaryResponse> {
        validateAdminKey(key)
        return userJpaRepository.findAll().map { user ->
            AdminUserSummaryResponse(
                id = user.nonNullId(),
                email = user.email,
            )
        }
    }

    @DeleteMapping("users/{email}")
    @Transactional
    override fun deleteAllByEmail(
        @PathVariable email: String,
        @RequestHeader("X-Admin-Key") key: String,
    ): AdminActionResponse {
        validateAdminKey(key)
        val user =
            userJpaRepository.findByEmail(email)
                ?: throw BusinessException.UserNotFoundException(message = "User not found for email: $email")
        val userId = user.nonNullId()

        refreshTokenRepository.deleteByUserId(userId)

        val couple = coupleJpaRepository.findByUserIdCandidates(userId).maxByOrNull { it.updatedAt }
        if (couple != null) {
            val albumIds = albumJpaRepository.findAlbumIdsByCoupleId(couple.nonNullId())
            if (albumIds.isNotEmpty()) {
                albumBoundsJpaRepository.deleteAllByStandardIdIn(albumIds)
            }
            coupleJpaRepository.delete(couple)
        }

        userJpaRepository.delete(user)
        cacheManager.clearAllCaches()

        return AdminActionResponse(message = "삭제 완료: $email")
    }

    @PostMapping("cache/clear")
    override fun clearAllCaches(
        @RequestHeader("X-Admin-Key") key: String,
    ): AdminActionResponse {
        validateAdminKey(key)
        cacheManager.clearAllCaches()
        return AdminActionResponse(message = "캐시 초기화 완료")
    }

    private fun validateAdminKey(key: String) {
        if (!adminKey.trim().equals(key.trim(), ignoreCase = true)) {
            throw BusinessException.ForbiddenException(message = "관리자 키가 유효하지 않습니다.")
        }
    }

    @SyncCoupleStatusCookie
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("couples/partner")
    @Transactional
    override fun createCouplePartner(
        @CurrentUserId userId: Long,
        httpRequest: HttpServletRequest,
    ): AdminPartnerResponse {
        val primaryUser =
            userJpaRepository.findById(userId).orElseThrow {
                BusinessException.UserNotFoundException(message = "Primary user not found for ID: $userId")
            }

        val email = "dev.partner.${primaryUser.nonNullId()}@example.com"

        val partnerUser =
            userJpaRepository
                .save(
                    userJpaRepository.findByEmail(email) ?: userJpaRepository.save(
                        userJpaRepository.save(
                            User(
                                email = email,
                                name = "test-${primaryUser.nonNullId()}",
                            ).toEntity(),
                        ),
                    ),
                ).toDomain()

        linkWithPartner(primaryUser.nonNullId(), partnerUser.id, httpRequest.remoteAddr)

        val accessToken = jwtTokenProvider.generateAccessToken(partnerUser)
        val refreshToken = jwtTokenProvider.generateRefreshToken()

        val expiresAt =
            LocalDateTime.now().plusSeconds(
                jwtTokenProvider.getRefreshTokenExpirationMillis() / 1000,
            )

        refreshTokenRepository.save(
            userId = partnerUser.id,
            token = refreshToken,
            expiresAt = expiresAt,
        )

        return AdminPartnerResponse(
            partnerEmail = email,
            tokens =
                AuthTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                ),
        )
    }

    /**
     * 초대 코드 입력 없이 실제 초대 흐름(파트너 발급 → 호출자 합류)을 그대로 태운다.
     * 프로필 이미지(LOCK/KEY)·캐시 무효화·호출자의 미완성 커플 정리를 joinByInviteCode 가 맡는다.
     * 이미 이 파트너와 연결돼 있으면 그대로 두고, 다른 사람과 연결돼 있으면 덮어쓰지 않고 거절한다.
     */
    private fun linkWithPartner(
        userId: Long,
        partnerId: Long,
        clientIp: String,
    ) {
        coupleRepository.findByUserId(userId)?.takeIf { it.isConnectedAndFull() }?.let { couple ->
            if (couple.isMember(partnerId)) return
            throw BusinessException.InviteAlreadyCoupledException()
        }
        createCoupleUseCase.createIfNone(Couple(name = Couple.DEFAULT_COUPLE_NAME), partnerId)
        val inviteCode = coupleInviteUseCase.generateInviteCode(partnerId).inviteCode
        coupleInviteUseCase.joinByInviteCode(userId, inviteCode, clientIp)
    }

    @PostMapping("couples/migrate")
    @Transactional
    override fun migratePreviousCoupleData(
        @CurrentUserId userId: Long,
        @RequestHeader("X-Admin-Key") key: String,
    ): AdminCoupleMigrationResponse {
        validateAdminKey(key)
        val user =
            userJpaRepository.findById(userId).orElseThrow {
                BusinessException.UserNotFoundException(message = "User not found for ID: $userId")
            }
        val currentCoupleId =
            coupleRepository.findByUserId(userId)?.id
                ?: throw BusinessException.CoupleNotFoundException(message = "Current couple not found for userId: $userId")
        val currentDefaultAlbum =
            albumRepository.findDefaultByCoupleId(currentCoupleId)
                ?: throw BusinessException.DefaultAlbumNotFoundForUserException(message = "Default album not found for userId: $userId")

        val sourceOwnedAlbums = albumRepository.findNonDefaultByCreatedByIdAndCoupleIdNot(userId, currentCoupleId)
        val ownedPhotosFromPreviousCouples =
            photoRepository
                .findAllByUserId(userId)
                .filter { photo -> photo.coupleId != null && photo.coupleId != currentCoupleId }

        val previousCoupleIds =
            buildSet {
                sourceOwnedAlbums.mapTo(this) { it.coupleId }
                ownedPhotosFromPreviousCouples.mapNotNullTo(this) { it.coupleId }
            }

        if (previousCoupleIds.isEmpty()) {
            return AdminCoupleMigrationResponse(
                previousCoupleCount = 0,
                createdAlbumCount = 0,
                movedPhotoCount = 0,
                movedCommentCount = 0,
                skippedCommentCount = 0,
                movedEmoticonCount = 0,
                skippedEmoticonCount = 0,
            )
        }

        val currentCoupleTitles = albumRepository.findAllByCoupleId(currentCoupleId).map { it.title }.toMutableSet()
        val targetAlbumBySourceAlbumId = mutableMapOf<Long, Long>()
        sourceOwnedAlbums
            .sortedBy { it.id }
            .forEach { sourceAlbum ->
                val resolvedTitle = resolveTargetAlbumTitle(sourceAlbum.title, user.name, currentCoupleTitles)
                val createdAlbum = albumRepository.save(Album(title = resolvedTitle), userId)
                targetAlbumBySourceAlbumId[sourceAlbum.id] = createdAlbum.id
                currentCoupleTitles.add(createdAlbum.title)
            }

        val sourceAlbumIds = ownedPhotosFromPreviousCouples.mapNotNull { it.albumId }.toSet()
        val sourceAlbumsById = albumRepository.findAllByIds(sourceAlbumIds.toList()).associateBy { it.id }
        val movedPhotoIds = mutableSetOf<Long>()
        ownedPhotosFromPreviousCouples.forEach { sourcePhoto ->
            val targetAlbumId =
                resolveTargetAlbumId(
                    sourceAlbumId = sourcePhoto.albumId,
                    sourceAlbumsById = sourceAlbumsById,
                    userId = userId,
                    targetAlbumBySourceAlbumId = targetAlbumBySourceAlbumId,
                    defaultAlbumId = currentDefaultAlbum.id,
                )
            val movedPhoto = photoRepository.update(sourcePhoto.copy(albumId = targetAlbumId))
            movedPhotoIds.add(movedPhoto.id)
        }

        val allCommentIds = commentRepository.findIdsByUserIdAndCoupleIds(userId, previousCoupleIds)
        val movedCommentIds = commentRepository.findIdsByUserIdAndPhotoIds(userId, movedPhotoIds)
        val allEmoticonIds = emoticonRepository.findIdsByUserIdAndCoupleIds(userId, previousCoupleIds)
        val movedEmoticonIds = emoticonRepository.findIdsByUserIdAndPhotoIds(userId, movedPhotoIds)

        cacheManager.clearAllCaches()

        return AdminCoupleMigrationResponse(
            previousCoupleCount = previousCoupleIds.size,
            createdAlbumCount = targetAlbumBySourceAlbumId.size,
            movedPhotoCount = movedPhotoIds.size,
            movedCommentCount = movedCommentIds.size,
            skippedCommentCount = (allCommentIds - movedCommentIds).size,
            movedEmoticonCount = movedEmoticonIds.size,
            skippedEmoticonCount = (allEmoticonIds - movedEmoticonIds).size,
        )
    }

    private fun resolveTargetAlbumId(
        sourceAlbumId: Long?,
        sourceAlbumsById: Map<Long, Album>,
        userId: Long,
        targetAlbumBySourceAlbumId: Map<Long, Long>,
        defaultAlbumId: Long,
    ): Long {
        val albumId = sourceAlbumId ?: return defaultAlbumId
        val sourceAlbum = sourceAlbumsById[albumId] ?: return defaultAlbumId
        if (sourceAlbum.isDefault) {
            return defaultAlbumId
        }
        if (sourceAlbum.createdById != userId) {
            return defaultAlbumId
        }
        return targetAlbumBySourceAlbumId[albumId] ?: defaultAlbumId
    }

    private fun resolveTargetAlbumTitle(
        sourceTitle: String,
        ownerName: String,
        existingTitles: Set<String>,
    ): String {
        if (!existingTitles.contains(sourceTitle)) {
            return sourceTitle
        }
        val suffixBase = "($ownerName)"
        var sequence = 1
        while (true) {
            val rawSuffix = if (sequence == 1) suffixBase else "$suffixBase$sequence"
            val suffix =
                if (rawSuffix.length > MAX_ALBUM_TITLE_LENGTH) rawSuffix.takeLast(MAX_ALBUM_TITLE_LENGTH) else rawSuffix
            val allowedSourceLength = (MAX_ALBUM_TITLE_LENGTH - suffix.length).coerceAtLeast(0)
            val candidate = (sourceTitle.take(allowedSourceLength) + suffix).take(MAX_ALBUM_TITLE_LENGTH)
            if (!existingTitles.contains(candidate)) {
                return candidate
            }
            sequence++
        }
    }

    companion object {
        private const val MAX_ALBUM_TITLE_LENGTH = 10
    }
}
