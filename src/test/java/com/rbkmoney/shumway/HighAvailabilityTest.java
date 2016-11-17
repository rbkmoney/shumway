package com.rbkmoney.shumway;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import com.palantir.docker.compose.logging.FileLogCollector;
import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.shumway.handler.ProtocolConverter;
import com.rbkmoney.shumway.service.AccountService;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private static final int numberOfAccs = 10000;
    private static final int amount = 1000;

    @Autowired
    AccounterSrv.Iface client;

    @Autowired
    AccountService accountService;

    @LocalServerPort
    int port;

    @ClassRule
    public static DockerComposeRule docker = DockerComposeRule.builder()
            .file("src/test/resources/docker-compose.yml")
            .logCollector(new FileLogCollector(new File("target/pglog")))
            .waitingForService("postgres", HealthChecks.toHaveAllPortsOpen())
            .build();

    @Test
    @Ignore
    public void testRemote() throws URISyntaxException, TException {
        THSpawnClientBuilder clientBuilder = new THSpawnClientBuilder().withAddress(new URI("http://localhost:" + getPort() + "/accounter"));
        client = clientBuilder.build(AccounterSrv.Iface.class);
        testHighAvailability();
    }

    @Test
    public void testLocal() throws URISyntaxException, TException {
        testHighAvailability();
    }

    // move money 1 -> 2 -> 3 .. -> N -> 1
    // after all transactions amount on all accounts should be zero
    // also check intermediate amounts
    private void testHighAvailability() throws TException {
        assertNotNull(client);

        long startTime = System.currentTimeMillis();
        List<Long> accs = createAccs(numberOfAccs, accountService);
        log.info("CreateAccs({}) execution time: {}ms", numberOfAccs, (System.currentTimeMillis() - startTime));

        log.info("end");
        for(int i=0; i < accs.size(); i++){
            long from = accs.get(i);
            long to = accs.get((i+1) % accs.size());

            makeAndTestTransfer(from, to, amount, client);
        }

        for(long accId: accs){
            Account acc = retry(() -> client.getAccountByID(accId));
            assertEquals("Acc ID: " + acc.getId(), 0, acc.getAvailableAmount());
        }
    }

    private void makeAndTestTransfer(long from, long to, long amount, AccounterSrv.Iface client){
        final Account accFromBefore = retry(() -> client.getAccountByID(from));
        final Account accToBefore = retry(() -> client.getAccountByID(to));
        final String ppid = System.currentTimeMillis() + "";

        transfer(ppid, accFromBefore, accToBefore, amount, client);

        final Account accFromAfter = retry(() -> client.getAccountByID(from));
        final Account accToAfter = retry(() -> client.getAccountByID(to));

        assertEquals("Account ID: " + from, amount, accFromBefore.getAvailableAmount() - accFromAfter.getAvailableAmount());
        assertEquals("Account ID: " + from,-amount, accToBefore.getAvailableAmount() - accToAfter.getAvailableAmount());
    }

    public void transfer(String ppid, Account accFrom , Account accTo, long amount, AccounterSrv.Iface client){
        final PostingPlan postingPlan = createPostingPlan(ppid, accFrom.getId(), accTo.getId(), amount);

        retry(() -> client.hold(postingPlan));
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

    public static PostingPlan createPostingPlan(String ppId,long accFrom, long accTo, long amount){
        Posting posting = new Posting(1, accFrom, accTo, amount, "RUB", "Desc");
        return new PostingPlan(ppId, Arrays.asList(posting));
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
}