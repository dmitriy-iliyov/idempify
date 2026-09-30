package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.OperationContext;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.TypeAwareIdempotentProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The condition decides whether the built-in processor of a type reaches the context, and it decides that
 * while bean definitions are still being registered - so every case here is about what it can learn from a
 * definition alone, without a processor ever being created.
 */
class OnMissingProcessorTypeConditionUnitTest {

    private final OnMissingProcessorTypeCondition tested = new OnMissingProcessorTypeCondition();

    @Test
    @DisplayName("UT getMatchOutcome() when no bean declares that processor type should match")
    void getMatchOutcome_whenNoBeanDeclaresThatProcessorType_shouldMatch() {
        // when
        ConditionOutcome outcome = tested.getMatchOutcome(
                context(beanFactory()), metadata(ProcessorType.BLOCKING));

        // then
        assertThat(outcome.isMatch()).isTrue();
        assertThat(outcome.getMessage()).contains("BLOCKING");
    }

    @Test
    @DisplayName("UT getMatchOutcome() when a bean declares that processor type should not match and name it")
    void getMatchOutcome_whenBeanDeclaresThatProcessorType_shouldNotMatchAndNameIt() {
        // when
        ConditionOutcome outcome = tested.getMatchOutcome(
                context(beanFactory(NonBlockingTestProcessor.class)), metadata(ProcessorType.NON_BLOCKING));

        // then
        assertThat(outcome.isMatch()).isFalse();
        assertThat(outcome.getMessage()).contains("NON_BLOCKING", NonBlockingTestProcessor.class.getSimpleName());
    }

    @Test
    @DisplayName("UT getMatchOutcome() when a bean declares another processor type should match")
    void getMatchOutcome_whenBeanDeclaresAnotherProcessorType_shouldMatch() {
        // when
        ConditionOutcome outcome = tested.getMatchOutcome(
                context(beanFactory(NonBlockingTestProcessor.class)), metadata(ProcessorType.BLOCKING));

        // then
        assertThat(outcome.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when a processor bean declares no type should match, leaving the pair to the delegator")
    void getMatchOutcome_whenProcessorBeanDeclaresNoType_shouldMatch() {
        // when
        ConditionOutcome outcome = tested.getMatchOutcome(
                context(beanFactory(UndeclaredTestProcessor.class)), metadata(ProcessorType.NON_BLOCKING));

        // then
        assertThat(outcome.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when a declaring bean cannot be created should answer without creating it")
    void getMatchOutcome_whenDeclaringBeanCannotBeCreated_shouldAnswerWithoutCreatingIt() {
        // when
        ConditionOutcome outcome = tested.getMatchOutcome(
                context(beanFactory(ExplodingTestProcessor.class)), metadata(ProcessorType.BLOCKING));

        // then
        assertThat(outcome.isMatch()).isFalse();
        assertThat(outcome.getMessage()).contains(ExplodingTestProcessor.class.getSimpleName());
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the context has no bean factory should throw IllegalStateException")
    void getMatchOutcome_whenContextHasNoBeanFactory_shouldThrowIllegalStateException() {
        // given
        ConditionContext context = context(null);

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata(ProcessorType.BLOCKING)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bean factory");
    }

    private ConditionContext context(ConfigurableListableBeanFactory beanFactory) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        return context;
    }

    private ConfigurableListableBeanFactory beanFactory(Class<?>... processorClasses) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        for (Class<?> processorClass : processorClasses) {
            beanFactory.registerBeanDefinition(processorClass.getSimpleName(), new RootBeanDefinition(processorClass));
        }
        return beanFactory;
    }

    private AnnotatedTypeMetadata metadata(ProcessorType processorType) {
        AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
        when(metadata.getAnnotationAttributes(ConditionalOnMissingProcessorType.class.getName()))
                .thenReturn(Map.of("type", processorType));
        return metadata;
    }

    @ProvidesProcessorType(type = ProcessorType.NON_BLOCKING)
    private static class NonBlockingTestProcessor implements TypeAwareIdempotentProcessor {

        @Override
        public Object process(OperationContext context, OperationMetadata metadata) {
            return null;
        }

        @Override
        public ProcessorType getType() {
            return ProcessorType.NON_BLOCKING;
        }
    }

    private static class UndeclaredTestProcessor implements TypeAwareIdempotentProcessor {

        @Override
        public Object process(OperationContext context, OperationMetadata metadata) {
            return null;
        }

        @Override
        public ProcessorType getType() {
            return ProcessorType.NON_BLOCKING;
        }
    }

    @ProvidesProcessorType(type = ProcessorType.BLOCKING)
    private static class ExplodingTestProcessor implements TypeAwareIdempotentProcessor {

        ExplodingTestProcessor() {
            throw new IllegalStateException("this processor must never be created by a condition");
        }

        @Override
        public Object process(OperationContext context, OperationMetadata metadata) {
            return null;
        }

        @Override
        public ProcessorType getType() {
            return ProcessorType.BLOCKING;
        }
    }
}
