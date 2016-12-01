package com.rbkmoney.shumway.dao;

import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.AccountLog;
import com.rbkmoney.shumway.domain.AccountState;

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
    List<Account> get(Collection<Long> ids) throws DaoException;
    List<Account> getExclusive(Collection<Long> ids) throws DaoException;
    List<Account> getShared(Collection<Long> ids) throws DaoException;
    AccountState getAccountState(long accountId) throws DaoException;
    Map<Long, AccountState> getAccountStates(List<Long> accountIds) throws DaoException;
    Map<Long, AccountState> getAccountStatesUpTo(List<Long> accountIds, long planId) throws DaoException;
    Map<Long, AccountState> getAccountStatesUpTo(List<Long> accountIds, long planId, long batchId) throws DaoException;
}
