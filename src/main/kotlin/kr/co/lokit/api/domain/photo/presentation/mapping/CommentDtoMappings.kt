package kr.co.lokit.api.domain.photo.presentation.mapping

import kr.co.lokit.api.common.permission.EditabilityPolicy
import kr.co.lokit.api.domain.photo.domain.Comment
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons
import kr.co.lokit.api.domain.photo.dto.CommentResponse
import kr.co.lokit.api.domain.photo.dto.EmoticonSummaryResponse

fun CommentWithEmoticons.toResponse(viewerUserId: Long): CommentResponse =
    CommentResponse(
        id = comment.id,
        userId = comment.userId,
        userName = userName,
        userProfileImageUrl = userProfileImageUrl,
        content = if (comment.removed) Comment.REMOVED_PLACEHOLDER_TEXT else comment.content,
        commentedAt = comment.commentedAt,
        emoticons =
            emoticons.map {
                EmoticonSummaryResponse(
                    emoji = it.emoji,
                    count = it.count,
                    isEditable = EditabilityPolicy.canEditEmoticon(it.reacted),
                )
            },
        isEditable =
            !comment.removed &&
                EditabilityPolicy.canEditOwnedResource(
                    viewerUserId = viewerUserId,
                    createdByUserId = comment.userId,
                ),
        isEdited = !comment.removed && comment.createdAt != comment.updatedAt,
        replies = replies.map { it.toResponse(viewerUserId) },
    )
