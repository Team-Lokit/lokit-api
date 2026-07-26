package kr.co.lokit.api.domain.photo.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.co.lokit.api.common.entity.BaseEntity
import kr.co.lokit.api.domain.user.infrastructure.UserEntity
import org.hibernate.annotations.NotFound
import org.hibernate.annotations.NotFoundAction
import java.time.LocalDate

@Entity(name = "Comment")
@Table(
    indexes = [
        Index(columnList = "photo_id"),
        Index(columnList = "user_id"),
        Index(columnList = "parent_id"),
    ],
)
class CommentEntity(
    @ManyToOne
    @JoinColumn(name = "photo_id", nullable = false)
    val photo: PhotoEntity,
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    val user: UserEntity?,
    @ManyToOne
    @JoinColumn(name = "parent_id")
    @NotFound(action = NotFoundAction.IGNORE)
    val parent: CommentEntity? = null,
    @Column(nullable = false, length = 500)
    var content: String,
    @Column(nullable = false)
    var commentedAt: LocalDate,
    @Column(nullable = false)
    var removed: Boolean = false,
) : BaseEntity()
