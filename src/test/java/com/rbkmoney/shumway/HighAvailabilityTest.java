package com.rbkmoney.shumway;

import com.rbkmoney.damsel.accounter.AccounterSrv;
import com.rbkmoney.shumway.dao.SupportAccountDao;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static com.rbkmoney.shumway.utils.AccountUtils.*;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@Slf4j
public class HighAvailabilityTest {
    private static final int NUMBER_OF_THREADS = 8;
    private static final int SIZE_OF_QUEUE = NUMBER_OF_THREADS * 8;
    private static final int NUMBER_OF_ACCS = 40000;
    private static final int AMOUNT = 1000;

//    @Autowired
    AccounterSrv.Iface client;

    @Autowired
    SupportAccountDao supportAccountDao;

    @LocalServerPort
    int port;

//    @ClassRule
//    public static DockerComposeRule docker = DockerComposeRule.builder()
//            .file("src/test/resources/docker-compose.yml")
//            .logCollector(new FileLogCollector(new File("target/pglog")))
//            .waitingForService("postgres", HealthChecks.toHaveAllPortsOpen())
//            .build();

    @Test
//    @Ignore
    public void testRemote() throws URISyntaxException, TException, InterruptedException {
        THSpawnClientBuilder clientBuilder = new THSpawnClientBuilder().withAddress(new URI("http://localhost:" + getPort() + "/accounter"));
        client = clientBuilder.build(AccounterSrv.Iface.class);
        testHighAvailability();
    }

    @Test
    public void testLocal() throws URISyntaxException, TException, InterruptedException {
        testHighAvailability();
    }

    // move money 1 -> 2 -> 3 .. -> N -> 1
    // after all transactions amount on all accounts should be zero
    // also check intermediate amounts
    private void testHighAvailability() throws TException, InterruptedException {
        long totalStartTime = System.currentTimeMillis();
        assertNotNull(client);

        List<Long> accs = createAccs(NUMBER_OF_ACCS, supportAccountDao);

        startCircleTransfer(client, accs, NUMBER_OF_THREADS, SIZE_OF_QUEUE, AMOUNT);
        startCircleCheck(client, accs, 0);

        log.warn("Total time: {}ms", (System.currentTimeMillis() - totalStartTime));
    }

    private int getPort() {
        return port;
    }
}