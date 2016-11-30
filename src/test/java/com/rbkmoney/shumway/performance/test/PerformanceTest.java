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

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:test.properties")
public class PerformanceTest {
    private static final int NUMBER_OF_THREADS = 8;
    private static final int SIZE_OF_QUEUE = NUMBER_OF_THREADS * 8;
    private static final int NUMBER_OF_ACCS = 100;
    private static final int AMOUNT = 1000;

    private static final String DUMP_PATH = "10_000.bak";
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

//        t("restoreDump",() -> utils.restoreDump(DUMP_PATH));
//        t("createSnapshot",() -> utils.createSnapshot());
    }

    @Test
    public void test1() throws Exception{
        utils.restoreSnapshot();
        utils.vacuumAnalyze();
        test();
    }

    @Test
    public void test2() throws Exception{
        utils.restoreSnapshot();
        utils.psqlCommit("drop index shm.account_log_plan_id_idx;");
        utils.psqlCommit("alter table shm.posting_log drop constraint posting_log_pkey;");
        utils.vacuumAnalyze();
        test();
    }

    @Test
    public void test3() throws Exception {
        utils.restoreSnapshot();
        utils.psqlCommit("drop index shm.account_log_plan_id_idx;");
        utils.psqlCommit("drop index shm.account_log_account_id_operation_idx;");
        utils.psqlCommit("create index acc_test_idx on shm.account_log using btree (plan_id, batch_id, account_id, own_amount, own_amount_delta);");
        utils.psqlCommit("create index account_log_account_id_idx on shm.account_log using btree (account_id);");
        utils.vacuumAnalyze();
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

    private static void t(String preffix, Runnable function){
        long startTime = System.currentTimeMillis();
        function.run();
        System.out.println(preffix + ": " + (System.currentTimeMillis() - startTime) + "ms.");
    }
}
