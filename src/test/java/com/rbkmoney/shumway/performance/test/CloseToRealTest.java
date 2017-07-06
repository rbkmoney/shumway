package com.rbkmoney.shumway.performance.test;

import com.rbkmoney.damsel.accounter.AccounterSrv;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.SupportAccountDao;
import com.rbkmoney.shumway.performance.PostgresUtils;
import com.rbkmoney.shumway.utils.AccountUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

//@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:test.properties")
@Ignore
public class CloseToRealTest {
    private static final int NUMBER_OF_THREADS = 8;
    private static final int SIZE_OF_QUEUE = NUMBER_OF_THREADS * 8;

    private static PostgresUtils utils;

    @Autowired
    SupportAccountDao supportAccountDao;

    @Autowired
    AccountDao accountDao;

    @Autowired
    AccounterSrv.Iface client;

    @BeforeClass
    public static void beforeAllTestOnlyOnce() throws IOException {

        utils = PostgresUtils.builder()
                .host("localhost")
                .port(5432)
                .superUser("postgres")
                .password("postgres")
                .database("shumway")
                .bashScriptPath(new ClassPathResource("db/docker-wrapper.sh").getFile().getAbsolutePath())
                .containerId("a4f0d3b9f7de")
                .bashScriptInContainerPath("/src/test/resources/db/utils.sh")
                .showOutput(true)

                .build();

        utils.dropDb();
        utils.createDb();
    }

    @Test
    public void test() throws Exception{
        final int numberOfMerchantAccs = 10000;

        List<Long> providerAccs = AccountUtils.createAccs(10, supportAccountDao);
        List<Long> rbkMoneyAccs = AccountUtils.createAccs(10, supportAccountDao);
        List<Long> merchantAccs = AccountUtils.createAccs(numberOfMerchantAccs, supportAccountDao);

        for(int i=0; i < 10; i++) {
            //utils.vacuumAnalyze();

            int numberOfRounds = 10000;
            double avgTime = AccountUtils.emulateRealTransfer(client, providerAccs, rbkMoneyAccs, merchantAccs, numberOfRounds,
                    NUMBER_OF_THREADS, SIZE_OF_QUEUE);

            System.out.println("Emulate real transfer:");
            System.out.println("NUMBER_OF_THREADS: " + NUMBER_OF_THREADS);
            System.out.println("NUMBER_OF_ROUNDS: " + numberOfRounds);
            System.out.println("AVG_TIME(ms): " + avgTime);
        }
    }
}
