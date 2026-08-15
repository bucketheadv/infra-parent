package io.infra.structure.core.algorithm;

import lombok.Getter;

import java.time.Duration;

/**
 * 滑动窗口计数器限流器（Nginx / Redis 风格）。
 *
 * <p>基于当前窗口与上一窗口的加权计数实现，无固定窗口边界突刺问题。
 * 线程安全，所有公有方法均为原子操作。
 *
 * <p>用法示例：
 * <pre>{@code
 * // 限流：每秒最多 10 次
 * RateLimiter limiter = RateLimiter.createPerSecond(10);
 * if (limiter.tryAcquire()) {
 *     // 放行
 * } else {
 *     // 限流，返回 429
 * }
 * }</pre>
 *
 * @author sven
 * Created on 2026/8/15
 */
public class RateLimiter {

    private final long windowMillis;
    /** 窗口内最大许可数。 */
    @Getter
    private final long maxPermits;
    private final Object lock = new Object();

    /** 上一窗口的请求数。 */
    private long prevCount = 0L;

    /** 当前窗口的请求数。 */
    private long currCount = 0L;

    /** 当前窗口的起始时间戳（毫秒）。 */
    private long windowStartMillis;

    /**
     * 构建限流器。
     * @param maxPermits 窗口内最大允许请求数
     * @param window     窗口时长
     */
    public RateLimiter(int maxPermits, Duration window) {
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("maxPermits 必须为正数，当前：" + maxPermits);
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("窗口时长必须为正数");
        }
        this.maxPermits = maxPermits;
        this.windowMillis = window.toMillis();
        this.windowStartMillis = System.currentTimeMillis();
    }

    /**
     * 创建每秒限流器。
     * @param maxPerSecond 每秒最大请求数
     * @return 限流器实例
     */
    public static RateLimiter createPerSecond(int maxPerSecond) {
        return new RateLimiter(maxPerSecond, Duration.ofSeconds(1));
    }

    /**
     * 创建每分钟限流器。
     * @param maxPerMinute 每分钟最大请求数
     * @return 限流器实例
     */
    public static RateLimiter createPerMinute(int maxPerMinute) {
        return new RateLimiter(maxPerMinute, Duration.ofMinutes(1));
    }

    /**
     * 尝试获取一个许可，非阻塞。
     * @return {@code true} 表示获取成功（放行），{@code false} 表示被限流
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试获取指定数量的许可，非阻塞。
     * @param permits 请求数
     * @return {@code true} 表示获取成功，{@code false} 表示被限流
     */
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits 必须为正数，当前：" + permits);
        }
        synchronized (lock) {
            long now = System.currentTimeMillis();
            rotateIfNeeded(now);
            long effective = effectiveCount(now);
            if (effective + permits <= maxPermits) {
                currCount += permits;
                return true;
            }
            return false;
        }
    }

    /**
     * 获取当前窗口的加权计数。
     * @return 当前窗口的有效计数（含上一窗口的加权）
     */
    public long getEffectiveCount() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            rotateIfNeeded(now);
            return effectiveCount(now);
        }
    }

    /**
     * 获取窗口时长。
     * @return 窗口时长
     */
    public Duration getWindow() {
        return Duration.ofMillis(windowMillis);
    }

    /** 当前时间已跨入新窗口时，滚动窗口。 */
    private void rotateIfNeeded(long now) {
        long elapsed = now - windowStartMillis;
        if (elapsed >= windowMillis) {
            long windowsPassed = elapsed / windowMillis;
            if (windowsPassed == 1) {
                // 正好跨过一个窗口：当前变上一
                prevCount = currCount;
            } else if (windowsPassed >= 2) {
                // 跨过多个窗口（长期空闲）：上一窗口清零
                prevCount = 0L;
            }
            currCount = 0L;
            windowStartMillis += windowsPassed * windowMillis;
        }
    }

    /** 计算加权计数。 */
    private long effectiveCount(long now) {
        long elapsed = now - windowStartMillis;
        // 上一窗口在当前窗口内的加权（截断，与 Nginx/Redis 算法一致）
        double weight = Math.max(1.0 - (double) elapsed / windowMillis, 0.0);
        long weightedPrev = (long) (prevCount * weight);
        return weightedPrev + currCount;
    }
}