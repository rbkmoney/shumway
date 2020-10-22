package com.rbkmoney.shumway.service;

import com.rbkmoney.damsel.shumaich.Posting;
import com.rbkmoney.damsel.shumaich.PostingBatch;
import com.rbkmoney.damsel.shumaich.PostingPlanChange;
import com.rbkmoney.shumway.AbstractIntegrationTest;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.DaoException;
import com.rbkmoney.shumway.domain.Account;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AccountTest extends AbstractIntegrationTest {

    @Autowired
    AccountDao accountDao;

    @Autowired
    AccountService accountService;

    @Test
    public void accountsCreatedWithoutOrder() {
        accountDao.createIfNotExists(new Account(10, Instant.now(), "RUB", null));
        accountDao.createIfNotExists(new Account(11, Instant.now(), "RUB", null));
        accountDao.createIfNotExists(new Account(12, Instant.now(), "RUB", null));

        assertEquals(3, accountDao.get(List.of(10L, 11L, 12L)).size());
    }

    public void accountAlreadyExisted() {
        accountDao.createIfNotExists(new Account(10, Instant.now(), "RUB", null));
        accountDao.createIfNotExists(new Account(10, Instant.now(), "RUB", null));

        assertEquals(1, accountDao.get(List.of(10L)).size());
    }

    @Test
    public void shouldIncrementOnAddAndGiveAsIsOnCreate() {
        accountDao.add(new Account(0, Instant.now(), "RUB", null));
        accountDao.add(new Account(0, Instant.now(), "RUB", null));
        accountDao.createIfNotExists(new Account(100, Instant.now(), "RUB", null));
        accountDao.createIfNotExists(new Account(101, Instant.now(), "RUB", null));
        accountDao.add(new Account(0, Instant.now(), "RUB", null));

        assertEquals(5, accountDao.get(List.of(1L, 2L, 3L, 100L, 101L)).size());

    }

    @Test
    public void createAccountOnHold() {
        accountDao.createIfNotExists(new Account(200, Instant.now(), "RUB", null));
        accountDao.createIfNotExists(new Account(300, Instant.now(), "RUB", null));
        accountService.createAccountsIfDontExist(
                new PostingPlanChange("plan",
                        new PostingBatch(1L,
                                List.of(new Posting(createAccount(100), createAccount(200), 100, "RUB", null),
                                        new Posting(createAccount(200), createAccount(300), 100, "RUB", null),
                                        new Posting(createAccount(300), createAccount(100), 100, "RUB", null)))));


        assertEquals("RUB", accountService.getStatefulAccount(100).getCurrSymCode());
        assertEquals("RUB", accountService.getStatefulAccount(200).getCurrSymCode());
        assertEquals("RUB", accountService.getStatefulAccount(300).getCurrSymCode());
    }

    private com.rbkmoney.damsel.shumaich.Account createAccount(int id) {
        return new com.rbkmoney.damsel.shumaich.Account(id, "RUB");
    }
}
