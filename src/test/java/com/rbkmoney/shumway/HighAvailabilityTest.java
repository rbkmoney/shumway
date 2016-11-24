package com.rbkmoney.shumway;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.shumway.dao.SupportAccountDao;
import com.rbkmoney.shumway.handler.ProtocolConverter;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import org.apache.thrift.TException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:test.properties")
public class HighAvailabilityTest {
    private  final Logger log = LoggerFactory.getLogger(this.getClass());
    private static final long TIMEOUT = 5000;
    private static final int NUMBER_OF_THREADS = 8;
    private static final int SIZE_OF_QUEUE = NUMBER_OF_THREADS * 8;
    private static final int NUMBER_OF_ACCS = 40000;
    private static final int AMOUNT = 1000;

    @Autowired
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
    @Ignore
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
        final ExecutorService executorService = newFixedThreadPoolWithQueueSize(NUMBER_OF_THREADS, SIZE_OF_QUEUE);

        long startTime = System.currentTimeMillis();
        List<Long> accs = createAccs(NUMBER_OF_ACCS, supportAccountDao);
        log.warn("CreateAccs({}) execution time: {}ms", NUMBER_OF_ACCS, (System.currentTimeMillis() - startTime));
        startTime = System.currentTimeMillis();

        for(int i=0; i < accs.size(); i++){
            final long from = accs.get(i);
            final long to = accs.get((i+1) % accs.size());
            final String ppid = System.currentTimeMillis() + "_" + i;

            final PostingPlan postingPlan = createNewPostingPlan(ppid, from, to, AMOUNT);

            executorService.submit(() -> makeAndTestTransfer(postingPlan, client));
        }
        log.warn("All transactions submitted.");

        executorService.shutdown();
        boolean success = executorService.awaitTermination(SIZE_OF_QUEUE, TimeUnit.SECONDS);
        if(!success){
            log.error("Waiting was terminated by timeout");
        }

        log.warn("Transactions execution time from start: {}ms", (System.currentTimeMillis() - startTime));
        startTime = System.currentTimeMillis();
        for(long accId: accs){
            Account acc = retry(() -> client.getAccountByID(accId));
            assertEquals("Acc ID: " + acc.getId(), 0, acc.getOwnAmount());
            assertEquals("Acc ID: " + acc.getId(), 0, acc.getMaxAvailableAmount());
            assertEquals("Acc ID: " + acc.getId(), 0, acc.getMinAvailableAmount());
        }
        log.warn("Check amounts on accs: {}ms", (System.currentTimeMillis() - startTime));
        log.warn("Total time: {}ms", (System.currentTimeMillis() - totalStartTime));
    }

    private void makeAndTestTransfer(PostingPlan postingPlan, AccounterSrv.Iface client){
        log.info("Try hold plan. " + postingPlan.getId());
        postingPlan.getBatchList().stream().forEach(batch ->
                retry(() -> client.hold(new PostingPlanChange(postingPlan.getId(), batch))));

        log.info("Plan was held. " + postingPlan.getId());

        //TODO: maybe random time sleep if transfers are made in different threads

        log.info("Try commit plan. " + postingPlan.getId());
        retry(() -> client.commitPlan(postingPlan));
        log.info("Plan committed. " + postingPlan.getId());
    }

    public static List<Long> createAccs(int N, SupportAccountDao supportAccountDao) throws TException {
        AccountPrototype prototype = new AccountPrototype("RUB");
        prototype.setDescription("Test");

        com.rbkmoney.shumway.domain.Account domainPrototype = ProtocolConverter.convertToDomainAccount(prototype);

        return supportAccountDao.add(domainPrototype, N);
    }

    public static List<Long> createAccs(int N, AccounterSrv.Iface client) throws TException {
        ArrayList<Long> accs = new ArrayList<>();
        for(int i=0; i < N; i++){
            accs.add(createAcc(client));
        }

        return accs;
    }

    public static long createAcc(AccounterSrv.Iface client) throws TException {
        AccountPrototype prototype = new AccountPrototype("RUB");
        prototype.setDescription("Test");
        return client.createAccount(prototype);
    }

    public static PostingPlan createNewPostingPlan(String ppid, long accFrom, long accTo, long amount){
        Posting posting = new Posting(accFrom, accTo, amount, "RUB", "Desc");
        return new PostingPlan(ppid, Arrays.asList(new PostingBatch(1, Arrays.asList(posting))));
    }

    private int getPort(){
        return port;
    }

    private <T> T retry(Callable<T> a){
        return new SimpleRetrier<>(a, TIMEOUT).retry();
    }

    private static class SimpleRetrier<T>{
        private final Logger log = LoggerFactory.getLogger(this.getClass());
        private Callable<T> action;
        private long timeout;

        SimpleRetrier(Callable<T> action, long timeout) {
            this.action = action;
            this.timeout = timeout;
        }

        T retry(){
            while (true){
                try {
                    return action.call();
                }catch (Exception e){
                    log.error("Exception during doing some retryable actions. Wait {} ms and execute.", timeout);
                    log.error("Description: ",e);
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException ignored) {
                        log.warn("Interrupted during waiting for execute.");
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private static ExecutorService newFixedThreadPoolWithQueueSize(int nThreads, int queueSize) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize, true), new ThreadPoolExecutor.CallerRunsPolicy());
    }
}