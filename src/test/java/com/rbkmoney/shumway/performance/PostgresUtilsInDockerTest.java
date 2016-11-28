package com.rbkmoney.shumway.performance;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import com.palantir.docker.compose.logging.FileLogCollector;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;

public class PostgresUtilsInDockerTest {
    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/test/resources/docker-compose.yml")
            .logCollector(new FileLogCollector(new File("target/pglog")))
            .waitingForService("postgres", HealthChecks.toHaveAllPortsOpen())
            .build();

    @Test
    public void testAllInOne() throws IOException {
        final String bashScriptPath = new ClassPathResource("db/utils.sh").getFile().getAbsolutePath();

        final PostgresUtils utils = PostgresUtils.builder()
                .host("localhost")
                .port(5000)
                .superUser("postgres")
                .password("postgres")
                .database("shumway")
                .bashScriptPath(bashScriptPath)
                .build();

        final String dumpPath = "/tmp/shumway.dump";

        utils.dropDb();
        utils.createDb();
        utils.createDump(dumpPath);
        utils.createSnapshot();
        utils.dropDb();
        utils.restoreDump(dumpPath);
        utils.restoreSnapshot();
        utils.dropSnapshot();
    }
}
