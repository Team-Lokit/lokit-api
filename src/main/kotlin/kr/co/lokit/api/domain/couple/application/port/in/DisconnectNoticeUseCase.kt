package kr.co.lokit.api.domain.couple.application.port.`in`

interface DisconnectNoticeUseCase {
    /** 상대가 연결을 끊은 뒤 "연결이 끊어졌어요" 알림창을 아직 보여줘야 하는가. */
    fun shouldShowDisconnectNotice(userId: Long): Boolean

    /** 알림창 확인 처리. 보여줄 알림창이 없으면 아무 것도 하지 않는다(멱등). */
    fun confirmDisconnectNotice(userId: Long)
}
