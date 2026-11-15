package kr.co.lokit.api.common.concurrency

import kr.co.lokit.api.common.exception.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LockManagerTest {
    private val lockManager = LockManager()

    @Test
    fun `withLock executes operation when lock can be acquired`() {
        val result =
            lockManager.withLock(
                key = "lock:happy-path",
                timeoutSeconds = 1,
            ) { "ok" }

        assertEquals("ok", result)
    }

    @Test
    fun `withLock throws lock timeout when lock is already held`() {
        val lockAcquired = CountDownLatch(1)
        val releaseLock = CountDownLatch(1)

        val holder =
            thread(start = true) {
                lockManager.withLock(
                    key = "lock:timeout",
                    timeoutSeconds = 3,
                ) {
                    lockAcquired.countDown()
                    releaseLock.await(3, TimeUnit.SECONDS)
                }
            }

        lockAcquired.await(1, TimeUnit.SECONDS)
        assertThrows(BusinessException.LockTimeoutException::class.java) {
            lockManager.withLock(
                key = "lock:timeout",
                timeoutSeconds = 1,
            ) { "never" }
        }

        releaseLock.countDown()
        holder.join(4000)
    }

    /**
     * 회귀 방지: 예전엔 @Transactional 프록시가 락 해제 뒤에 커밋해서, 다음 대기자가 커밋 전 상태를 읽고
     * @Version 충돌로 실패했다(알림 그룹 카운트·업로드 배치 갱신 유실의 원인).
     */
    @Test
    fun `트랜잭션을 커밋한 뒤에 락을 풀어 다음 대기자는 커밋 이후에 실행된다`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val transactionManager =
            object : AbstractPlatformTransactionManager() {
                override fun doGetTransaction(): Any = Any()

                override fun doBegin(transaction: Any, definition: TransactionDefinition) {}

                override fun doCommit(status: DefaultTransactionStatus) {
                    Thread.sleep(100) // 커밋이 느려도 순서가 지켜지는지 본다
                    events.add("commit:${Thread.currentThread().name}")
                }

                override fun doRollback(status: DefaultTransactionStatus) {}
            }
        val manager = LockManager(transactionManager)
        val firstEntered = CountDownLatch(1)

        val first =
            thread(name = "A") {
                manager.withLock(key = "lock:commit-order", timeoutSeconds = 3) {
                    events.add("op:A")
                    firstEntered.countDown()
                }
            }
        firstEntered.await(1, TimeUnit.SECONDS)
        val second =
            thread(name = "B") {
                manager.withLock(key = "lock:commit-order", timeoutSeconds = 3) { events.add("op:B") }
            }
        first.join(3000)
        second.join(3000)

        assertEquals(listOf("op:A", "commit:A", "op:B", "commit:B"), events.toList())
    }

    @Test
    fun `operation 이 null 을 반환해도 다시 실행하지 않고 null 을 돌려준다`() {
        var calls = 0
        val transactionManager =
            object : AbstractPlatformTransactionManager() {
                override fun doGetTransaction(): Any = Any()

                override fun doBegin(transaction: Any, definition: TransactionDefinition) {}

                override fun doCommit(status: DefaultTransactionStatus) {}

                override fun doRollback(status: DefaultTransactionStatus) {}
            }

        val result: String? =
            LockManager(transactionManager).withLock(key = "lock:null", timeoutSeconds = 1) {
                calls++
                null
            }

        assertEquals(null, result)
        assertEquals(1, calls)
    }
}
