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
import java.util.Arrays;
import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

//@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:test.properties")
public class PerformanceTest3 {
    private static final String DUMP_PATH = "new10k_accs_100r.bak";
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

//        utils.restoreDump(DUMP_PATH);
//        utils.createSnapshot();
        utils.restoreSnapshot();
        utils.psqlCommit("drop index shm.account_log_account_id_idx;");
        utils.psqlCommit("drop index shm.acc_test_idx;");
        utils.psqlCommit("create index acc_test_idx on shm.account_log using btree (account_id, plan_id, batch_id);");
        utils.vacuumAnalyze();
    }

    @Test
    public void test1() throws Exception{
        List<Integer> numberOfThreadsSet = Arrays.asList(1,8);
        List<Integer> numberOfAccsSet = Arrays.asList(100, 1000);
        List<Integer> numberOfRoundsSet = Arrays.asList(10, 100);

        for(int numberOfThread: numberOfThreadsSet){
            for(int numberOfAccs: numberOfAccsSet){
                for(int numberOfRounds: numberOfRoundsSet){
//                    utils.restoreSnapshot();
                    utils.vacuumAnalyze();
                    test(numberOfThread, numberOfAccs, numberOfRounds);
                }
            }
        }
    }

    public void test(int numberOfThread, int numberOfAccs, int numberOfRounds) throws InterruptedException {
        final int SIZE_OF_QUEUE = 100;
        final int AMOUNT = 1000;
        final List<Long> accIds = AccountUtils.createAccs(numberOfAccs, supportAccountDao);
        final double avgTime = AccountUtils.startCircleTransfer(client, accIds, numberOfThread, SIZE_OF_QUEUE, AMOUNT, numberOfRounds);

        System.out.println("NUMBER_OF_THREADS: " + numberOfThread);
        System.out.println("NUMBER_OF_ACCS: " + numberOfAccs);
        System.out.println("NUMBER_OF_ROUNDS: " + numberOfRounds);
        System.out.println("AVG_TIME(ms): " + avgTime);
        System.out.println("");
    }

}
