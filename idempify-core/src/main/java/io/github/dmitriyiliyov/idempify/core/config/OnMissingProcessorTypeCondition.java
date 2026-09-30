package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.TypeAwareIdempotentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;

public class OnMissingProcessorTypeCondition extends SpringBootCondition {

    private static final Logger log = LoggerFactory.getLogger(OnMissingProcessorTypeCondition.class);

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ProcessorType processorType = getProcessorType(metadata);
        String beanName = findProcessorOfType(processorType, context);
        if (beanName != null) {
            log.debug("Bean '{}' declares processor type {}, so the built-in one stays out", beanName, processorType);
            return ConditionOutcome.noMatch(
                    "Processor type %s is already covered by bean '%s'".formatted(processorType, beanName)
            );
        }
        return ConditionOutcome.match("No bean declares processor type %s yet".formatted(processorType));
    }

    private ProcessorType getProcessorType(AnnotatedTypeMetadata metadata) {
        String annotationName = ConditionalOnMissingProcessorType.class.getName();
        return (ProcessorType) AnnotationAttributeUtils.getAttribute(annotationName, "type", metadata);
    }

    /**
     * Definitions are read, never beans: the processors are still being registered when this runs, and
     * creating one to ask its {@code getType()} would pull its whole dependency graph into existence before
     * the context is ready to build it.
     */
    private String findProcessorOfType(ProcessorType processorType, ConditionContext context) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        if (beanFactory == null) {
            throw new IllegalStateException(
                    "%s needs a bean factory to read bean definitions from, but the context has none"
                            .formatted(OnMissingProcessorTypeCondition.class.getSimpleName())
            );
        }

        for (String beanName : beanFactory.getBeanNamesForType(TypeAwareIdempotentProcessor.class, true, false)) {
            if (processorType.equals(declaredProcessorType(beanFactory, beanName))) {
                return beanName;
            }
        }
        return null;
    }

    /**
     * A {@code @Bean} method declaring the interface as its return type carries the answer nowhere else, and
     * it is the more specific of the two: the class behind it may stand for more than one bean.
     */
    private ProcessorType declaredProcessorType(ConfigurableListableBeanFactory beanFactory, String beanName) {
        ProcessorType fromFactoryMethod = fromFactoryMethod(beanFactory, beanName);
        if (fromFactoryMethod != null) {
            return fromFactoryMethod;
        }
        return fromBeanClass(beanFactory, beanName);
    }

    private ProcessorType fromFactoryMethod(ConfigurableListableBeanFactory beanFactory, String beanName) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return null;
        }

        if (!(beanFactory.getBeanDefinition(beanName) instanceof AnnotatedBeanDefinition definition)) {
            return null;
        }

        MethodMetadata factoryMethod = definition.getFactoryMethodMetadata();
        if (factoryMethod == null) {
            return null;
        }

        String annotationName = ProvidesProcessorType.class.getName();
        return (ProcessorType) AnnotationAttributeUtils.findAttribute(annotationName, "type", factoryMethod);
    }

    private ProcessorType fromBeanClass(ConfigurableListableBeanFactory beanFactory, String beanName) {
        Class<?> beanClass = beanFactory.getType(beanName, false);
        if (beanClass == null) {
            return null;
        }

        ProvidesProcessorType annotation = beanClass.getAnnotation(ProvidesProcessorType.class);
        return annotation == null ? null : annotation.type();
    }
}
