package kr.co.lokit.api.domain.user.application.port

interface UserHardDeletePort {
    /** 소프트 삭제된 행까지 포함해 이메일이 일치하는 사용자 id 를 조회한다. */
    fun findUserIdsByEmail(email: String): List<Long>

    /**
     * 사용자와 그 참조 데이터를 물리 삭제하고, 함께 삭제된 사진의 URL 을 반환한다.
     *
     * - 현재 커플, 그리고 다른 활성 멤버가 남아 있지 않은 과거 커플은 앨범·사진·댓글까지 통째로 삭제한다.
     * - 다른 활성 멤버가 남은 과거 커플은 유지하고 이 사용자의 멤버십만 지운다.
     *   그 커플에 남는 사진·댓글의 작성자 참조는 @NotFound(IGNORE) 로 '탈퇴한 사용자'처럼 처리된다.
     */
    fun hardDelete(userIds: List<Long>): List<String>
}
