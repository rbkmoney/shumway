package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.shumaich.*;
import com.rbkmoney.shumway.AbstractIntegrationTest;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.domain.StatefulAccount;
import org.apache.thrift.TException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class ShumaichServiceHandlerTest extends AbstractIntegrationTest {

    @MockBean
    @Qualifier("shumaichClient")
    private AccounterSrv.Iface shumaichClient;

    @MockBean
    private ShumpuneServiceHandler shumpuneServiceHandler;

    @Autowired
    private ShumaichServiceHandler shumaichServiceHandler;

    @Autowired
    private AccountDao accountDao;

    @Test
    public void createAccountOnMultipleHoldAtTheSameTime() throws TException, InterruptedException {
        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        final PostingPlanChange postingPlanChange = buildPostChangingPlan();
        Runnable holdTask = () -> {
            try {
                shumaichServiceHandler.hold(postingPlanChange, Mockito.mock(Clock.class));
            } catch (TException e) {
                e.printStackTrace();
            }
        };
        for (int i = 0; i < 10; i++) {
            executorService.submit(holdTask);
        }
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        final List<Long> accountIds = postingPlanChange.getBatch().getPostings().stream()
                .flatMapToLong(posting -> LongStream.of(posting.getFromAccount().getId(), posting.getToAccount().getId()))
                .distinct()
                .boxed()
                .collect(Collectors.toList());

        final List<com.rbkmoney.shumway.domain.Account> accounts = accountDao.get(accountIds);
        final Map<Long, StatefulAccount> stateful = accountDao.getStateful(accountIds);
        Assert.assertEquals(postingPlanChange.getBatch().getPostings().size(), accountIds.size());
        for (com.rbkmoney.shumway.domain.Account account : accounts) {
            Assert.assertTrue(accounts.stream().anyMatch(acc -> acc.getId() == account.getId()));
        }
    }

    private PostingPlanChange buildPostChangingPlan() {
        return new PostingPlanChange("plan",
                new PostingBatch(1L,
                        List.of(new Posting(buildAccount(100), buildAccount(200), 100, "RUB", null),
                                new Posting(buildAccount(200), buildAccount(300), 100, "RUB", null),
                                new Posting(buildAccount(300), buildAccount(100), 100, "RUB", null))));
    }

    private com.rbkmoney.damsel.shumaich.Account buildAccount(int id) {
        return new com.rbkmoney.damsel.shumaich.Account(id, "RUB");
    }

}
