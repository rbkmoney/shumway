package com.rbkmoney.shumway.service;

import com.rbkmoney.shumway.AbstractIntegrationTest;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.domain.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest extends AbstractIntegrationTest {

    @Autowired
    AccountDao accountDao;

    @Autowired
    AccountService accountService;

    @Test
    void createAccountsIfDontExist() {
        accountDao.create(new Account(10, Instant.now(), "RUB", null));
    }
}