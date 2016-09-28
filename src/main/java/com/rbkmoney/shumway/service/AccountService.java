package com.rbkmoney.shumway.service;

import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.domain.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class AccountService {
    private AccountDao accountDao;

    public AccountService(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    public long createAccount(Account prototype) {
        return accountDao.add(prototype);
    }

    public Account getAccount(long id) {
        return accountDao.get(id);
    }

    public Map<Long, Account> getAccounts(Collection<Long> ids) {
        return ids.stream().collect(Collectors.toMap(id -> id, id -> getAccount(id)));
    }

    public StatefulAccount getStatefulAccount(long id) {
        Account account = getAccount(id);
        if (account == null) {
            return null;
        }
        AmountState amountState = accountDao.getAmountState(id);
        return new StatefulAccount(account, amountState);
    }

    public Map<Long, StatefulAccount> getStatefulAccountsUpTo(List<Account> srcAccounts, String planId) {
        Map<Long, AmountState> amountStates = accountDao.getAmountStatesUpTo(srcAccounts.stream().map(account -> account.getId()).collect(Collectors.toList()), planId);
        return srcAccounts.stream().collect(Collectors.toMap(account -> account.getId(), account ->  new StatefulAccount(account, amountStates.get(account.getId()))));
    }

    /**
     * @return  List with stateful accounts. If no state was found for account, if'll be set to null
     * */
    public Map<Long, StatefulAccount> getStatefulAccounts(List<Account> srcAccounts) {
        Map<Long, AmountState> amountStates = accountDao.getAmountStates(srcAccounts.stream().map(account -> account.getId()).collect(Collectors.toList()));
        return srcAccounts.stream().collect(Collectors.toMap(account -> account.getId(), account ->  new StatefulAccount(account, amountStates.get(account.getId()))));
    }

    public void addAccountLogs(List<PostingLog> postingLogs) {
       List<AccountLog> accountLogs = new ArrayList<>(postingLogs.size() * 2);
       for (PostingLog postingLog: postingLogs) {
           accountLogs.add(new AccountLog(0, postingLog.getRequestId(), postingLog.getPostingId(), postingLog.getPlanId(), postingLog.getCreationTime(), postingLog.getFromAccountId(), postingLog.getOperation(), -postingLog.getAmount()));
           accountLogs.add(new AccountLog(0, postingLog.getRequestId(), postingLog.getPostingId(), postingLog.getPlanId(), postingLog.getCreationTime(), postingLog.getToAccountId(), postingLog.getOperation(), postingLog.getAmount()));
       }
       accountDao.addLogs(accountLogs);
    }

}