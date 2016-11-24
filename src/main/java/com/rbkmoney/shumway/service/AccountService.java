package com.rbkmoney.shumway.service;

import com.rbkmoney.damsel.accounter.PostingBatch;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.domain.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class AccountService {
    private final AccountDao accountDao;
    private final Function<Collection<PostingBatch>, Set<Long>> getUnicAccountIds = (batches) -> batches
            .stream()
            .flatMap(batch -> batch.getPostings().stream())
            .flatMap(posting -> Stream.of(posting.getFromId(), posting.getToId()))
            .collect(Collectors.toSet());

    public AccountService(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    public long createAccount(Account prototype) {
        return accountDao.add(prototype);
    }

    public List<Long> createAccounts(Account prototype, int numberOfAccs) {
        return accountDao.add(prototype, numberOfAccs);
    }

    public Account getAccount(long id) {
        return accountDao.get(id);
    }

    public Map<Long, Account> getExclusiveAccountsByBatchList(Collection<PostingBatch> batches) {
        return getExclusiveAccountsById(getUnicAccountIds.apply(batches));
    }

    public Map<Long, Account> getAccountsByBatchList(Collection<PostingBatch> batches) {
        return getAccountsById(getUnicAccountIds.apply(batches));
    }

    public Map<Long, Account> getExclusiveAccountsById(Collection<Long> ids) {
        return accountDao.getExclusive(ids).stream().collect(Collectors.toMap(account -> account.getId(), Function.identity()));
    }

    public Map<Long, Account> getAccountsById(Collection<Long> ids) {
        return accountDao.get(ids).stream().collect(Collectors.toMap(account -> account.getId(), Function.identity()));
    }

    public StatefulAccount getStatefulAccount(long id) {
        Account account = getAccount(id);
        if (account == null) {
            return null;
        }
        AccountState accountState = accountDao.getAccountState(id);
        return new StatefulAccount(account, accountState);
    }

    public Map<Long, StatefulAccount> getStatefulAccountsUpTo(List<Account> srcAccounts, String planId) {
        Map<Long, AccountState> accountStates = accountDao.getAccountStatesUpTo(srcAccounts.stream().map(account -> account.getId()).collect(Collectors.toList()), planId);
        return srcAccounts.stream().collect(Collectors.toMap(account -> account.getId(), account ->  new StatefulAccount(account, accountStates.get(account.getId()))));
    }

    /**
     * @return  List with stateful accounts. If no state was found for account, if'll be set to null
     * */
    public Map<Long, StatefulAccount> getStatefulAccounts(List<Account> srcAccounts) {
        Map<Long, AccountState> accountStates = accountDao.getAccountStates(srcAccounts.stream().map(account -> account.getId()).collect(Collectors.toList()));
        return srcAccounts.stream().collect(Collectors.toMap(account -> account.getId(), account ->  new StatefulAccount(account, accountStates.get(account.getId()))));
    }

    public void addAccountLogs(List<PostingLog> postingLogs) {
       List<AccountLog> accountLogs = new ArrayList<>(postingLogs.size() * 2);
       for (PostingLog postingLog: postingLogs) {
           accountLogs.add(new AccountLog(0, postingLog.getBatchId(), postingLog.getPlanId(), postingLog.getCreationTime(), postingLog.getFromAccountId(), postingLog.getOperation(), getAmount(postingLog, true), getOwnAmount(postingLog, true), getOwnAmountDelta(postingLog, true), true, false));
           accountLogs.add(new AccountLog(0, postingLog.getBatchId(), postingLog.getPlanId(), postingLog.getCreationTime(), postingLog.getToAccountId(), postingLog.getOperation(), getAmount(postingLog, false), getOwnAmount(postingLog, false), getOwnAmountDelta(postingLog, false), false, false));
       }
       accountDao.addLogs(accountLogs);
    }

    private long getOwnAmount(PostingLog postingLog, boolean isCredit) {
        switch (postingLog.getOperation()) {
            case HOLD:
                return 0;
            case COMMIT:
                return getAmount(postingLog, isCredit);
            case ROLLBACK:
                return 0;
            default:
                throw new IllegalStateException("Unknown operation:"+postingLog.getOperation());
        }
    }

    private long getOwnAmountDelta(PostingLog postingLog, boolean isCredit) {
        switch (postingLog.getOperation()) {
            case HOLD:
                return getAmount(postingLog, isCredit);
            case COMMIT:
            case ROLLBACK:
                return -getAmount(postingLog, isCredit);
            default:
                throw new IllegalStateException("Unknown operation:"+postingLog.getOperation());
        }
    }
/**
 * @param isCredit true - if it's the source in posting, false - if target (debit)
 * */
    private long getAmount(PostingLog postingLog, boolean isCredit) {
        return isCredit ? -postingLog.getAmount() : postingLog.getAmount();
    }


}