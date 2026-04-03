package io.infra.structure.core.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionToolTest {

    @Test
    void compare_shouldSupportDifferentSegmentLengths() {
        assertThat(VersionTool.compare("2.0", "2.0.0")).isZero();
        assertThat(VersionTool.compare("2.0", "2.0.0.0")).isZero();
    }

    @Test
    void compare_shouldCompareNumericSegmentsFirst() {
        assertThat(VersionTool.compare("2.1", "2.0.10")).isPositive();
        assertThat(VersionTool.compare("2.0.9", "2.0.10")).isNegative();
    }

    @Test
    void compare_shouldCompareSuffixWhenNumericEqual() {
        assertThat(VersionTool.compare("2.1.1b", "2.1.1a")).isPositive();
        assertThat(VersionTool.compare("2.0.0", "2.0.0-a")).isPositive();
        assertThat(VersionTool.compare("2.0.0-a", "2.0.0-b")).isNegative();
    }

    @Test
    void helperMethods_shouldReturnExpectedValues() {
        assertThat(VersionTool.isGreaterThan("2.1", "2.0.10")).isTrue();
        assertThat(VersionTool.isLessThan("2.0.0-a", "2.0.0")).isTrue();
        assertThat(VersionTool.isEquals("2.0.0", "2.0")).isTrue();
    }

    @Test
    void compare_shouldThrowException_whenVersionInvalid() {
        assertThrows(IllegalArgumentException.class, () -> VersionTool.compare("", "2.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionTool.compare("2.a.0", "2.0"));
    }
}
