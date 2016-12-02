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
        return srcAccounts.stream().collect(Collectors.toMap(account -> account.getId(), account ->  new StatefulAccount(account, accountStates.get(account.getId()))));
    }

    /**
     * @return  List with stateful accounts. If no state was found for account, if'll be set to null
     * */
    public Map<Long, StatefulAccount> getStatefulAccounts(Collection<Account> srcAccounts) {
        Map<Long, AccountState> accountStates = accountDao.getAccountStates(srcAccounts.stream().map(account -> account.getId()).collect(Collectors.toList()));
        return srcAccounts.stream().collect(Collectors.toMap(account -> account.getId(), account ->  new StatefulAccount(account, accountStates.get(account.getId()))));
    }

    public void holdAccounts(String ppId, PostingBatch pb, List<PostingLog> newPostingLogs, List<PostingLog> savedPostingLogs) {
        final List<AccountLog> accountLogs = new ArrayList<>();

        long neg; // negativeDiff
        long pos; // positiveDiff

        final Map<Long, Long> dnMap = computeDiffs(newPostingLogs);
        final Map<Long, Long> dsMap = computeDiffs(savedPostingLogs);
        final Map<Long, Long> dnsMap = mergeDiffs(dnMap, dsMap);

        for(Long accId: dnMap.keySet()){
            boolean firstHoldForThisAcc = !dsMap.containsKey(accId);
            final long dn = dnMap.get(accId);

            if(firstHoldForThisAcc){
                neg = dn < 0 ? dn : 0;
                pos = dn > 0 ? dn : 0;

            }else{
                // second+ hold
                final long ds = dsMap.get(accId);
                final long dns = dnsMap.get(accId);

                boolean signChanged = (ds < 0 && dns > 0) ||  (ds > 0 && dns < 0);
                if(signChanged){
                    if(ds > 0){
                        neg = dns;
                        pos = -ds;
                    }else{
                        neg = -ds;
                        pos = dns;
                    }
                }else{
                    if(dns < 0){
                        neg = dn;
                        pos = 0;
                    }else{
                        neg = 0;
                        pos = dn;
                    }
                }

            }
            accountLogs.add(new AccountLog(0, pb.getId(), ppId, Instant.now(), accId, PostingOperation.HOLD, 0, neg, pos, dn < 0, false));
        }
        accountDao.addLogs(accountLogs);
    }

    public void commitOrRollback(PostingOperation op, String ppId, List<PostingLog> newPostingLogs){
        final List<AccountLog> accountLogs = new ArrayList<>();
        final Map<Long, Long> dnMap = computeDiffs(newPostingLogs);

        // has no sense for committed plan
        final long batchId = 0;

        for(Long accId: dnMap.keySet()) {
            final long dn = dnMap.get(accId);
            long neg = dn < 0 ? -dn : 0;
            long pos = dn > 0 ? -dn : 0;
            long ownAmount =  PostingOperation.COMMIT.equals(op) ? dn : 0;

            accountLogs.add(new AccountLog(0, batchId, ppId, Instant.now(), accId, op, ownAmount, neg, pos, dn < 0, false));
        }
        accountDao.addLogs(accountLogs);
    }

    private Map<Long, Long> computeDiffs(Collection<PostingLog> postingLogs) {
        Map<Long, Long> accountIdToAmountDiff = new HashMap<>();

        for(PostingLog pl: postingLogs){
            accountIdToAmountDiff.put(pl.getFromAccountId(), accountIdToAmountDiff.getOrDefault(pl.getFromAccountId(),0L) - pl.getAmount());
            accountIdToAmountDiff.put(pl.getToAccountId(), accountIdToAmountDiff.getOrDefault(pl.getToAccountId(), 0L) + pl.getAmount());
        }

        return accountIdToAmountDiff;
    }

    private Map<Long, Long> mergeDiffs(Map<Long, Long> one, Map<Long, Long> two){
        final Map<Long, Long> merged = new HashMap<>();

        for(long accId: one.keySet()){
            merged.put(accId, merged.getOrDefault(accId, 0L) + one.get(accId));
        }

        for(long accId: two.keySet()){
            merged.put(accId, merged.getOrDefault(accId, 0L) + two.get(accId));
        }

        return merged;
    }
}