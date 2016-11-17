package com.rbkmoney.shumway.service;

import com.rbkmoney.damsel.accounter.Posting;
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
    private AccountDao accountDao;

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

    public Map<Long, Account> getAccountsByPosting(Collection<Posting> postings) {
        Set<Long> accountIds = postings.stream().flatMap(posting -> Stream.of(posting.getFromId(), posting.getToId())).collect(Collectors.toSet());//optimize if necessary
        return getAccountsById(accountIds);
    }

    public Map<Long, Account> getAccountsById(Collection<Long> ids) {
        return accountDao.get(ids).stream().collect(Collectors.toMap(account -> account.getId(), Function.identity()));
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
           accountLogs.add(new AccountLog(0, postingLog.getRequestId(), postingLog.getPostingId(), postingLog.getPlanId(), postingLog.getCreationTime(), postingLog.getFromAccountId(), postingLog.getOperation(), getAmount(postingLog, true), getOwnAmount(postingLog, true), getAvailableAmount(postingLog, true), true));
           accountLogs.add(new AccountLog(0, postingLog.getRequestId(), postingLog.getPostingId(), postingLog.getPlanId(), postingLog.getCreationTime(), postingLog.getToAccountId(), postingLog.getOperation(), getAmount(postingLog, false), getOwnAmount(postingLog, false), getAvailableAmount(postingLog, false), false));
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

    private long getAvailableAmount(PostingLog postingLog, boolean isCredit) {
        switch (postingLog.getOperation()) {
            case HOLD:
                return isCredit ? -postingLog.getAmount() : 0;
            case COMMIT:
                return !isCredit ? getAmount(postingLog, isCredit) : 0;
            case ROLLBACK:
                return isCredit ? postingLog.getAmount() : 0;
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