package com.rbkmoney.shumway;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import org.apache.thrift.TException;
import org.assertj.core.util.Lists;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rbkmoney.shumway.handler.AccounterValidator.*;
import static java.util.Arrays.asList;
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
        assertEquals(0, sentAccount.getMaxAvailableAmount());
        assertEquals(0, sentAccount.getMinAvailableAmount());
        assertEquals(0, sentAccount.getOwnAmount());
        assertEquals(prototype.getCurrencySymCode(), sentAccount.getCurrencySymCode());
        assertEquals(prototype.getDescription(), sentAccount.getDescription());
    }
    @Test
    public void testGetNotExistingAccount() throws TException {
        try {
            client.getAccountByID(Long.MAX_VALUE);
            fail();
        } catch (AccountNotFound e) {
            assertEquals(Long.MAX_VALUE, e.getAccountId());
            return;
        }
    }

    @Test
    public void testGetNotExistingPlan() throws TException {
        try {
            client.getPlan(Long.MAX_VALUE + "");
            fail();
        } catch (PlanNotFound e) {
            assertEquals(Long.MAX_VALUE + "", e.getPlanId());
            return;
        }
    }

    @Test
    public void testEmptyHoldGetPlan() throws TException {
        try {
            String planId = System.currentTimeMillis() + "";
            PostingPlanChange postingPlanChange = new PostingPlanChange(planId, new PostingBatch(1, asList()));
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidRequest e) {
            assertEquals(1, e.getErrors().size());
            assertThat(e.getErrors().get(0), genMatcher(POSTING_BATCH_EMPTY));
            return;
        }
        fail();
    }

    @Test
    public void testErrHoldGetPlan() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        Posting posting = new Posting(id, id, -1, "RU", "Desc");
        PostingBatch postingBatch = new PostingBatch(1, asList(posting));
        PostingPlanChange postingPlanChange = new PostingPlanChange(planId, postingBatch);
        try {
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(SOURCE_TARGET_ACC_EQUAL_ERR, AMOUNT_NEGATIVE_ERR));
        }

        posting = new Posting(id - 1, id, -1, "RU", "Desc");
        postingBatch = new PostingBatch(1, asList(posting));
        postingPlanChange = new PostingPlanChange(planId, postingBatch);
        try {
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(AMOUNT_NEGATIVE_ERR));
        }
        posting = new Posting(id - 1, id, 1, "RU", "Desc");
        postingBatch = new PostingBatch(1, asList(posting));
        postingPlanChange = new PostingPlanChange(planId, postingBatch);
        try {
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(SRC_ACC_NOT_FOUND_ERR, DST_ACC_NOT_FOUND_ERR));
        }

        try {
            client.getPlan(planId);
            fail();
        } catch (PlanNotFound e) {
            assertEquals(planId, e.getPlanId());
        }
    }
    @Test
    public void testErrAccountHold() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        long fromAccountId = client.createAccount(new AccountPrototype("RU"));
        Posting posting = new Posting(fromAccountId, id, 1, "RU", "Desc");
        PostingPlanChange postingPlanChange = new PostingPlanChange(planId, new PostingBatch(1, asList(posting)));

        try {
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(DST_ACC_NOT_FOUND_ERR));
        }

        long toAccountId = client.createAccount(new AccountPrototype(posting.getCurrencySymCode()));
        posting = new Posting(fromAccountId, toAccountId, 1, "ERR", "Desc");
        postingPlanChange = new PostingPlanChange(planId, new PostingBatch(1, asList(posting)));
        try {
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidPostingParams e) {
            assertEquals(1, e.getWrongPostingsSize());
            assertThat(e.getWrongPostings().get(posting), genMatcher(ACC_CURR_CODE_NOT_EQUAL_ERR, ACC_CURR_CODE_NOT_EQUAL_ERR));
        }

        posting = new Posting(fromAccountId, toAccountId, 1, "RU", "Desc");
        postingPlanChange = new PostingPlanChange(planId, new PostingBatch(1, asList(posting)));
        client.hold(postingPlanChange);
    }

    @Test
    public void testHoldCommitPlan() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        long fromAccountId = client.createAccount(new AccountPrototype("RU"));
        long toAccountId = client.createAccount(new AccountPrototype("RU"));

        Posting posting = new Posting(fromAccountId, toAccountId, 1, "RU", "Desc");
        PostingBatch postingBatch = new PostingBatch(1, asList(posting));
        PostingPlanChange postingPlanChange = new PostingPlanChange(planId, postingBatch);
        PostingPlanLog planLog = client.hold(postingPlanChange);

        assertEquals(2, planLog.getAffectedAccountsSize());
        assertEquals("Src Max available hope on credit rollback", 0, planLog.getAffectedAccounts().get(fromAccountId).getMaxAvailableAmount());
        assertEquals("Src Min available hope on credit commit", -posting.getAmount(), planLog.getAffectedAccounts().get(fromAccountId).getMinAvailableAmount());
        assertEquals("Debit doesn't include hold for src own amount ", 0, planLog.getAffectedAccounts().get(fromAccountId).getOwnAmount());
        assertEquals("Dst Max available hope on debit commit", posting.getAmount(), planLog.getAffectedAccounts().get(toAccountId).getMaxAvailableAmount());
        assertEquals("Dst Min available hope on debit rollback", 0, planLog.getAffectedAccounts().get(toAccountId).getMinAvailableAmount());
        assertEquals("Credit doesn't include hold for dst own amount", 0, planLog.getAffectedAccounts().get(toAccountId).getOwnAmount());

        assertEquals("Duplicate request, result must be equal", planLog, client.hold(postingPlanChange));

        Posting posting2 = new Posting(fromAccountId, toAccountId, 5, "RU", "Desc");
        postingBatch = new PostingBatch(2, asList(posting, posting2));
        PostingPlan postingPlan = new PostingPlan(planId, asList(postingBatch));

        try {
            client.commitPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(2, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting), genMatcher(SAVED_POSTING_NOT_FOUND_ERR, RECEIVED_POSTING_NOT_FOUND_ERR));
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }
        try {
            client.rollbackPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(2, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting), genMatcher(SAVED_POSTING_NOT_FOUND_ERR, RECEIVED_POSTING_NOT_FOUND_ERR));
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }

        postingBatch = new PostingBatch(1, asList(posting, posting2));
        postingPlan = new PostingPlan(planId, asList(postingBatch));

        try {
            client.commitPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }
        try {
            client.rollbackPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }


        postingPlan = new PostingPlan(planId, Lists.emptyList());
        try {
            client.commitPlan(postingPlan);
            fail();
        } catch (InvalidRequest ex) {
            assertEquals(1, ex.getErrorsSize());
            assertThat(ex.getErrors().get(0), genMatcher(POSTING_PLAN_EMPTY));
        }
        try {
            client.rollbackPlan(postingPlan);
            fail();
        } catch (InvalidRequest ex) {
            assertEquals(1, ex.getErrorsSize());
            assertThat(ex.getErrors().get(0), genMatcher(POSTING_PLAN_EMPTY));
        }

        postingBatch = new PostingBatch(1, asList(posting2));
        postingPlan = new PostingPlan(planId, asList(postingBatch));
        try {
            client.commitPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(2, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting), genMatcher(SAVED_POSTING_NOT_FOUND_ERR));
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }
        try {
            client.rollbackPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(2, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting), genMatcher(SAVED_POSTING_NOT_FOUND_ERR));
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }

        postingPlan = new PostingPlan(planId, asList(new PostingBatch(1, asList(posting)), new PostingBatch(2, asList(posting2))));

        try {
            client.commitPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }
        try {
            client.rollbackPlan(postingPlan);
            fail();
        } catch (InvalidPostingParams ex) {
            assertEquals(1, ex.getWrongPostingsSize());
            assertThat(ex.getWrongPostings().get(posting2), genMatcher(RECEIVED_POSTING_NOT_FOUND_ERR));
        }

        postingPlan = new PostingPlan(planId, asList(new PostingBatch(1, asList(posting))));
        planLog = client.commitPlan(postingPlan);
        assertEquals(2, planLog.getAffectedAccountsSize());
        assertEquals("Debit sets max available amount to own amount", -posting.getAmount(), planLog.getAffectedAccounts().get(fromAccountId).getMaxAvailableAmount());
        assertEquals("Debit sets min available amount to own amount", -posting.getAmount(), planLog.getAffectedAccounts().get(fromAccountId).getMinAvailableAmount());
        assertEquals("Debit includes commit for src own amount ", -posting.getAmount(), planLog.getAffectedAccounts().get(fromAccountId).getOwnAmount());
        assertEquals("Credit sets max available amount to own amount", posting.getAmount(), planLog.getAffectedAccounts().get(toAccountId).getMaxAvailableAmount());
        assertEquals("Credit sets max available amount to own amount", posting.getAmount(), planLog.getAffectedAccounts().get(toAccountId).getMinAvailableAmount());
        assertEquals("Credit includes commit for dst own amount", posting.getAmount(), planLog.getAffectedAccounts().get(toAccountId).getOwnAmount());

        try {
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_STATE_CHANGE_ERR));
        }

        assertEquals("Duplicate request, result must be equal", planLog, client.commitPlan(postingPlan));

        try {
            client.rollbackPlan(postingPlan);
            fail();
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

        Posting posting = new Posting(fromAccountId, toAccountId, 1, "RU", "Desc");
        PostingPlanChange postingPlanChange = new PostingPlanChange(planId, new PostingBatch(1, asList(posting)));
        PostingPlanLog planLog = client.hold(postingPlanChange);

        assertEquals("Duplicate request, result must be equal", planLog, client.hold(postingPlanChange));

        PostingPlan postingPlan = new PostingPlan(planId, asList(new PostingBatch(1, asList(posting))));
        planLog = client.rollbackPlan(postingPlan);

        try {
            client.hold(postingPlanChange);
            fail();
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_STATE_CHANGE_ERR));
        }

        assertEquals("Duplicate request, result must be equal", planLog, client.rollbackPlan(postingPlan));

        try {
            client.commitPlan(postingPlan);
            fail();
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
            fail();
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_EMPTY));
        }
        try {
           client.rollbackPlan(postingPlan);
            fail();
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_EMPTY));
        }
        PostingPlanLog planLog = new PostingPlanLog(Collections.emptyMap());

        try {
            assertEquals(planLog, client.hold(new PostingPlanChange(planId, new PostingBatch(1, asList()))));
            fail();
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_BATCH_EMPTY));
        }

        try {
            client.getPlan(planId);
            fail();
        } catch (PlanNotFound e) {
            assertEquals(planId, e.getPlanId());
        }

        Posting posting = new Posting(0, 1, 1, "RU", "Desc");
        try {
            client.commitPlan(new PostingPlan(planId, asList(new PostingBatch(1, Arrays.asList(posting)))));
            fail();
        } catch (InvalidRequest e) {
            assertThat(e.getErrors().get(0), genMatcher(POSTING_PLAN_NOT_FOUND_ERR));
        }

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
        Posting posting11 = new Posting(fromAccountId1, toAccountId1, 10, "RU", "Desc");
        Posting posting12 = new Posting(fromAccountId2, fromAccountId1, 25, "RU", "Desc");
        String planId1 = planId+"_1";
        PostingPlanChange planChange1 = new PostingPlanChange(planId1, new PostingBatch(1, asList(posting11, posting12)));
        PostingPlanLog planLog1 = client.hold(planChange1);
        assertEquals(3, planLog1.getAffectedAccountsSize());

        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(15, planLog1.getAffectedAccounts().get(fromAccountId1).getMaxAvailableAmount());
        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId1).getMinAvailableAmount());

        assertEquals(0, planLog1.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(10, planLog1.getAffectedAccounts().get(toAccountId1).getMaxAvailableAmount());
        assertEquals(0, planLog1.getAffectedAccounts().get(toAccountId1).getMinAvailableAmount());

        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(0, planLog1.getAffectedAccounts().get(fromAccountId2).getMaxAvailableAmount());
        assertEquals(-25, planLog1.getAffectedAccounts().get(fromAccountId2).getMinAvailableAmount());

        //Create and hold plan2
        Posting posting21 = new Posting(fromAccountId1, toAccountId1, 7, "RU", "Desc");
        Posting posting22 = new Posting(fromAccountId2, fromAccountId1, 18, "RU", "Desc");
        String planId2 = planId+"_2";

        PostingPlanLog planLog2 = client.hold(new PostingPlanChange(planId2, new PostingBatch(1, asList(posting21, posting22))));

        assertEquals(3, planLog2.getAffectedAccountsSize());

        assertEquals(0, planLog2.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(26, planLog2.getAffectedAccounts().get(fromAccountId1).getMaxAvailableAmount());
        assertEquals(0, planLog2.getAffectedAccounts().get(fromAccountId1).getMinAvailableAmount());

        assertEquals(0, planLog2.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(17, planLog2.getAffectedAccounts().get(toAccountId1).getMaxAvailableAmount());
        assertEquals(0, planLog2.getAffectedAccounts().get(toAccountId1).getMinAvailableAmount());

        assertEquals(0, planLog2.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(0, planLog2.getAffectedAccounts().get(fromAccountId2).getMaxAvailableAmount());
        assertEquals(-43, planLog2.getAffectedAccounts().get(fromAccountId2).getMinAvailableAmount());

        //Commit plan2
        PostingPlan plan2 = new PostingPlan(planId2, Arrays.asList(new PostingBatch(1, asList(posting21, posting22))));
        planLog2 = client.commitPlan(plan2);

        assertEquals(3, planLog2.getAffectedAccountsSize());

        assertEquals(11, planLog2.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(26, planLog2.getAffectedAccounts().get(fromAccountId1).getMaxAvailableAmount());
        assertEquals(11, planLog2.getAffectedAccounts().get(fromAccountId1).getMinAvailableAmount());

        assertEquals(7, planLog2.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(17, planLog2.getAffectedAccounts().get(toAccountId1).getMaxAvailableAmount());
        assertEquals(7, planLog2.getAffectedAccounts().get(toAccountId1).getMinAvailableAmount());

        assertEquals(-18, planLog2.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-18, planLog2.getAffectedAccounts().get(fromAccountId2).getMaxAvailableAmount());
        assertEquals(-43, planLog2.getAffectedAccounts().get(fromAccountId2).getMinAvailableAmount());

        //Create and hold plan3
        Posting posting31 = new Posting(fromAccountId1, toAccountId1, 70, "RU", "Desc");
        Posting posting32 = new Posting(fromAccountId2, toAccountId2, 180, "RU", "Desc");
        String planId3 = planId+"_3";

        client.hold(new PostingPlanChange(planId3, new PostingBatch(1, asList(posting31, posting32))));

        //Rollback plan3
        PostingPlan plan3 = new PostingPlan(planId3, asList(new PostingBatch(1, asList(posting31, posting32))));

        PostingPlanLog planLog3 = client.rollbackPlan(plan3);

        assertEquals(4, planLog3.getAffectedAccountsSize());

        assertEquals(11, planLog3.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(26, planLog3.getAffectedAccounts().get(fromAccountId1).getMaxAvailableAmount());
        assertEquals(11, planLog3.getAffectedAccounts().get(fromAccountId1).getMinAvailableAmount());

        assertEquals(7, planLog3.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(17, planLog3.getAffectedAccounts().get(toAccountId1).getMaxAvailableAmount());
        assertEquals(7, planLog3.getAffectedAccounts().get(toAccountId1).getMinAvailableAmount());

        assertEquals(-18, planLog3.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-18, planLog3.getAffectedAccounts().get(fromAccountId2).getMaxAvailableAmount());
        assertEquals(-43, planLog3.getAffectedAccounts().get(fromAccountId2).getMinAvailableAmount());

        assertEquals(0, planLog3.getAffectedAccounts().get(toAccountId2).getOwnAmount());
        assertEquals(0, planLog3.getAffectedAccounts().get(toAccountId2).getMaxAvailableAmount());
        assertEquals(0, planLog3.getAffectedAccounts().get(toAccountId2).getMinAvailableAmount());

        //Test that duplicate hold for plan1 returns same data
        assertEquals(planLog1, client.hold(planChange1));

        //Created and rollback plan3 before plan1 committed

        //Commit plan1
        planLog1 = client.commitPlan(new PostingPlan(planId1, asList(planChange1.getBatch())));

        assertEquals(3, planLog1.getAffectedAccountsSize());

        assertEquals(26, planLog1.getAffectedAccounts().get(fromAccountId1).getOwnAmount());
        assertEquals(26, planLog1.getAffectedAccounts().get(fromAccountId1).getMaxAvailableAmount());
        assertEquals(26, planLog1.getAffectedAccounts().get(fromAccountId1).getMinAvailableAmount());

        assertEquals(17, planLog1.getAffectedAccounts().get(toAccountId1).getOwnAmount());
        assertEquals(17, planLog1.getAffectedAccounts().get(toAccountId1).getMaxAvailableAmount());
        assertEquals(17, planLog1.getAffectedAccounts().get(toAccountId1).getMinAvailableAmount());

        assertEquals(-43, planLog1.getAffectedAccounts().get(fromAccountId2).getOwnAmount());
        assertEquals(-43, planLog1.getAffectedAccounts().get(fromAccountId2).getMaxAvailableAmount());
        assertEquals(-43, planLog1.getAffectedAccounts().get(fromAccountId2).getMinAvailableAmount());
    }

    @Test
    public void testRepeatableHold() throws TException {
        long id = System.currentTimeMillis();
        String planId = id + "";
        long fromAccountId1 = client.createAccount(new AccountPrototype("RU"));
        long toAccountId1 = client.createAccount(new AccountPrototype("RU"));

        Posting posting = new Posting(fromAccountId1, toAccountId1, 100, "RU", "Test");

        PostingBatch postingBatch1 = new PostingBatch(1, asList(posting));

        PostingPlanLog planLog1 = client.hold(new PostingPlanChange(planId, postingBatch1));

        PostingBatch postingBatch2 = new PostingBatch(2, asList(posting));

        PostingPlanLog planLog2 = client.hold(new PostingPlanChange(planId, postingBatch2));

        assertEquals(planLog1, client.hold(new PostingPlanChange(planId, postingBatch1)));


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
