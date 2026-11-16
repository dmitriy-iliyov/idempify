package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdempifySchemaMigratorDatabaseInitializerDetectorUnitTest {

    private final IdempifySchemaMigratorDatabaseInitializerDetector tested =
            new IdempifySchemaMigratorDatabaseInitializerDetector();

    @Test
    @DisplayName("UT detect() when a migrator bean is defined should name it as a database initializer")
    void detect_whenMigratorBeanIsDefined_shouldNameItAsDatabaseInitializer() {
        // given
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("migrator", new RootBeanDefinition(NoopIdempifySchemaMigrator.class));
        beanFactory.registerBeanDefinition("other", new RootBeanDefinition(Object.class));

        // when
        Set<String> result = tested.detect(beanFactory);

        // then
        assertThat(result).containsExactly("migrator");
    }

    @Test
    @DisplayName("UT getOrder() should come after the application's own Flyway")
    void getOrder_shouldComeAfterApplicationsOwnFlyway() {
        // when / then
        assertThat(tested.getOrder()).isGreaterThan(1);
    }

    @Test
    @DisplayName("UT detector should be registered so that Spring Boot finds it without the application's help")
    void detector_shouldBeRegisteredSoThatSpringBootFindsItWithoutApplicationsHelp() {
        // when
        List<DatabaseInitializerDetector> detectors = SpringFactoriesLoader.forDefaultResourceLocation()
                .load(DatabaseInitializerDetector.class);

        // then
        assertThat(detectors).hasAtLeastOneElementOfType(IdempifySchemaMigratorDatabaseInitializerDetector.class);
    }
}
