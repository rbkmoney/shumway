package com.rbkmoney.shumway.integration;

import com.rbkmoney.easyway.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.FailureDetectingExternalResource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.function.Consumer;

@RunWith(SpringRunner.class)
@ContextConfiguration(initializers = DaoTestBase.Initializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Slf4j
public abstract class DaoTestBase extends AbstractTestUtils {

    private static TestContainers testContainers = TestContainersBuilder.builderWithTestContainers(TestContainersParameters::new)
            .addPostgresqlTestContainer()
            .build();

    @ClassRule
    public static final FailureDetectingExternalResource resource = new FailureDetectingExternalResource() {

        @Override
        protected void starting(Description description) {
            testContainers.startTestContainers();
        }

        @Override
        protected void failed(Throwable e, Description description) {
            log.warn("Test Container start failed ", e);
        }

        @Override
        protected void finished(Description description) {
            testContainers.stopTestContainers();
        }
    };

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues
                    .of(testContainers.getEnvironmentProperties(getEnvironmentPropertiesConsumer()))
                    .applyTo(configurableApplicationContext);
        }
    }

    private static Consumer<EnvironmentProperties> getEnvironmentPropertiesConsumer() {
        return environmentProperties -> {
            PostgreSQLContainer postgreSQLContainer = testContainers.getPostgresqlTestContainer().get();
            environmentProperties.put("spring.datasource.url", postgreSQLContainer.getJdbcUrl());
            environmentProperties.put("spring.datasource.username", postgreSQLContainer.getUsername());
            environmentProperties.put("spring.datasource.password", postgreSQLContainer.getPassword());
            environmentProperties.put("spring.flyway.url", postgreSQLContainer.getJdbcUrl());
            environmentProperties.put("spring.flyway.user", postgreSQLContainer.getUsername());
            environmentProperties.put("spring.flyway.password", postgreSQLContainer.getPassword());
        };
    }
}
