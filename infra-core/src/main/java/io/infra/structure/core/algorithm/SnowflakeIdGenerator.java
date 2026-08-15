package io.infra.structure.core.algorithm;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 雪花算法 ID 生成器。
 *
 * <p>ID 结构：1 位符号位 + 41 位毫秒时间戳 + 5 位数据中心 + 5 位工作节点 + 12 位序列号，
 * 单节点单毫秒可生成 4096 个不重复 ID，整体趋势递增，适合作为分布式主键。
 *
 * @author sven
 * Created on 2026/8/15
 */
public class SnowflakeIdGenerator {

    /** 默认起始时间戳（2024-01-01 00:00:00）。 */
    private static final long DEFAULT_EPOCH = 1704067200000L;

    /** 数据中心 ID 位数。 */
    private static final long DATA_CENTER_ID_BITS = 5L;

    /** 工作节点 ID 位数。 */
    private static final long WORKER_ID_BITS = 5L;

    /** 序列号位数。 */
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    private final long epoch;
    private final long dataCenterId;
    private final long workerId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 使用默认 epoch 与 0 数据中心、0 工作节点构建生成器。
     */
    public SnowflakeIdGenerator() {
        this(DEFAULT_EPOCH, 0L, 0L);
    }

    /**
     * 使用默认 epoch 构建生成器。
     * @param dataCenterId 数据中心 ID，范围 [0, 31]
     * @param workerId 工作节点 ID，范围 [0, 31]
     */
    public SnowflakeIdGenerator(long dataCenterId, long workerId) {
        this(DEFAULT_EPOCH, dataCenterId, workerId);
    }

    /**
     * 构建生成器。
     * @param epoch 自定义起始时间戳（毫秒），需小于当前时间
     * @param dataCenterId 数据中心 ID，范围 [0, 31]
     * @param workerId 工作节点 ID，范围 [0, 31]
     */
    public SnowflakeIdGenerator(long epoch, long dataCenterId, long workerId) {
        if (dataCenterId < 0 || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("dataCenterId 必须在 [0, 31] 范围内，当前：" + dataCenterId);
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 [0, 31] 范围内，当前：" + workerId);
        }
        if (epoch < 0 || epoch > System.currentTimeMillis()) {
            throw new IllegalArgumentException("epoch 必须为非负且小于当前时间，当前：" + epoch);
        }
        this.epoch = epoch;
        this.dataCenterId = dataCenterId;
        this.workerId = workerId;
    }

    /**
     * 生成下一个分布式 ID。
     * @return 趋势递增的唯一长整型 ID
     */
    public synchronized long nextId() {
        long timestamp = currentTimestamp();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > 5) {
                throw new IllegalStateException("时钟回拨超过 5ms，拒绝生成 ID，回拨量：" + offset);
            }
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - epoch) << TIMESTAMP_LEFT_SHIFT)
            | (dataCenterId << DATA_CENTER_ID_SHIFT)
            | (workerId << WORKER_ID_SHIFT)
            | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private long currentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 随机生成合法的数据中心 ID。
     * @return [0, 31] 范围内的随机数据中心 ID
     */
    public static long randomDataCenterId() {
        return ThreadLocalRandom.current().nextLong(MAX_DATA_CENTER_ID + 1);
    }

    /**
     * 随机生成合法的工作节点 ID。
     * @return [0, 31] 范围内的随机工作节点 ID
     */
    public static long randomWorkerId() {
        return ThreadLocalRandom.current().nextLong(MAX_WORKER_ID + 1);
    }
}