package com.rbkmoney.shumway.dao;

import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.AccountLog;
import com.rbkmoney.shumway.domain.AccountState;
import com.rbkmoney.shumway.domain.StatefulAccount;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public interface AccountDao {
    long add(Account prototype) throws DaoException;
    void addLogs(List<AccountLog> accountLogs) throws DaoException;
    Account get(long id) throws DaoException;
    Map<Long, StatefulAccount> getStatefulUpTo(Collection<Long> ids, String planId, long batchId) throws DaoException;
    Map<Long, StatefulAccount> getStateful(Collection<Long> ids) throws DaoException;
    Map<Long, StatefulAccount> getStatefulExclusive(Collection<Long> ids) throws DaoException;
    List<Account> get(Collection<Long> ids) throws DaoException;
    Map<Long, AccountState> getAccountStates(Collection<Long> accountIds) throws DaoException;

    void create(Account prototype) throws DaoException;
}
