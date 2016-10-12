package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.damsel.accounter.Account;
import com.rbkmoney.damsel.accounter.PostingPlanLog;
import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.shumway.domain.*;
import com.rbkmoney.shumway.service.AccountService;
import com.rbkmoney.shumway.service.PostingPlanService;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.rbkmoney.shumway.handler.AccounterValidator.validatePlanNotFixedResult;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class AccounterHandler implements AccounterSrv.Iface {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final TransactionTemplate transactionTemplate;

    private AccountService accountService;
    private PostingPlanService planService;

    public AccounterHandler(AccountService accountService, PostingPlanService planService, TransactionTemplate transactionTemplate) {
        this.accountService = accountService;
        this.planService = planService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public PostingPlanLog hold(PostingPlan postingPlan) throws InvalidPostingParams, InvalidRequest, TException {
        return doSafeOperation(postingPlan, PostingOperation.HOLD);
    }

    @Override
    public PostingPlanLog commitPlan(PostingPlan postingPlan) throws InvalidPostingParams, InvalidRequest, TException {
        return doSafeOperation(postingPlan, PostingOperation.COMMIT);
    }

    @Override
    public PostingPlanLog rollbackPlan(PostingPlan postingPlan) throws InvalidPostingParams, InvalidRequest, TException {
        return doSafeOperation(postingPlan, PostingOperation.ROLLBACK);
    }

    protected PostingPlanLog doSafeOperation(PostingPlan postingPlan, PostingOperation operation) throws TException {
        AtomicReference<TException> errHolder = new AtomicReference<>();
        try {
            return transactionTemplate.execute(transactionStatus -> safePostingOperation(postingPlan, operation, errHolder));
        } catch (Exception e) {
            log.error("Request processing error: ", e);
            if (e instanceof TransactionException) {
                throw e;
            } else if (errHolder.get() != null) {
                //notice that up level error is overlapped here
                throw errHolder.get();
            } else {
                throw e;
            }
        }
    }

    private PostingPlanLog safePostingOperation(PostingPlan postingPlan, PostingOperation operation, AtomicReference<TException> errHolder) {
        boolean finalOp = isFinalOperation(operation);
        try {
            log.info("New {} request, received: {}", operation, postingPlan);
            AccounterValidator.validateStatic(postingPlan);
            com.rbkmoney.shumway.domain.PostingPlanLog receivedDomainPlanLog = ProtocolConverter.convertToDomainPlan(postingPlan, operation);

            Pair<com.rbkmoney.shumway.domain.PostingPlanLog, com.rbkmoney.shumway.domain.PostingPlanLog> postingPlanLogPair = finalOp ?
                    planService.updatePostingPlan(receivedDomainPlanLog, operation) :
                    planService.createOrUpdatePostingPlan(receivedDomainPlanLog);
            com.rbkmoney.shumway.domain.PostingPlanLog oldDomainPlanLog = postingPlanLogPair.getKey();
            com.rbkmoney.shumway.domain.PostingPlanLog currDomainPlanLog = postingPlanLogPair.getValue();

            PostingOperation prevOperation = oldDomainPlanLog == null ? PostingOperation.HOLD : oldDomainPlanLog.getLastOperation();
            if (currDomainPlanLog == null) {
                throw validatePlanNotFixedResult(receivedDomainPlanLog, oldDomainPlanLog, !finalOp);
            } else {
                List<PostingLog> savedDomainPostingLogs = planService.getPostingLogs(currDomainPlanLog.getPlanId(), prevOperation);

                List<Posting> notSavedProtocolPostings = AccounterValidator.validatePostings(postingPlan, savedDomainPostingLogs, !finalOp);

                Map<Long, com.rbkmoney.shumway.domain.Account> accountMap = accountService.getAccountsByPosting(postingPlan.getBatch());
                AccounterValidator.validateAccounts(notSavedProtocolPostings, accountMap);

                log.debug("Saving posting logs: {}", notSavedProtocolPostings);
                if (notSavedProtocolPostings.isEmpty()) {
                    log.info("This is duplicate or empty request");
                } else {
                    List<PostingLog> notSavedDomainPostingLogs = notSavedProtocolPostings
                            .stream()
                            .map(posting -> ProtocolConverter.convertToDomainPosting(posting, currDomainPlanLog))
                            .collect(Collectors.toList());
                    log.info("Adding posting logs");
                    planService.addPostingLogs(notSavedDomainPostingLogs);
                    log.info("Adding account logs");
                    accountService.addAccountLogs(notSavedDomainPostingLogs);
                }

                Map<Long, StatefulAccount> affectedDomainAccountsMap = accountService.getStatefulAccountsUpTo(accountMap.values()
                        .stream()
                        .collect(Collectors.toList()), currDomainPlanLog.getPlanId());

                Map<Long, Account> affectedProtocolAccounts = affectedDomainAccountsMap.values()
                        .stream()
                        .collect(Collectors.toMap(
                                domainStAccount -> domainStAccount.getId(),
                                domainStAccount -> ProtocolConverter.convertFromDomainAccount(domainStAccount)
                        ));
                PostingPlanLog protocolPlanLog = new PostingPlanLog(postingPlan);
                protocolPlanLog.setAffectedAccounts(affectedProtocolAccounts);
                log.info("Response: {}", protocolPlanLog);
                return protocolPlanLog;
            }
        } catch (TException e) {
            errHolder.set(e);
            throw new RuntimeException(e);
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

    public static boolean isFinalOperation(PostingOperation operation) {
        return operation != PostingOperation.HOLD;
    }
}
