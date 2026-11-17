package kr.co.lokit.api.domain.couple.application

import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.couple.application.port.`in`.DisconnectNoticeUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 상대가 연결을 끊으면 남은 사용자는 기존(DISCONNECTED/EXPIRED) 커플에 그대로 소속된다.
 * 확인 여부를 그 커플 행에 저장하므로 기기가 바뀌어도 한 번만 노출되고, 재연결·재해제 시 초기화된다.
 * 캐시된 findByUserId 는 끊긴 직후 오래된 상태를 줄 수 있어 findByUserIdFresh 를 쓴다.
 */
@Service
class DisconnectNoticeService(
    private val coupleRepository: CoupleRepositoryPort,
) : DisconnectNoticeUseCase {
    @Transactional(readOnly = true)
    override fun shouldShowDisconnectNotice(userId: Long): Boolean =
        coupleRepository.findByUserIdFresh(userId)?.shouldShowPartnerDisconnectNoticeTo(userId) == true

    @Transactional
    override fun confirmDisconnectNotice(userId: Long) {
        val couple = coupleRepository.findByUserIdFresh(userId) ?: return
        if (couple.shouldShowPartnerDisconnectNoticeTo(userId)) {
            coupleRepository.confirmDisconnectNotice(couple.id)
        }
    }
}
