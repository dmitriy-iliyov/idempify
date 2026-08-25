package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DefaultOperationMetadataCacheUnitTest {

    DefaultOperationMetadataCache tested = new DefaultOperationMetadataCache();

    @Test
    @DisplayName("UT get() when method was never put should return null")
    void get_whenMethodWasNeverPut_shouldReturnNull() throws NoSuchMethodException {
        // when
        OperationMetadata result = tested.get(method("action"));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT get() when method was put should return the stored metadata")
    void get_whenMethodWasPut_shouldReturnStoredMetadata() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata metadata = mock(OperationMetadata.class);

        // when
        tested.put(method, metadata);

        // then
        assertThat(tested.get(method)).isSameAs(metadata);
    }

    @Test
    @DisplayName("UT put() when the same method is put twice should keep the last metadata")
    void put_whenSameMethodIsPutTwice_shouldKeepLastMetadata() throws NoSuchMethodException {
        // given
        Method method = method("action");
        OperationMetadata first = mock(OperationMetadata.class);
        OperationMetadata second = mock(OperationMetadata.class);

        // when
        tested.put(method, first);
        tested.put(method, second);

        // then
        assertThat(tested.get(method)).isSameAs(second);
    }

    @Test
    @DisplayName("UT put() when different methods are put should keep their metadata apart")
    void put_whenDifferentMethodsArePut_shouldKeepTheirMetadataApart() throws NoSuchMethodException {
        // given
        Method action = method("action");
        Method otherAction = method("otherAction");
        OperationMetadata actionMetadata = mock(OperationMetadata.class);
        OperationMetadata otherActionMetadata = mock(OperationMetadata.class);

        // when
        tested.put(action, actionMetadata);
        tested.put(otherAction, otherActionMetadata);

        // then
        assertThat(tested.get(action)).isSameAs(actionMetadata);
        assertThat(tested.get(otherAction)).isSameAs(otherActionMetadata);
    }

    @Test
    @DisplayName("UT put() when called concurrently for the same method should stay readable by every thread")
    void put_whenCalledConcurrentlyForSameMethod_shouldStayReadableByEveryThread() throws Exception {
        // given
        int threads = 16;
        Method method = method("action");
        OperationMetadata metadata = mock(OperationMetadata.class);
        List<Callable<OperationMetadata>> tasks = IntStream.range(0, threads)
                .<Callable<OperationMetadata>>mapToObj(i -> () -> {
                    tested.put(method, metadata);
                    return tested.get(method);
                })
                .toList();

        // when
        List<Future<OperationMetadata>> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            results = executor.invokeAll(tasks);
        }

        // then
        for (Future<OperationMetadata> result : results) {
            assertThat(result.get()).isSameAs(metadata);
        }
    }

    @Test
    @DisplayName("UT get() when the method is null should refuse rather than answer for it")
    void get_whenMethodIsNull_shouldRefuseRatherThanAnswerForIt() {
        assertThatThrownBy(() -> tested.get(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UT put() when the method is null should refuse rather than store the metadata under it")
    void put_whenMethodIsNull_shouldRefuseRatherThanStoreMetadataUnderIt() {
        assertThatThrownBy(() -> tested.put(null, mock(OperationMetadata.class)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UT put() when the metadata is null should refuse rather than cache an absent answer")
    void put_whenMetadataIsNull_shouldRefuseRatherThanCacheAbsentAnswer() throws NoSuchMethodException {
        // given
        Method method = method("action");

        // when / then
        assertThatThrownBy(() -> tested.put(method, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(tested.get(method)).isNull();
    }

    private Method method(String name) throws NoSuchMethodException {
        return TestTarget.class.getMethod(name);
    }

    private static class TestTarget {

        public String action() {
            return "action";
        }

        public String otherAction() {
            return "otherAction";
        }
    }
}
