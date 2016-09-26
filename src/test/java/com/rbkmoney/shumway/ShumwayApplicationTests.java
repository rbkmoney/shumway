package com.rbkmoney.shumway;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import org.apache.thrift.TException;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class ShumwayApplicationTests {
    private AccounterSrv.Iface client = createClient("http://localhost:8022/accounter");

    @Test
    public void testAddGetAccount() throws TException {
        AccountPrototype prototype = new AccountPrototype("RUB");
        prototype.setDescription("Test");
        long id = client.createAccount(prototype);
        Account sentAccount = client.getAccountByID(id);
        assertNotNull(sentAccount);
        assertEquals(0, sentAccount.getAvailableAmount());
        assertEquals(prototype.getCurrencySymCode(), sentAccount.getCurrencySymCode());
        assertEquals(prototype.getDescription(), sentAccount.getDescription());
    }

    @Test
    public void testGetNotExistingAccount() throws TException {
        try {
            client.getAccountByID(Long.MAX_VALUE);
        } catch (AccountNotFound e) {
            assertEquals(Long.MAX_VALUE, e.getAccountId());
            return;
        }
    }

    @Test
    public void testGetNotExistingPlan() throws TException {
        try {
            client.getPlan(Long.MAX_VALUE + "");
        } catch (PlanNotFound e) {
            assertEquals(Long.MAX_VALUE + "", e.getPlanId());
            return;
        }
    }

    @Test
    public void testAddEmptyGetPlan() throws TException {
        String planId = System.currentTimeMillis() + "";
        PostingPlan postingPlan = new PostingPlan(planId, Arrays.asList());
        PostingPlanLog planLog = client.hold(postingPlan);
        assertEquals(postingPlan.getId(), planLog.getPlan().getId());
        assertArrayEquals(postingPlan.getBatch().toArray(), planLog.getPlan().getBatch().toArray());
        assertEquals(0, planLog.getAffectedAccountsSize());
    }

    @Test
    public void testAddFullGetPlan() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        Posting posting = new Posting(1, id, id, -1, "RU", "Desc");
        PostingPlan postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        PostingPlanLog planLog = null;
        try {
            planLog = client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertEquals("Source and target accounts cannot be the same", e.getWrongPostings().get(posting));
        }

        posting = new Posting(1, id - 1, id, -1, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            planLog = client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertEquals("Amount cannot be negative", e.getWrongPostings().get(posting));
        }
        posting = new Posting(1, id - 1, id, 1, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            planLog = client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertEquals("Source account not found", e.getWrongPostings().get(posting));
        }

        long fromAccountId = client.createAccount(new AccountPrototype(posting.getCurrencySymCode()));
        posting = new Posting(1, fromAccountId, id, 1, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            planLog = client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertEquals("Target account not found", e.getWrongPostings().get(posting));
        }

        long toAccountId = client.createAccount(new AccountPrototype(posting.getCurrencySymCode()));
        posting = new Posting(1, fromAccountId, toAccountId, 1, "ERR", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            planLog = client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertEquals("Referred currency code is not equal to account code: RU", e.getWrongPostings().get(posting));
        }

        posting = new Posting(1, fromAccountId, toAccountId, 1, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        planLog = client.hold(postingPlan);

        assertEquals(planLog.getPlan(), client.getPlan(planLog.getPlan().getId()));
        assertEquals(planLog.getPlan(), client.getPlan(postingPlan.getId()));

        assertEquals("Duplicate request, result must be equal", planLog, client.hold(postingPlan));

        Posting posting2 = new Posting(2, fromAccountId, toAccountId, 5, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting, posting2));

        try {
            client.commitPlan(postingPlan);
        } catch (InvalidRequest ex) {
            assertEquals(1, ex.getErrorsSize());
            assertEquals("Posting with id '2' not found", ex.getErrors().get(0));
        }

        postingPlan = new PostingPlan(planId, Lists.emptyList());
        try {
            client.commitPlan(postingPlan);
        } catch (InvalidRequest ex) {
            assertEquals(1, ex.getErrorsSize());
            assertEquals("Posting with id '1' not found", ex.getErrors().get(0));
        }

        postingPlan = new PostingPlan(planId, Arrays.asList(posting2));
        try {
            client.commitPlan(postingPlan);
        } catch (InvalidRequest ex) {
            assertEquals(2, ex.getErrorsSize());
        }

        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        client.commitPlan(postingPlan);

    }


    public static AccounterSrv.Iface createClient(String url) {
        try {
            THSpawnClientBuilder clientBuilder = new THSpawnClientBuilder().withAddress(new URI(url));
            return clientBuilder.build(AccounterSrv.Iface.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
