package com.rbkmoney.shumway.performance.test;

import com.rbkmoney.damsel.accounter.AccounterSrv;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.SupportAccountDao;
import com.rbkmoney.shumway.performance.PostgresUtils;
import com.rbkmoney.shumway.utils.AccountUtils;
import org.junit.BeforeClass;
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

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:test.properties")
public class PerformanceTest {
    private static final int NUMBER_OF_THREADS = 8;
    private static final int SIZE_OF_QUEUE = NUMBER_OF_THREADS * 8;
    private static final int NUMBER_OF_ACCS = 1000;
    private static final int AMOUNT = 1000;

    private static final String DUMP_PATH = "one_million.bak";
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
                .bashScriptPath(new ClassPathResource("db/utils.sh").getFile().getAbsolutePath())
                .showOutput(true)
                .build();

//        utils.restoreDump(DUMP_PATH);
        utils.restoreSnapshot();
        utils.vacuumAnalyze();
    }

    @Test
    public void test() throws InterruptedException {
        List<Long> accIds = AccountUtils.createAccs(NUMBER_OF_ACCS, supportAccountDao);
        AccountUtils.startCircleTransfer(client, accIds, NUMBER_OF_THREADS, SIZE_OF_QUEUE, AMOUNT);
        AccountUtils.startCircleCheck(accountDao, accIds, 0);

        utils.createDump("two_million.bak");
    }


    private static void t(String preffix, Runnable function){
        long startTime = System.currentTimeMillis();
        function.run();
        System.out.println(preffix + ": " + (System.currentTimeMillis() - startTime) + "ms.");
    }
}
