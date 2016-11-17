package com.rbkmoney.shumway;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.shumway.handler.ProtocolConverter;
import com.rbkmoney.shumway.service.AccountService;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:test.properties")
@Slf4j
public class HighAvailabilityTest {
    private static final long TIMEOUT = 5000;
    private static final int NUMBER_OF_THREADS = 8;
    private static final int SIZE_OF_QUEUE = NUMBER_OF_THREADS * 8;
    private static final int NUMBER_OF_ACCS = 40000;
    private static final int AMOUNT = 1000;

    @Autowired
    AccounterSrv.Iface client;

    @Autowired
    AccountService accountService;

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
        assertNotNull(client);
        final ExecutorService executorService = newFixedThreadPoolWithQueueSize(NUMBER_OF_THREADS, SIZE_OF_QUEUE);

        long startTime = System.currentTimeMillis();
        List<Long> accs = createAccs(NUMBER_OF_ACCS, accountService);
        log.info("CreateAccs({}) execution time: {}ms", NUMBER_OF_ACCS, (System.currentTimeMillis() - startTime));

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

        for(long accId: accs){
            Account acc = retry(() -> client.getAccountByID(accId));
            assertEquals("Acc ID: " + acc.getId(), 0, acc.getAvailableAmount());
        }
    }

    private void makeAndTestTransfer(PostingPlan postingPlan, AccounterSrv.Iface client){
        log.info("Try hold plan. " + postingPlan.getId());
        retry(() -> client.hold(postingPlan));
        log.info("Plan was held. " + postingPlan.getId());

        //TODO: maybe random time sleep if transfers are made in different threads

        log.info("Try commit plan. " + postingPlan.getId());
        retry(() -> client.commitPlan(postingPlan));
        log.info("Plan committed. " + postingPlan.getId());
    }

    public static List<Long> createAccs(int N, AccountService service) throws TException {
        AccountPrototype prototype = new AccountPrototype("RUB");
        prototype.setDescription("Test");

        com.rbkmoney.shumway.domain.Account domainPrototype = ProtocolConverter.convertToDomainAccount(prototype);

        return service.createAccounts(domainPrototype, N);
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
        Posting posting = new Posting(1, accFrom, accTo, amount, "RUB", "Desc");
        return new PostingPlan(ppid, Arrays.asList(posting));
    }

    private int getPort(){
        return port;
    }

    @FunctionalInterface
    private interface Action<T> {
        T execute() throws Exception;
    }

    private <T> T retry(Action<T> a){
        return new SimpleRetrier<>(a, TIMEOUT).retry();
    }

    private static class SimpleRetrier<T>{
        private Action<T> action;
        private long timeout;

        SimpleRetrier(Action<T> action, long timeout) {
            this.action = action;
            this.timeout = timeout;
        }

        T retry(){
            while (true){
                try {
                    return action.execute();
                }catch (Exception e){
                    log.error("Exception during doing some retryable actions. Wait {} ms and execute.", timeout);
                    log.error("Description: ",e);
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException ignored) {
                        log.warn("Interrupted during waiting for execute.");
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