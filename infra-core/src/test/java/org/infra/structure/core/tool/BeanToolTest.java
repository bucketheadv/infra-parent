package org.infra.structure.core.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class BeanToolTest {

    @Test
    void copyAs_shouldReturnNull_whenSourceIsNull() {
        TestSource source = null;
        TestTarget result = BeanTool.copyAs(source, TestTarget.class);
        assertNull(result);
    }

    @Test
    void copyAs_shouldCopyProperties_whenSourceIsValid() {
        TestSource source = new TestSource("test", 123);
        TestTarget result = BeanTool.copyAs(source, TestTarget.class);
        
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("test");
        assertThat(result.getValue()).isEqualTo(123);
    }

    @Test
    void copyList_shouldReturnEmptyList_whenSourceIsEmpty() {
        List<TestTarget> result = BeanTool.copyList(null, TestTarget.class);
        assertThat(result).isEmpty();
    }

    @Test
    void copyList_shouldCopyAllElements_whenSourceIsValid() {
        List<TestSource> sources = Arrays.asList(
            new TestSource("test1", 1),
            new TestSource("test2", 2)
        );

        List<TestTarget> results = BeanTool.copyList(sources, TestTarget.class);
        
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("test1");
        assertThat(results.get(0).getValue()).isEqualTo(1);
        assertThat(results.get(1).getName()).isEqualTo("test2");
        assertThat(results.get(1).getValue()).isEqualTo(2);
    }

    // Test data classes
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class TestSource {
        private String name;
        private int value;
    }

    @Data
    private static class TestTarget {
        private String name;
        private int value;
    }
}
