package com.rbkmoney.shumway.dao;

import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.AccountLog;
import com.rbkmoney.shumway.domain.AmountState;

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
    AmountState getAmountState(long accountId) throws DaoException;
    AmountState getAmountStateUpTo(long accountId, String planId) throws DaoException;
    Map<Long, AmountState> getAmountStates(List<Long> accountIds) throws DaoException;
    Map<Long, AmountState> getAmountStatesUpTo(List<Long> accountIds, String planId) throws DaoException;
}
