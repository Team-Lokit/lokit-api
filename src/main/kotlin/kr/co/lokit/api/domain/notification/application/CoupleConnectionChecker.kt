package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import org.springframework.stereotype.Component

/**
 * 지연 발송(그룹 요약 푸시·업로드 알림) 직전에 두 사람이 아직 연결된 커플인지 다시 확인한다.
 * 이벤트 시점엔 연결돼 있었어도 5~10분 사이에 연결이 끊길 수 있다.
 *
 * findByUserIdFresh 를 쓴다 — findByUserId 는 캐시(USER_COUPLE)를 타서 끊긴 직후 오래된 상태를 볼 수 있다.
 * arePartners 로 멤버십을 본다(partnerIdFor 는 비멤버에게 userIds[0] 을 돌려주는 함정이 있다).
 */
@Component
class CoupleConnectionChecker(
    private val coupleRepository: CoupleRepositoryPort,
) {
    fun areConnectedPartners(
        userIdA: Long,
        userIdB: Long,
    ): Boolean {
        val couple = coupleRepository.findByUserIdFresh(userIdA) ?: return false
        return couple.isConnected() && couple.arePartners(userIdA, userIdB)
    }
}
