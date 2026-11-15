package kr.co.lokit.api.common.concurrency

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * JVM 락 + REQUIRES_NEW 트랜잭션.
 *
 * 🔴 순서가 핵심이다: 락 획득 → 트랜잭션 시작 → operation → **커밋** → 락 해제.
 * 예전처럼 메서드에 @Transactional 을 붙이면 프록시가 메서드 반환(=락 해제) 뒤에 커밋하므로,
 * 다음 대기자가 커밋 전 상태를 읽어 @Version 충돌(낙관적 락 예외)로 실패했다.
 * 그래서 트랜잭션을 TransactionTemplate 으로 락 안쪽에서 직접 연다.
 *
 * transactionManager 가 null 이면(순수 단위 테스트) 트랜잭션 없이 operation 만 실행한다.
 */
@Component
class LockManager(
    transactionManager: PlatformTransactionManager? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val transactionTemplate: TransactionTemplate? =
        transactionManager?.let {
            TransactionTemplate(it).apply {
                propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            }
        }

    companion object {
        private val locks = ConcurrentHashMap<String, ReentrantLock>()
    }

    fun <T> withLock(
        key: String,
        timeoutSeconds: Long = LockPolicy.JVM_LOCK_TIMEOUT_SECONDS,
        operation: () -> T,
    ): T {
        val lock = getOrCreateLock(key)
        val acquired =
            runCatching { lock.tryLock(timeoutSeconds, TimeUnit.SECONDS) }
                .getOrElse { throwable ->
                    if (throwable is InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    log.warn("락 획득 중단: key={}", key, throwable)
                    throw BusinessException.LockTimeoutException(
                        errors = errorDetailsOf(ErrorField.LOCK_KEY to key),
                        cause = throwable,
                    )
                }

        if (!acquired) {
            log.warn("락 획득 타임아웃: key={}, timeoutSeconds={}", key, timeoutSeconds)
            throw BusinessException.LockTimeoutException(
                errors = errorDetailsOf(ErrorField.LOCK_KEY to key),
            )
        }

        return try {
            executeInNewTransaction(operation)
        } finally {
            unlockSafely(key, lock)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> executeInNewTransaction(operation: () -> T): T {
        val template = transactionTemplate ?: return operation()
        // execute 는 커밋까지 끝낸 뒤 반환한다. T 가 nullable 일 수 있어 엘비스 대신 캐스트한다.
        return template.execute { operation() } as T
    }

    private fun getOrCreateLock(key: String): ReentrantLock = locks.computeIfAbsent(key) { ReentrantLock(true) }

    private fun unlockSafely(
        key: String,
        lock: ReentrantLock,
    ) {
        if (lock.isHeldByCurrentThread) {
            lock.unlock()
        }
        if (!lock.hasQueuedThreads()) {
            locks.remove(key, lock)
        }
    }
}
