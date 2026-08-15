package io.infra.structure.core.algorithm;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void nextId_shouldBePositiveAndTrendIncreasing() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3L, 7L);
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < 10000; i++) {
            long id = generator.nextId();
            assertThat(id).isPositive();
            assertThat(id).isGreaterThan(prev);
            prev = id;
        }
    }

    @Test
    void nextId_shouldGenerateUniqueIdsAcrossMultipleWorkers() {
        SnowflakeIdGenerator a = new SnowflakeIdGenerator(0L, 0L);
        SnowflakeIdGenerator b = new SnowflakeIdGenerator(1L, 1L);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            assertThat(ids.add(a.nextId())).isTrue();
            assertThat(ids.add(b.nextId())).isTrue();
        }
    }

    @Test
    void nextId_shouldBeUniqueWithinSameMillisecond() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        Set<Long> ids = new HashSet<>();
        long start = System.currentTimeMillis();
        long deadline = start + 50;
        while (System.currentTimeMillis() <= deadline) {
            assertThat(ids.add(generator.nextId())).isTrue();
        }
        assertThat(ids).hasSizeGreaterThan(1);
    }

    @Test
    void defaultConstructor_shouldWork() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        assertThat(generator.nextId()).isPositive();
    }

    @Test
    void constructor_shouldRejectOutOfRangeIds() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32L, 0L))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0L, 32L))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_shouldRejectInvalidEpoch() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(System.currentTimeMillis() + 1000, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomWorkerId_shouldStayInRange() {
        for (int i = 0; i < 100; i++) {
            long workerId = SnowflakeIdGenerator.randomWorkerId();
            assertThat(workerId).isBetween(0L, 31L);
        }
    }

    @Test
    void randomDataCenterId_shouldStayInRange() {
        for (int i = 0; i < 100; i++) {
            long dataCenterId = SnowflakeIdGenerator.randomDataCenterId();
            assertThat(dataCenterId).isBetween(0L, 31L);
        }
    }

    @Test
    void ids_shouldBeDistinctForSameEpochWithDifferentDataCenter() {
        SnowflakeIdGenerator a = new SnowflakeIdGenerator(0L, 0L);
        SnowflakeIdGenerator b = new SnowflakeIdGenerator(1L, 0L);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(ids.add(a.nextId())).isTrue();
            assertThat(ids.add(b.nextId())).isTrue();
        }
        assertThat(ids).hasSize(2000);
    }

    @Test
    void ids_shouldBeDistinctForSameWorkerWithDifferentDataCenter() {
        long now = System.currentTimeMillis() - 1000;
        SnowflakeIdGenerator a = new SnowflakeIdGenerator(now, 0L, 5L);
        SnowflakeIdGenerator b = new SnowflakeIdGenerator(now, 1L, 5L);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(ids.add(a.nextId())).isTrue();
            assertThat(ids.add(b.nextId())).isTrue();
        }
        assertThat(ids).hasSize(2000);
    }
}