package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.StatefulAccount;
import com.rbkmoney.shumway.service.AccountService;
import com.rbkmoney.shumway.service.PostingPlanService;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class AccounterHandler implements AccounterSrv.Iface {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final TransactionTemplate transactionTemplate;//<<final required

    private AccountService accountService;
    private PostingPlanService planService;

    public AccounterHandler(AccountService accountService, PostingPlanService planService, TransactionTemplate transactionTemplate) {
        this.accountService = accountService;
        this.planService = planService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public PostingPlanLog hold(PostingPlan postingPlan) throws InvalidPostingParams, InvalidRequest, TException {
        AtomicReference<TException> errHolder = new AtomicReference<>();
        try {
            return transactionTemplate.execute(transactionStatus -> safeHold(postingPlan, errHolder));
        } catch (TransactionException e) {
            log.error("Request processing error: ", e);
            throw e;
        } catch (Exception e) {
            if (errHolder.get() != null) {
                throw errHolder.get();
            } else {
                log.error("Request processing error: ", e);
                throw e;
            }
        }
    }

    @Override
    public PostingPlanLog commitPlan(PostingPlan postingPlan) throws InvalidPostingParams, InvalidRequest, TException {
        AtomicReference<TException> errHolder = new AtomicReference<>();
        try {
            return transactionTemplate.execute(transactionStatus -> safeFinalizePlan(postingPlan, "CommitPlan", PostingOperation.COMMIT, errHolder));
        } catch (TransactionException e) {
            log.error("Request processing error: ", e);
            throw e;
        } catch (Exception e) {
            if (errHolder.get() != null) {
                throw errHolder.get();
            } else {
                log.error("Request processing error: ", e);
                throw e;
            }
        }
    }

    @Override
    public PostingPlanLog rollbackPlan(PostingPlan postingPlan) throws InvalidPostingParams, InvalidRequest, TException {
        AtomicReference<TException> errHolder = new AtomicReference<>();
        try {
            return transactionTemplate.execute(transactionStatus -> safeFinalizePlan(postingPlan, "RollbackPlan", PostingOperation.ROLLBACK, errHolder));
        } catch (TransactionException e) {
            log.error("Request processing error: ", e);
            throw e;
        } catch (Exception e) {
            if (errHolder.get() != null) {
                throw errHolder.get();
            } else {
                log.error("Request processing error: ", e);
                throw e;
            }
        }
    }

    private PostingPlanLog safeHold(PostingPlan postingPlan, AtomicReference<TException> errHolder) {
        try {
            log.info("New Hold request, received: {}", postingPlan);
            com.rbkmoney.shumway.domain.PostingPlanLog receivedDomainPlanLog = ProtocolConverter.convertToDomainPlan(postingPlan, PostingOperation.HOLD);
            com.rbkmoney.shumway.domain.PostingPlanLog currDomainPlanLog = planService.createOrUpdatePostingPlan(receivedDomainPlanLog);
            if (currDomainPlanLog == null) {
                log.warn("Posting plan log update is not performed, trying to get plan state");
                com.rbkmoney.shumway.domain.PostingPlanLog savedDomainPlanLog = planService.getSharedPostingPlan(postingPlan.getId());
                if (savedDomainPlanLog == null) {
                    log.error("Failed to create new posting plan and no matching plan was saved in db. This is inconsistency problem that might be fatal");
                    throw new TException("Failed to create or update plan [cannot be resolved automaitcally]");
                } else {
                    log.warn("Unable to change posting plan state: {} to new state: {}, [overridable: {}]", savedDomainPlanLog, receivedDomainPlanLog, planService.isOverridable(savedDomainPlanLog.getLastOperation(), receivedDomainPlanLog.getLastOperation()));
                    throw new InvalidRequest(Arrays.asList("Unable to change plan state"));
                }
            } else {
                List<PostingLog> domainPostingLogs = planService.getPostingLogs(currDomainPlanLog.getPlanId(), currDomainPlanLog.getLastOperation());
                List<PostingLog> newDomainPostingLogs = compareWithExistingPostings(postingPlan.getBatch(), domainPostingLogs, currDomainPlanLog);
                List<com.rbkmoney.shumway.domain.Account> accounts = getAndValidateAccounts(newDomainPostingLogs);
                log.debug("New posting logs: {}", newDomainPostingLogs);
                if (newDomainPostingLogs.isEmpty()) {
                    log.info("This is duplicate or empty request");
                } else {
                    log.info("Adding posting logs");
                    planService.addPostingLogs(newDomainPostingLogs);
                    log.info("Adding account logs");
                    accountService.addAccountLogs(newDomainPostingLogs);
                }
                Map<Long, Account> affectedAccounts = accountService.getStatefulAccountsUpTo(accounts, currDomainPlanLog.getPlanId()).values()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        domainStAccount -> domainStAccount.getId(),
                                        domainStAccount -> ProtocolConverter.convertFromDomainAccount(domainStAccount)
                                )
                        );
                PostingPlanLog protocolPlanLog = new PostingPlanLog(postingPlan);
                protocolPlanLog.setAffectedAccounts(affectedAccounts);
                log.info("Response: {}", protocolPlanLog);
                return protocolPlanLog;
            }
        } catch (TException e) {
            errHolder.set(e);
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            log.error("Error during performing Hold request", e);
            errHolder.set(new TException(e));
            throw e;
        }
    }

    private PostingPlanLog safeFinalizePlan(PostingPlan postingPlan, String methodType, PostingOperation operation, AtomicReference<TException> errHolder) {
        try {
            log.info("New {} request, received: {}", methodType, postingPlan);
            com.rbkmoney.shumway.domain.PostingPlanLog receivedDomainPlanLog = ProtocolConverter.convertToDomainPlan(postingPlan, operation);
            com.rbkmoney.shumway.domain.PostingPlanLog currDomainPlanLog = planService.updatePostingPlan(receivedDomainPlanLog, operation);
            if (currDomainPlanLog == null) {
                log.warn("Posting plan log update is not performed, trying to get plan state");
                com.rbkmoney.shumway.domain.PostingPlanLog savedDomainPlanLog = planService.getSharedPostingPlan(postingPlan.getId());
                if (savedDomainPlanLog == null) {
                    log.warn("Failed to update posting plan, no matching plan was saved in db. This plan is probably not created");
                    throw new InvalidRequest(Arrays.asList("Posting plan not found"));
                } else {
                    log.warn("Unable to change posting plan state: {} to new state: {}, [overridable: {}]", savedDomainPlanLog, receivedDomainPlanLog, planService.isOverridable(savedDomainPlanLog.getLastOperation(), receivedDomainPlanLog.getLastOperation()));
                    throw new InvalidRequest(Arrays.asList("Unable to change plan state"));
                }
            } else {
                List<PostingLog> domainPostingLogs = planService.getPostingLogs(currDomainPlanLog.getPlanId(), currDomainPlanLog.getLastOperation());

                List<PostingLog> newLogs = compareFinalWithExistingPostings(postingPlan.getBatch(), domainPostingLogs, currDomainPlanLog);
                if (newLogs.isEmpty()) {
                    log.info("This is duplicate or empty request");
                } else {
                    log.debug("New posting logs: {}", newLogs);

                    log.info("Adding posting logs");
                    planService.addPostingLogs(newLogs);
                    log.info("Adding account logs");
                    accountService.addAccountLogs(newLogs);
                }

                List<com.rbkmoney.shumway.domain.Account> accounts = getAndValidateAccounts(newLogs);

                Map<Long, Account> affectedAccounts = accountService.getStatefulAccountsUpTo(accounts, currDomainPlanLog.getPlanId()).values()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        domainStAccount -> domainStAccount.getId(),
                                        domainStAccount -> ProtocolConverter.convertFromDomainAccount(domainStAccount)
                                )
                        );
                PostingPlanLog protocolPlanLog = new PostingPlanLog(postingPlan);
                protocolPlanLog.setAffectedAccounts(affectedAccounts);
                log.info("Response: {}", protocolPlanLog);
                return protocolPlanLog;
            }
        } catch (TException e) {
            errHolder.set(e);
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            errHolder.set(new TException(e));
            throw e;
        }
    }

    @Override
    public PostingPlan getPlan(String planId) throws PlanNotFound, InvalidRequest, TException {
        log.info("New GetPlan request, received: {}", planId);

        com.rbkmoney.shumway.domain.PostingPlanLog domainPostingPlan;
        try {
            domainPostingPlan = planService.getSharedPostingPlan(planId);
        } catch (Exception e) {
            log.error("Failed to get posting plan log", e);
            throw new TException(e);
        }
        if (domainPostingPlan == null) {
            log.warn("Not found plan with id: {}", planId);
            throw new PlanNotFound(planId);
        }
        List<PostingLog> domainPostingLogs;
        try {
            domainPostingLogs = planService.getPostingLogs(planId, PostingOperation.HOLD);
        } catch (Exception e) {
            log.error("Failed to get posting logs", e);
            throw new TException(e);
        }
        List<Posting> protocolPostingList = domainPostingLogs.stream().map(ProtocolConverter::convertFromDomainToPosting).collect(Collectors.toList());
        PostingPlan protocolPlan = new PostingPlan(planId, protocolPostingList);
        log.info("Response: {}", protocolPlan);
        return protocolPlan;
    }

    @Override
    public long createAccount(AccountPrototype accountPrototype) throws InvalidRequest, TException {
        log.info("New CreateAccount request, received: {}", accountPrototype);
        com.rbkmoney.shumway.domain.Account domainPrototype = ProtocolConverter.convertToDomainAccount(accountPrototype);
        long response;
        try {
            response = accountService.createAccount(domainPrototype);
        } catch (Exception e) {
            log.error("Failed to create account", e);
            throw new TException(e);
        }
        log.info("Response: {}", response);
        return response;
    }

    @Override
    public Account getAccountByID(long id) throws AccountNotFound, TException {
        log.info("New GetAccountById request, received: {}", id);
        StatefulAccount domainAccount;
        try {
            domainAccount = accountService.getStatefulAccount(id);
        } catch (Exception e) {
            log.error("Failed to get account", e);
            throw new TException(e);
        }
        if (domainAccount == null) {
            log.warn("Not found account with id: {}", id);
            throw new AccountNotFound(id);
        }
        Account response = ProtocolConverter.convertFromDomainAccount(domainAccount);
        log.info("Response: {}", response);
        return response;
    }

    protected List<PostingLog> compareWithExistingPostings(List<Posting> newProtocolPostings, List<PostingLog> savedDomainPostingLogs, com.rbkmoney.shumway.domain.PostingPlanLog currentDomainPlanLog) throws TException {
        //TODO implement this correctly (check that postings're equal, new or missing postings're allowed for hold but not allowed for commit or rollback
        Set<Long> savedPostingIds = savedDomainPostingLogs.stream().map(postingLog -> postingLog.getPostingId()).collect(Collectors.toSet());
        List<Posting> filteredNewPostings = newProtocolPostings.stream().filter(posting -> true).collect(Collectors.toList());
        for (Posting posting : newProtocolPostings) {

        }
        return newProtocolPostings.stream().map(posting -> ProtocolConverter.convertToDomainPosting(posting, currentDomainPlanLog)).collect(Collectors.toList());
    }


    /**
     * if state duplicate and all postings match -> must return empty list
     * if state duplicate and some postings mismatch -> throw InvalidPosingParams if mismatch found
     * if new state and postings match (saved and referred)-> return postings with final operation
     * if new state and postings mismatch (saved and referred)-> return postings with final operation
     *
     * @return list of postings that must be saved
     */
    protected List<PostingLog> compareFinalWithExistingPostings(List<Posting> newProtocolPostings, List<PostingLog> savedDomainPostingLogs, com.rbkmoney.shumway.domain.PostingPlanLog currentDomainPlanLog) throws TException {
        //TODO implement this correctly (check that postings're equal, new or missing postings're allowed for hold but not allowed for commit or rollback
        //TODO >>mush check for posting subset
        Set<Long> savedPostingIds = savedDomainPostingLogs.stream().map(postingLog -> postingLog.getPostingId()).collect(Collectors.toSet());
        List<Posting> filteredNewPostings = newProtocolPostings.stream().filter(posting -> true).collect(Collectors.toList());
        for (Posting posting : newProtocolPostings) {

        }
        return newProtocolPostings.stream().map(posting -> ProtocolConverter.convertToDomainPosting(posting, currentDomainPlanLog)).collect(Collectors.toList());
    }

    protected List<com.rbkmoney.shumway.domain.Account> getAndValidateAccounts(List<PostingLog> newDomainPostingLogs) throws TException {
        //TODO rewrite this
        Set<com.rbkmoney.shumway.domain.Account> accounts = new HashSet<>();
        Map<Posting, String> errors = new HashMap<>();
        for (PostingLog postingLog : newDomainPostingLogs) {
            Posting posting = ProtocolConverter.convertFromDomainToPosting(postingLog);
            if (postingLog.getFromAccountId() == postingLog.getToAccountId()) {
                errors.putIfAbsent(posting, "Source and target accounts cannot be the same");
            }
            if (postingLog.getAmount() < 0) {
                errors.putIfAbsent(posting, "Amount cannot be negative");//errors cant be rewritten? yeah, it is.
            }
            com.rbkmoney.shumway.domain.Account fromAccount = accountService.getAccount(postingLog.getFromAccountId());
            com.rbkmoney.shumway.domain.Account toAccount = accountService.getAccount(postingLog.getToAccountId());
            if (fromAccount == null) {
                errors.putIfAbsent(posting, "Source account not found");
            } else {
                if (!fromAccount.getCurrSymCode().equals(postingLog.getCurrSymCode())) {
                    errors.putIfAbsent(posting, "Referred currency code is not equal to account code: " + fromAccount.getCurrSymCode());
                }
                accounts.add(fromAccount);
            }
            if (toAccount == null) {
                errors.putIfAbsent(posting, "Target account not found");
            } else {
                if (!toAccount.getCurrSymCode().equals(postingLog.getCurrSymCode())) {
                    errors.putIfAbsent(posting, "Referred currency code is not equal to account code: " + toAccount.getCurrSymCode());
                }
                accounts.add(toAccount);
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidPostingParams(errors);
        }
        return accounts.stream().collect(Collectors.toList());
    }
}
