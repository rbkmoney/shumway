package com.rbkmoney.shumway.integration;

import com.rbkmoney.easyway.AbstractTestUtils;
import com.rbkmoney.easyway.EnvironmentProperties;
import com.rbkmoney.easyway.TestContainers;
import com.rbkmoney.easyway.TestContainersBuilder;
import com.rbkmoney.easyway.TestContainersParameters;
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

    private static TestContainers testContainers =
            TestContainersBuilder.builderWithTestContainers(TestContainersParameters::new)
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

    private static Consumer<EnvironmentProperties> getEnvironmentPropertiesConsumer() {
        return environmentProperties -> {
            PostgreSQLContainer postgreSqlContainer = testContainers.getPostgresqlTestContainer().get();
            environmentProperties.put("spring.datasource.url", postgreSqlContainer.getJdbcUrl());
            environmentProperties.put("spring.datasource.username", postgreSqlContainer.getUsername());
            environmentProperties.put("spring.datasource.password", postgreSqlContainer.getPassword());
            environmentProperties.put("spring.flyway.url", postgreSqlContainer.getJdbcUrl());
            environmentProperties.put("spring.flyway.user", postgreSqlContainer.getUsername());
            environmentProperties.put("spring.flyway.password", postgreSqlContainer.getPassword());
        };
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues
                    .of(testContainers.getEnvironmentProperties(getEnvironmentPropertiesConsumer()))
                    .applyTo(configurableApplicationContext);
        }
    }
}
