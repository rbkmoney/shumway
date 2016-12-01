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

//@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:test.properties")
public class PerformanceTest2 {
    private static final int NUMBER_OF_THREADS = 8;
    private static final int SIZE_OF_QUEUE = NUMBER_OF_THREADS * 8;
    private static final int NUMBER_OF_ACCS = 100;
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
                .showOutput(false)
                .build();

//        utils.dropDb();
//        utils.createDb();
    }

    @Test
    public void test1() throws Exception{
        test();
    }


    public void test() throws InterruptedException {
        List<Long> accIds = AccountUtils.createAccs(NUMBER_OF_ACCS, supportAccountDao);
        int numberOfRounds = 100;
        double avgTime = AccountUtils.startCircleTransfer(client, accIds, NUMBER_OF_THREADS, SIZE_OF_QUEUE, AMOUNT, numberOfRounds);

        System.out.println("NUMBER_OF_THREADS: " + NUMBER_OF_THREADS);
        System.out.println("NUMBER_OF_ACCS: " + NUMBER_OF_ACCS);
        System.out.println("NUMBER_OF_ROUNDS: " + numberOfRounds);
        System.out.println("AVG_TIME(ms): " + avgTime);
    }

}
