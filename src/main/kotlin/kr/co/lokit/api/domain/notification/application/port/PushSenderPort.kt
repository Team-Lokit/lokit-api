package kr.co.lokit.api.domain.notification.application.port

import kr.co.lokit.api.domain.notification.domain.PushMessage
import kr.co.lokit.api.domain.notification.domain.PushSendResult

/**
 * 구현체는 notification.push.enabled=true일 때만 빈 등록. 소비자는 nullable로 주입받아
 * null이면 발송을 건너뛴다(선례: PhotoStoragePort?).
 * 구현 규약(D3): 기기별 독립 발송, 어떤 경우에도 예외를 던지지 않음.
 */
interface PushSenderPort {
    fun send(message: PushMessage): PushSendResult
}
