package kr.co.lokit.api.support

import kr.co.lokit.api.domain.album.infrastructure.JpaAlbumRepository
import kr.co.lokit.api.domain.calendar.infrastructure.JpaCalendarPhotoQueryAdapter
import kr.co.lokit.api.domain.couple.infrastructure.JpaCoupleRepository
import kr.co.lokit.api.domain.notification.infrastructure.JpaDeviceTokenRepository
import kr.co.lokit.api.domain.notification.infrastructure.JpaNotificationRepository
import kr.co.lokit.api.domain.notification.infrastructure.JpaNotificationSettingsRepository
import kr.co.lokit.api.domain.notification.infrastructure.JpaPendingUploadNotificationRepository
import kr.co.lokit.api.domain.photo.infrastructure.JpaCommentRepository
import kr.co.lokit.api.domain.photo.infrastructure.JpaEmoticonRepository
import kr.co.lokit.api.domain.photo.infrastructure.JpaPhotoRepository
import kr.co.lokit.api.domain.user.infrastructure.JpaUserHardDeleteAdapter
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

/**
 * JPA 슬라이스 테스트 공통 베이스. 어댑터를 전부 한 번에 올려 **컨텍스트 하나**를 공유한다.
 *
 * 격리는 @DataJpaTest 의 테스트별 트랜잭션 롤백에 의존한다. 커밋이 필요한 테스트
 * (`@Commit`, `REQUIRES_NEW`, `TestTransaction.end()` 등)를 추가한다면 직접 정리(cleanup)해야
 * 다른 테스트 클래스로 데이터가 새지 않는다. 같은 이유로 PK 값을 하드코딩해 단언하지 말 것
 * (시퀀스는 컨텍스트 수명 동안 이어진다).
 *
 * 서브클래스에 `@Import` / `@MockitoBean` 을 붙이면 컨텍스트가 다시 갈라지므로, 새 어댑터는 여기에 추가한다.
 */
@DataJpaTest
@Import(
    JpaAlbumRepository::class,
    JpaCalendarPhotoQueryAdapter::class,
    JpaCommentRepository::class,
    JpaCoupleRepository::class,
    JpaDeviceTokenRepository::class,
    JpaEmoticonRepository::class,
    JpaNotificationRepository::class,
    JpaNotificationSettingsRepository::class,
    JpaPendingUploadNotificationRepository::class,
    JpaPhotoRepository::class,
    JpaUserHardDeleteAdapter::class,
)
abstract class JpaSliceTest
