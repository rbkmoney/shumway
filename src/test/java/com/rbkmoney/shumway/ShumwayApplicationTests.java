package com.rbkmoney.shumway;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import org.apache.thrift.TException;
import org.assertj.core.util.Lists;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rbkmoney.shumway.handler.AccounterValidator.*;
import static org.hamcrest.Matchers.matchesPattern;
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
        assertEquals(0, sentAccount.getOwnAmount());
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
    public void testEmptyHoldGetPlan() throws TException {
        String planId = System.currentTimeMillis() + "";
        PostingPlan postingPlan = new PostingPlan(planId, Arrays.asList());
        PostingPlanLog planLog = client.hold(postingPlan);
        assertEquals(postingPlan.getId(), planLog.getPlan().getId());
        assertArrayEquals(postingPlan.getBatch().toArray(), planLog.getPlan().getBatch().toArray());
        assertEquals(0, planLog.getAffectedAccountsSize());
    }

    @Test
    public void testErrHoldGetPlan() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        Posting posting = new Posting(1, id, id, -1, "RU", "Desc");
        PostingPlan postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(SOURCE_TARGET_ACC_EQUAL_ERR, AMOUNT_NEGATIVE_ERR));
        }

        posting = new Posting(1, id - 1, id, -1, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(AMOUNT_NEGATIVE_ERR));
        }
        posting = new Posting(1, id - 1, id, 1, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(SRC_ACC_NOT_FOUND_ERR, DST_ACC_NOT_FOUND_ERR));
        }

        try {
            client.getPlan(postingPlan.getId());
        } catch (PlanNotFound e) {
            assertEquals(planId, e.getPlanId());
        }
    }

    @Test
    public void testErrAccountHold() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        long fromAccountId = client.createAccount(new AccountPrototype("RU"));
        Posting posting = new Posting(1, fromAccountId, id, 1, "RU", "Desc");
        PostingPlan postingPlan = new PostingPlan(planId, Arrays.asList(posting));

        try {
            client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(DST_ACC_NOT_FOUND_ERR));
        }

        long toAccountId = client.createAccount(new AccountPrototype(posting.getCurrencySymCode()));
        posting = new Posting(1, fromAccountId, toAccountId, 1, "ERR", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        try {
            client.hold(postingPlan);
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(ACC_CURR_CODE_NOT_EQUAL_ERR, ACC_CURR_CODE_NOT_EQUAL_ERR));
        }

        posting = new Posting(1, fromAccountId, toAccountId, 1, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        client.hold(postingPlan);
    }

    @Test
    public void testHoldCommitPlan() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        long fromAccountId = client.createAccount(new AccountPrototype("RU"));
        long toAccountId = client.createAccount(new AccountPrototype("RU"));

        Posting posting = new Posting(1, fromAccountId, toAccountId, 1, "RU", "Desc");
        PostingPlan postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        PostingPlanLog planLog = client.hold(postingPlan);

        assertEquals(planLog.getPlan(), client.getPlan(planLog.getPlan().getId()));
        assertEquals(planLog.getPlan(), client.getPlan(postingPlan.getId()));
        assertEquals(2, planLog.getAffectedAccountsSize());
        assertEquals("Debit includes hold for src available amount", -posting.getAmount(), planLog.getAffectedAccounts().get(fromAccountId).getAvailableAmount());
        assertEquals("Debit doesn't include hold for src own amount ", 0, planLog.getAffectedAccounts().get(fromAccountId).getOwnAmount());
        assertEquals("Credit doesn't include hold for dst available amount", 0, planLog.getAffectedAccounts().get(toAccountId).getAvailableAmount());
        assertEquals("Credit doesn't include hold for dst own amount", 0, planLog.getAffectedAccounts().get(toAccountId).getOwnAmount());

        assertEquals("Duplicate request, result must be equal", planLog, client.hold(postingPlan));

        Posting posting2 = new Posting(2, fromAccountId, toAccountId, 5, "RU", "Desc");
        postingPlan = new PostingPlan(planId, Arrays.asList(posting, posting2));

        try {
            client.commitPlan(postingPlan);
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(POSTING_NEW_OLD_RECEIVED_ERR));
        }
        try {
            client.rollbackPlan(postingPlan);
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(POSTING_NEW_OLD_RECEIVED_ERR));
        }

        postingPlan = new PostingPlan(planId, Lists.emptyList());
        try {
            client.commitPlan(postingPlan);
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting), genMatcher(SAVED_POSTING_NOT_FOUND_ERR));
        }
        try {
            client.rollbackPlan(postingPlan);
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting), genMatcher(SAVED_POSTING_NOT_FOUND_ERR));
        }

        postingPlan = new PostingPlan(planId, Arrays.asList(posting2));
        try {
            client.commitPlan(postingPlan);
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(POSTING_NOT_FOUND_ERR));
        }
        try {
            client.rollbackPlan(postingPlan);
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(POSTING_NOT_FOUND_ERR));
        }

        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        planLog = client.commitPlan(postingPlan);
        assertEquals(2, planLog.getAffectedAccountsSize());
        assertEquals("Debit includes commit for src available amount", -posting.getAmount(), planLog.getAffectedAccounts().get(fromAccountId).getAvailableAmount());
        assertEquals("Debit includes commit for src own amount ", -posting.getAmount(), planLog.getAffectedAccounts().get(fromAccountId).getOwnAmount());
        assertEquals("Credit includes commit for dst available amount", posting.getAmount(), planLog.getAffectedAccounts().get(toAccountId).getAvailableAmount());
        assertEquals("Credit includes commit for dst own amount", posting.getAmount(), planLog.getAffectedAccounts().get(toAccountId).getOwnAmount());

        try {
            client.hold(postingPlan);
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_STATE_CHANGE_ERR));
        }

        assertEquals("Duplicate request, result must be equal", planLog, client.commitPlan(postingPlan));

        try {
            client.rollbackPlan(postingPlan);
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_STATE_CHANGE_ERR));
        }

        assertEquals("Duplicate request, result must be equal", planLog, client.commitPlan(postingPlan));
    }

    @Test
    public void testHoldRollbackPlan() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        long fromAccountId = client.createAccount(new AccountPrototype("RU"));
        long toAccountId = client.createAccount(new AccountPrototype("RU"));

        Posting posting = new Posting(1, fromAccountId, toAccountId, 1, "RU", "Desc");
        PostingPlan postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        PostingPlanLog planLog = client.hold(postingPlan);

        assertEquals("Duplicate request, result must be equal", planLog, client.hold(postingPlan));

        postingPlan = new PostingPlan(planId, Arrays.asList(posting));
        planLog = client.rollbackPlan(postingPlan);

        try {
            client.hold(postingPlan);
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_STATE_CHANGE_ERR));
        }

        assertEquals("Duplicate request, result must be equal", planLog, client.rollbackPlan(postingPlan));

        try {
            client.commitPlan(postingPlan);
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_STATE_CHANGE_ERR));
        }

        assertEquals("Duplicate request, result must be equal", planLog, client.rollbackPlan(postingPlan));
    }

    @Test
    public void testEmptyPlan() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        PostingPlan postingPlan = new PostingPlan(planId, Collections.emptyList());
        try {
            client.commitPlan(postingPlan);
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_NOT_FOUND_ERR));
        }
        try {
           client.rollbackPlan(postingPlan);
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_NOT_FOUND_ERR));
        }
        PostingPlanLog planLog = new PostingPlanLog(postingPlan);
        planLog.setAffectedAccounts(Collections.emptyMap());

        assertEquals(planLog, client.hold(postingPlan));

        assertEquals(postingPlan, client.getPlan(planId));

        Posting posting = new Posting(1, 0, 1, 1, "RU", "Desc");
        try {
            client.commitPlan(new PostingPlan(planId, Arrays.asList(posting)));
        } catch (InvalidPostingParams e) {
            assertThat(e.getWrongPostings().get(posting), genMatcher(POSTING_NOT_FOUND_ERR));
        }

        assertEquals(planLog, client.commitPlan(postingPlan));
    }

    @Test
    public void testMultiplePlans() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        long fromAccountId1 = client.createAccount(new AccountPrototype("RU"));
        long toAccountId1 = client.createAccount(new AccountPrototype("RU"));

        long fromAccountId2 = client.createAccount(new AccountPrototype("RU"));
        long toAccountId2 = client.createAccount(new AccountPrototype("RU"));

        //Create and hold plan1
        Posting posting11 = new Posting(1, fromAccountId1, toAccountId1, 10, "RU", "Desc");
        Posting posting12 = new Posting(2, fromAccountId2, fromAccountId1, 25, "RU", "Desc");
        String planId1 = planId+"_1";
        PostingPlan plan1 = new PostingPlan(planId1, Arrays.asList(posting11, posting12));
        PostingPlanLog planLog1 = client.hold(plan1);
        assertEquals(plan1, planLog1.getPlan());
        assertEquals(3, planLog1.getAffectedAccountsSize());

        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(-10, planLog1.getAffectedAccounts().get(fromAccountId1).getAvailableAmount());

        assertEquals(0, planLog1.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(0, planLog1.getAffectedAccounts().get(toAccountId1).getAvailableAmount());

        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-25, planLog1.getAffectedAccounts().get(fromAccountId2).getAvailableAmount());

        //Create and hold plan2
        Posting posting21 = new Posting(1, fromAccountId1, toAccountId1, 7, "RU", "Desc");
        Posting posting22 = new Posting(2, fromAccountId2, fromAccountId1, 18, "RU", "Desc");
        String planId2 = planId+"_2";
        PostingPlan plan2 = new PostingPlan(planId2, Arrays.asList(posting21, posting22));

        PostingPlanLog planLog2 = client.hold(plan2);

        assertEquals(plan2, planLog2.getPlan());
        assertEquals(3, planLog2.getAffectedAccountsSize());

        assertEquals(0, planLog2.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(-17, planLog2.getAffectedAccounts().get(fromAccountId1).getAvailableAmount());

        assertEquals(0, planLog2.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(0, planLog2.getAffectedAccounts().get(toAccountId1).getAvailableAmount());

        assertEquals(0, planLog2.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-43, planLog2.getAffectedAccounts().get(fromAccountId2).getAvailableAmount());

        //Commit plan2
        planLog2 = client.commitPlan(plan2);

        assertEquals(plan2, planLog2.getPlan());
        assertEquals(3, planLog2.getAffectedAccountsSize());

        assertEquals(11, planLog2.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(1, planLog2.getAffectedAccounts().get(fromAccountId1).getAvailableAmount());

        assertEquals(7, planLog2.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(7, planLog2.getAffectedAccounts().get(toAccountId1).getAvailableAmount());

        assertEquals(-18, planLog2.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-43, planLog2.getAffectedAccounts().get(fromAccountId2).getAvailableAmount());

        //Create and hold plan3
        Posting posting31 = new Posting(1, fromAccountId1, toAccountId1, 70, "RU", "Desc");
        Posting posting32 = new Posting(2, fromAccountId2, toAccountId2, 180, "RU", "Desc");
        String planId3 = planId+"_3";
        PostingPlan plan3 = new PostingPlan(planId3, Arrays.asList(posting31, posting32));

        PostingPlanLog planLog3 = client.hold(plan3);

        //Rollback plan3
        planLog3 = client.rollbackPlan(plan3);

        assertEquals(plan3, planLog3.getPlan());
        assertEquals(4, planLog3.getAffectedAccountsSize());

        assertEquals(11, planLog3.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(1, planLog3.getAffectedAccounts().get(fromAccountId1).getAvailableAmount());

        assertEquals(7, planLog3.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(7, planLog3.getAffectedAccounts().get(toAccountId1).getAvailableAmount());

        assertEquals(-18, planLog3.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-43, planLog3.getAffectedAccounts().get(fromAccountId2).getAvailableAmount());

        assertEquals(0, planLog3.getAffectedAccounts().get(toAccountId2).getOwnAmount());
        assertEquals(0, planLog3.getAffectedAccounts().get(toAccountId2).getAvailableAmount());

        //Test that duplicate hold for plan1 returns same data
        planLog1 = client.hold(plan1);
        assertEquals(plan1, planLog1.getPlan());
        assertEquals(3, planLog1.getAffectedAccountsSize());

        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(-10, planLog1.getAffectedAccounts().get(fromAccountId1).getAvailableAmount());

        assertEquals(0, planLog1.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(0, planLog1.getAffectedAccounts().get(toAccountId1).getAvailableAmount());

        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-25, planLog1.getAffectedAccounts().get(fromAccountId2).getAvailableAmount());

        //Created and rollback plan3 before plan1 committed

        //Commit plan1
        planLog1 = client.commitPlan(plan1);

        assertEquals(plan1, planLog1.getPlan());
        assertEquals(3, planLog1.getAffectedAccountsSize());

        assertEquals(26, planLog1.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(26, planLog1.getAffectedAccounts().get(fromAccountId1).getAvailableAmount());

        assertEquals(17, planLog1.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(17, planLog1.getAffectedAccounts().get(toAccountId1).getAvailableAmount());

        assertEquals(-43, planLog1.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-43, planLog1.getAffectedAccounts().get(fromAccountId2).getAvailableAmount());
    }


    public static AccounterSrv.Iface createClient(String url) {
        try {
            THSpawnClientBuilder clientBuilder = new THSpawnClientBuilder().withAddress(new URI(url));
            return clientBuilder.build(AccounterSrv.Iface.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Matcher genMatcher(String... msgPatterns) {
        return matchesPattern(generateMessage(convertToPattern(msgPatterns)));
    }

    public static String convertToPattern(String formatString) {

        return escapeRegex(formatString).replaceAll("%d", "\\\\d+").replaceAll("%s", "\\\\w+");
    }

    public static Collection<String> convertToPattern(String... formatStrings) {
        return Stream.of(formatStrings).map(str -> convertToPattern(str)).collect(Collectors.toList());
    }

    public static String escapeRegex(String str) {
        return str.replaceAll("[\\<\\(\\[\\{\\\\\\^\\-\\=\\$\\!\\|\\]\\}\\)‌​\\?\\*\\+\\.\\>]", "\\\\$0");
    }

}
