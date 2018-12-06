package com.rbkmoney.shumway;

import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Created by jeckep on 08.02.17.
 */

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = ShumwayApplication.class, initializers = AbstractIntegrationTest.Initializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {
    @ClassRule
    public static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:10.5");

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            initDockerEnv(configurableApplicationContext);
        }

        private static void initDockerEnv(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword(),
                    "flyway.url=" + postgres.getJdbcUrl(),
                    "flyway.user=" + postgres.getUsername(),
                    "flyway.password=" + postgres.getPassword()
            ).applyTo(configurableApplicationContext);
        }

        private static void initLocalEnv(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "spring.datasource.url=jdbc:postgresql://localhost:32778/accounter",
                    "spring.datasource.username=postgres",
                    "spring.datasource.password=",
                    "flyway.url=jdbc:postgresql://localhost:32778/accounter",
                    "flyway.user=postgres",
                    "flyway.password="
            ).applyTo(configurableApplicationContext);
        }
    }

    @LocalServerPort
    protected int port;
}
