package com.rbkmoney.shumway.service;

import com.rbkmoney.damsel.accounter.PostingBatch;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.domain.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
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

    public Map<Long, StatefulAccount> getStatefulAccountsUpTo(Collection<Account> srcAccounts, String planId) {
        return getStatefulAccounts(srcAccounts, () -> accountDao.getAccountStatesUpTo(srcAccounts.stream().map(account -> account.getId()).collect(Collectors.toList()), planId));
    }

    public Map<Long, StatefulAccount> getStatefulAccountsUpTo(Collection<Account> srcAccounts, String planId, long batchId) {
        return getStatefulAccounts(srcAccounts, () -> accountDao.getAccountStatesUpTo(srcAccounts.stream().map(account -> account.getId()).collect(Collectors.toList()), planId, batchId));
    }

    private Map<Long, StatefulAccount> getStatefulAccounts(Collection<Account> srcAccounts, Supplier<Map<Long, AccountState>> valsSupplier) {
        Map<Long, AccountState> accountStates = valsSupplier.get();
        return srcAccounts.stream().collect(Collectors.toMap(account -> account.getId(), account -> new StatefulAccount(account, accountStates.get(account.getId()))));
    }

    /**
     * @return List with account states. If no state was found for account, if'll be set to initial value
     */
    public Map<Long, AccountState> getAccountStates(Collection<Long> srcAccountIds) {
        return accountDao.getAccountStates(srcAccountIds);
    }

    public void holdAccounts(String ppId, PostingBatch pb, List<PostingLog> newPostingLogs, List<PostingLog> savedPostingLogs, Map<Long, AccountState> accStates) {
        final List<AccountLog> accountLogs = new ArrayList<>();

        long ownAmountDiff = 0;
        long negDiff;
        long posDiff;

        final Map<Long, Long> newDiffsMap = computeDiffs(newPostingLogs);
        final Map<Long, Long> savedDiffsMap = computeDiffs(savedPostingLogs);
        final Map<Long, Long> mergedDiffsMap = mergeDiffs(newDiffsMap, savedDiffsMap);

        for (Long accId : newDiffsMap.keySet()) {
            boolean firstHoldForThisAcc = !savedDiffsMap.containsKey(accId);
            final long newDiff = newDiffsMap.get(accId);

            if (firstHoldForThisAcc) {
                negDiff = newDiff < 0 ? newDiff : 0;
                posDiff = newDiff > 0 ? newDiff : 0;

            } else {
                // second+ hold
                final long savedDiff = savedDiffsMap.get(accId);
                final long mergedDiff = mergedDiffsMap.get(accId);

                boolean signChanged = (savedDiff < 0 && mergedDiff > 0) || (savedDiff > 0 && mergedDiff < 0);
                if (signChanged) {
                    if (savedDiff > 0) {
                        negDiff = mergedDiff;
                        posDiff = -savedDiff;
                    } else {
                        negDiff = -savedDiff;
                        posDiff = mergedDiff;
                    }
                } else {
                    if (mergedDiff < 0) {
                        negDiff = newDiff;
                        posDiff = 0;
                    } else {
                        negDiff = 0;
                        posDiff = newDiff;
                    }
                }

            }
            AccountState accountState = accStates.get(accId);

            accountLogs.add(createAccountLog(pb.getId(), ppId, accId, PostingOperation.HOLD, accountState, ownAmountDiff, posDiff, negDiff, newDiff));
        }
        accountDao.addLogs(accountLogs);
    }

    public void commitOrRollback(PostingOperation op, String ppId, List<PostingLog> newPostingLogs, Map<Long, AccountState> accState) {
        final List<AccountLog> accountLogs = new ArrayList<>();
        final Map<Long, Long> newDiffsMap = computeDiffs(newPostingLogs);

        // has no sense for committed plan
        final long batchId = 0;

        for (Long accId : newDiffsMap.keySet()) {
            final long newDiff = newDiffsMap.get(accId);
            long negDiff = newDiff < 0 ? -newDiff : 0;
            long posDiff = newDiff > 0 ? -newDiff : 0;
            long ownAmountDiff = PostingOperation.COMMIT.equals(op) ? newDiff : 0;
            AccountState accountState = accState.get(accId);

            accountLogs.add(createAccountLog(batchId, ppId, accId, op, accountState, ownAmountDiff, posDiff, negDiff, newDiff));
        }
        accountDao.addLogs(accountLogs);
    }

    private AccountLog createAccountLog(long batchId, String ppId, long accId, PostingOperation op, AccountState accountState, long ownAmountDiff, long posDiff, long negDiff, long newDiff) {
        long newOwnAmount = accountState.getOwnAmount() + ownAmountDiff;
        return new AccountLog(0, batchId, ppId, Instant.now(), accId, op,
                 newOwnAmount,
                accountState.getMaxAccumulatedDuff() + posDiff,
                accountState.getMinAccumulatedDiff() + negDiff,
                ownAmountDiff, negDiff, posDiff, newDiff < 0, false);
    }

    private Map<Long, Long> computeDiffs(Collection<PostingLog> postingLogs) {
        Map<Long, Long> accountIdToAmountDiff = new HashMap<>();

        for (PostingLog pl : postingLogs) {
            accountIdToAmountDiff.put(pl.getFromAccountId(), accountIdToAmountDiff.getOrDefault(pl.getFromAccountId(), 0L) - pl.getAmount());
            accountIdToAmountDiff.put(pl.getToAccountId(), accountIdToAmountDiff.getOrDefault(pl.getToAccountId(), 0L) + pl.getAmount());
        }

        return accountIdToAmountDiff;
    }

    private Map<Long, Long> mergeDiffs(Map<Long, Long> one, Map<Long, Long> two) {
        final Map<Long, Long> merged = new HashMap<>();

        for (long accId : one.keySet()) {
            merged.put(accId, merged.getOrDefault(accId, 0L) + one.get(accId));
        }

        for (long accId : two.keySet()) {
            merged.put(accId, merged.getOrDefault(accId, 0L) + two.get(accId));
        }

        return merged;
    }
}