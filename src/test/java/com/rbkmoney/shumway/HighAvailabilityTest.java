package com.rbkmoney.shumway;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import com.palantir.docker.compose.logging.FileLogCollector;
import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
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
public class HighAvailabilityTest {
    @Autowired
    AccounterSrv.Iface client;

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

    private void testHighAvailability() throws TException {
        assertNotNull(client);

        final int numberOfAccs = 100;
        final int ammount = 1000;

        long startTime = System.currentTimeMillis();
        List<Long> accs = createAccs(numberOfAccs, client);
        System.out.printf("CreateAccs(%d) execution time: %dms\n", numberOfAccs, (System.currentTimeMillis() - startTime));


        for(int i=0; i < accs.size() - 1; i++){
            long from = accs.get(i);
            long to = accs.get(i+1);
            transfer(from, to, ammount, client);
        }

        final long firstAccId = accs.get(0);
        final long lastAccId = accs.get(accs.size() - 1);

        transfer(lastAccId, firstAccId, ammount, client);

        for(long accId: accs){
            Account acc = client.getAccountByID(accId);
            assertEquals(acc.getAvailableAmount(), 0);
        }



        System.out.println("end.");
    }


    public void transfer(long accIdFrom, long accIdTo, long amount, AccounterSrv.Iface client) throws TException {
        // read accounts, imitate read and checks before postings
        Account accFrom = client.getAccountByID(accIdFrom);
        Account accTo = client.getAccountByID(accIdTo);

        String ppid = System.currentTimeMillis() + "";

        //TODO create retry around it
        PostingPlan postingPlan = createPostingPlan(ppid, accIdFrom, accIdTo, amount);

        client.hold(postingPlan);
        //TODO: maybe random time sleep if transfers are made in different threads
        client.commitPlan(postingPlan);
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
}