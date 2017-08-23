package com.rbkmoney.shumway.service;

import com.rbkmoney.damsel.accounter.PostingBatch;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final Logger log = LoggerFactory.getLogger(this.getClass());

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
        log.info("Get exclusive accounts for batches");
        return getExclusiveAccountsById(getUnicAccountIds.apply(batches));
    }

    public Map<Long, Account> getAccountsFromBatches(Collection<PostingBatch> batches) {
        log.info("Get accounts for batches");
        return getAccountsById(getUnicAccountIds.apply(batches));
    }

    public Map<Long, StatefulAccount> getStatefulAccounts(Collection<PostingBatch> batches, String planId, boolean finalOp) {
        long lastBatchId = finalOp ? Long.MAX_VALUE : batches.stream().mapToLong(batch -> batch.getId()).max().getAsLong();
        Collection<Long> uniqAccIds = getUnicAccountIds.apply(batches);
        log.info("Get stateful accounts: {}, plan: {}, up to batch: {}", uniqAccIds, planId, lastBatchId);
        Map<Long, StatefulAccount> result = accountDao.getStatefulUpTo(uniqAccIds, planId, lastBatchId);
        log.info("Got accounts: {}:{}", result.size(), result.values());
        return result;
    }

    public Map<Long, StatefulAccount> getStatefulExclusiveAccounts(Collection<PostingBatch> batches) {
        Collection<Long> uniqAccIds = getUnicAccountIds.apply(batches);
        log.info("Get stateful exclusive accounts by ids: {}", uniqAccIds);
        Map<Long, StatefulAccount> result = accountDao.getStatefulExclusive(uniqAccIds);
        log.info("Got exclusive accounts: {}:{}", result.size(), result.values());
        return result;
    }

    public Map<Long, AccountState> getAccountStatesFromBatches(Collection<PostingBatch> batches, String planId, boolean finalOp) {
        log.info("Get account states for batches");
        long lastBatchId = finalOp ? Long.MAX_VALUE : batches.stream().mapToLong(batch -> batch.getId()).max().getAsLong();
        return getAccountStatesUpTo(getUnicAccountIds.apply(batches), planId, lastBatchId);
    }

    public Map<Long, Account> getExclusiveAccountsById(Collection<Long> ids) {
        log.info("Get exclusive account states by ids: {}", ids);//not states here
        Map<Long, Account> result = accountDao.getExclusive(ids).stream().collect(Collectors.toMap(account -> account.getId(), Function.identity()));
        log.info("Got accounts: {}:{}", result.size(), result);
        return result;
    }

    public Map<Long, Account> getAccountsById(Collection<Long> ids) {
        log.info("Get accounts by ids: {}", ids);
        Map<Long, Account> result = accountDao.get(ids).stream().collect(Collectors.toMap(account -> account.getId(), Function.identity()));
        log.info("Got accounts: {}:{}", result.size(), result);
        return result;
    }

    public StatefulAccount getStatefulAccount(long id) {
        Account account = getAccount(id);
        if (account == null) {
            return null;
        }
        AccountState accountState = accountDao.getAccountState(id);
        return new StatefulAccount(account, accountState);
    }

    /*public Map<Long, StatefulAccount> getStatefulAccountsUpTo(Map<Long, Account> srcAccounts, String planId, long batchId) {
        return getStatefulAccounts(srcAccounts, () -> accountDao.getAccountStatesUpTo(srcAccounts.keySet().stream().collect(Collectors.toList()), planId, batchId));
    }*/

    public static Map<Long, StatefulAccount> getStatefulAccounts(Map<Long, StatefulAccount> srcAccounts, Supplier<Map<Long, AccountState>> valsSupplier) {
        Map<Long, AccountState> accountStates = valsSupplier.get();
        return srcAccounts.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> new StatefulAccount(entry.getValue(), accountStates.get(entry.getKey()))));
    }

    /**
     * @return List with account states. If no state was found for account, if'll be set to initial value
     */
    public Map<Long, AccountState> getAccountStates(Collection<Long> srcAccountIds) {
        log.info("Get account states by ids: {}", srcAccountIds);
        Map<Long, AccountState> result = accountDao.getAccountStates(srcAccountIds);
        log.info("Got states: {}:{}", result.size(), result);
        return result;
    }

    public Map<Long, AccountState> getAccountStatesUpTo(Collection<Long> srcAccountIds, String planId, long batchId) {
        log.info("Get states for accounts: {}, plan: {}, up to batch: {}", srcAccountIds, planId, batchId);
        Map<Long, AccountState> result = accountDao.getAccountStatesUpTo(srcAccountIds, planId, batchId);
        log.info("Got states: {}:{}", result.size(), result);
        return result;
    }

    public Map<Long, AccountState> holdAccounts(String ppId, PostingBatch pb, List<PostingLog> newPostingLogs, List<PostingLog> savedPostingLogs, Map<Long, StatefulAccount> statefulAccounts) {
        final List<AccountLog> accountLogs = new ArrayList<>();

        long ownAmountDiff = 0;
        long negDiff;
        long posDiff;

        final Map<Long, Long> newDiffsMap = computeDiffs(newPostingLogs);
        final Map<Long, Long> savedDiffsMap = computeDiffs(savedPostingLogs);
        final Map<Long, Long> mergedDiffsMap = mergeDiffs(newDiffsMap, savedDiffsMap);
        final Map<Long, AccountState> resultAccStates = new HashMap<>();
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
            AccountState accountState = statefulAccounts.get(accId).getAccountState();
            AccountLog accountLog = createAccountLog(pb.getId(), ppId, accId, PostingOperation.HOLD, accountState, ownAmountDiff, posDiff, negDiff, newDiff);

            accountLogs.add(accountLog);
            resultAccStates.put(accId, new AccountState(accountLog.getOwnAccumulated(), accountLog.getMinAccumulated(), accountLog.getMaxAccumulated()));
        }
        log.info("Add account hold logs: {}", accountLogs);
        accountDao.addLogs(accountLogs);
        log.info("Added hold logs: {}", accountLogs.size());
        return resultAccStates;
    }

    public Map<Long, AccountState> commitOrRollback(PostingOperation op, String ppId, List<PostingLog> newPostingLogs, Map<Long, StatefulAccount> statefulAccounts) {
        final List<AccountLog> accountLogs = new ArrayList<>();
        final Map<Long, Long> newDiffsMap = computeDiffs(newPostingLogs);
        final Map<Long, AccountState> resultAccStates = new HashMap<>();

        // has no sense for committed plan
        final long batchId = Long.MAX_VALUE;

        for (Long accId : newDiffsMap.keySet()) {
            final long newDiff = newDiffsMap.get(accId);
            long negDiff = newDiff < 0 ? -newDiff : 0;
            long posDiff = newDiff > 0 ? -newDiff : 0;
            long ownAmountDiff = PostingOperation.COMMIT.equals(op) ? newDiff : 0;
            AccountState accountState = statefulAccounts.get(accId).getAccountState();
            AccountLog accountLog = createAccountLog(batchId, ppId, accId, op, accountState, ownAmountDiff, posDiff, negDiff, newDiff);

            accountLogs.add(accountLog);
            resultAccStates.put(accId, new AccountState(accountLog.getOwnAccumulated(), accountLog.getMinAccumulated(), accountLog.getMaxAccumulated()));
        }
        log.info("Add account c/r logs: {}", accountLogs);
        accountDao.addLogs(accountLogs);
        log.info("Added c/r logs: {}", accountLogs.size());
        return resultAccStates;
    }

    private AccountLog createAccountLog(long batchId, String ppId, long accId, PostingOperation op, AccountState accountState, long ownAmountDiff, long posDiff, long negDiff, long newDiff) {
        long newOwnAmount = accountState.getOwnAmount() + ownAmountDiff;
        return new AccountLog(0, batchId, ppId, Instant.now(), accId, op,
                 newOwnAmount,
                accountState.getMaxAccumulatedDiff() + posDiff,
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