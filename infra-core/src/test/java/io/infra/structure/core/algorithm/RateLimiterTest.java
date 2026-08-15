package io.infra.structure.core.algorithm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    @Test
    void tryAcquire_shouldReturnTrueWhenUnderLimit() {
        RateLimiter limiter = RateLimiter.createPerSecond(5);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
    }

    @Test
    void tryAcquire_shouldReturnFalseWhenOverLimit() {
        RateLimiter limiter = RateLimiter.createPerSecond(3);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void tryAcquire_withPermits_shouldConsumeCorrectCount() {
        RateLimiter limiter = RateLimiter.createPerSecond(10);
        assertThat(limiter.tryAcquire(7)).isTrue();
        assertThat(limiter.tryAcquire(3)).isTrue();
        assertThat(limiter.tryAcquire(1)).isFalse();
    }

    @Test
    void tryAcquire_shouldRecoverAfterWindowElapses() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMillis(100));
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        Thread.sleep(120);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void tryAcquire_shouldHandleConcurrentAccessSafely() throws InterruptedException {
        int maxPermits = 500;
        RateLimiter limiter = new RateLimiter(maxPermits, Duration.ofMinutes(1));
        int threadCount = 10;
        int triesPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger acquired = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                for (int i = 0; i < triesPerThread; i++) {
                    if (limiter.tryAcquire()) {
                        acquired.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        assertThat(acquired.get() + rejected.get()).isEqualTo(threadCount * triesPerThread);
        assertThat(acquired.get()).isLessThanOrEqualTo(maxPermits);
        assertThat(rejected.get()).isGreaterThan(0);
    }

    @Test
    void constructor_shouldRejectInvalidParams() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, Duration.ofSeconds(0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPerMinute_shouldLimitCorrectly() {
        RateLimiter limiter = RateLimiter.createPerMinute(2);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void getEffectiveCount_shouldReflectCurrentUsage() {
        RateLimiter limiter = RateLimiter.createPerSecond(10);
        assertThat(limiter.getEffectiveCount()).isZero();
        limiter.tryAcquire(4);
        assertThat(limiter.getEffectiveCount()).isEqualTo(4);
        limiter.tryAcquire(2);
        assertThat(limiter.getEffectiveCount()).isEqualTo(6);
    }

    @Test
    void tryAcquire_shouldRejectInvalidPermits() {
        RateLimiter limiter = RateLimiter.createPerSecond(5);
        assertThatThrownBy(() -> limiter.tryAcquire(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}