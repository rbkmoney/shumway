package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.Account;
import com.rbkmoney.damsel.accounter.AccountNotFound;
import com.rbkmoney.damsel.accounter.AccountPrototype;
import com.rbkmoney.damsel.accounter.AccounterSrv;
import com.rbkmoney.damsel.accounter.PlanNotFound;
import com.rbkmoney.damsel.accounter.PostingBatch;
import com.rbkmoney.damsel.accounter.PostingPlan;
import com.rbkmoney.damsel.accounter.PostingPlanChange;
import com.rbkmoney.damsel.accounter.PostingPlanLog;
import com.rbkmoney.shumway.dao.DaoException;
import com.rbkmoney.shumway.domain.AccountState;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.StatefulAccount;
import com.rbkmoney.shumway.service.AccountService;
import com.rbkmoney.shumway.service.PostingPlanService;
import com.rbkmoney.woody.api.flow.error.WUnavailableResultException;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rbkmoney.shumway.handler.AccounterValidator.validatePlanNotFixedResult;
import static com.rbkmoney.shumway.handler.ProtocolConverter.convertFromDomainAccount;
import static com.rbkmoney.shumway.handler.ProtocolConverter.convertFromDomainToBatch;
import static com.rbkmoney.shumway.handler.ProtocolConverter.convertToDomainAccount;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class AccounterHandler implements AccounterSrv.Iface {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final TransactionTemplate transactionTemplate;

    private final AccountService accountService;
    private final PostingPlanService planService;

    public AccounterHandler(
            AccountService accountService,
            PostingPlanService planService,
            TransactionTemplate transactionTemplate
    ) {
        this.accountService = accountService;
        this.planService = planService;
        this.transactionTemplate = transactionTemplate;
    }

    public static boolean isFinalOperation(PostingOperation operation) {
        return operation != PostingOperation.HOLD;
    }

    @Override
    public PostingPlanLog hold(PostingPlanChange planChange) throws TException {
        return doSafeOperation(new PostingPlan(planChange.getId(), Arrays.asList(planChange.getBatch())),
                PostingOperation.HOLD);
    }

    @Override
    public PostingPlanLog commitPlan(PostingPlan postingPlan) throws TException {
        return doSafeOperation(postingPlan, PostingOperation.COMMIT);
    }

    @Override
    public PostingPlanLog rollbackPlan(PostingPlan postingPlan) throws TException {
        return doSafeOperation(postingPlan, PostingOperation.ROLLBACK);
    }

    protected PostingPlanLog doSafeOperation(PostingPlan postingPlan, PostingOperation operation) throws TException {
        Map<Long, com.rbkmoney.shumway.domain.StatefulAccount> affectedDomainStatefulAccounts;
        try {
            affectedDomainStatefulAccounts =
                    transactionTemplate.execute(transactionStatus -> safePostingOperation(postingPlan, operation));
            Map<Long, Account> affectedProtocolAccounts = affectedDomainStatefulAccounts.values()
                    .stream()
                    .collect(Collectors.toMap(
                            domainStAccount -> domainStAccount.getId(),
                            domainStAccount -> ProtocolConverter.convertFromDomainAccount(domainStAccount)
                    ));
            PostingPlanLog protocolPostingPlanLog = new PostingPlanLog(affectedProtocolAccounts);
            log.info("PostingPlanLog of affected accounts: {}", protocolPostingPlanLog);
            return protocolPostingPlanLog;
        } catch (Exception e) {
            log.error("PostingOperation processing error: ", e);
            if (e instanceof TransactionException) {
                throw new WUnavailableResultException(e);
            } else if (e.getCause() instanceof TException) {
                throw (TException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private Map<Long, com.rbkmoney.shumway.domain.StatefulAccount> safePostingOperation(
            PostingPlan postingPlan,
            PostingOperation operation
    ) {
        boolean finalOp = isFinalOperation(operation);
        try {
            log.info("New {} request, plan: {}", operation, postingPlan);
            AccounterValidator.validateStaticPlanBatches(postingPlan, finalOp);
            AccounterValidator.validateStaticPostings(postingPlan);
            com.rbkmoney.shumway.domain.PostingPlanLog receivedDomainPlanLog =
                    ProtocolConverter.convertToDomainPlan(postingPlan, operation);

            Map.Entry<com.rbkmoney.shumway.domain.PostingPlanLog, com.rbkmoney.shumway.domain.PostingPlanLog>
                    postingPlanLogPair = finalOp
                    ? planService.updatePostingPlan(receivedDomainPlanLog, operation)
                    : planService.createOrUpdatePostingPlan(receivedDomainPlanLog);
            com.rbkmoney.shumway.domain.PostingPlanLog oldDomainPlanLog = postingPlanLogPair.getKey();
            com.rbkmoney.shumway.domain.PostingPlanLog currDomainPlanLog = postingPlanLogPair.getValue();
            log.info("Old plan log is {}, curr plan log is {}", oldDomainPlanLog, currDomainPlanLog);

            PostingOperation prevOperation =
                    oldDomainPlanLog == null ? PostingOperation.HOLD : oldDomainPlanLog.getLastOperation();
            if (currDomainPlanLog == null) {
                throw validatePlanNotFixedResult(receivedDomainPlanLog, oldDomainPlanLog, !finalOp);
            } else {
                Map<Long, List<PostingLog>> savedDomainPostingLogs =
                        planService.getPostingLogs(currDomainPlanLog.getPlanId(), prevOperation);

                AccounterValidator.validatePlanBatches(postingPlan, savedDomainPostingLogs, finalOp);

                //generally - valid result is single received batch for new hold and empty for any commit or rollback
                List<PostingBatch> newProtocolBatches = postingPlan.getBatchList()
                        .stream()
                        .filter(batch -> !savedDomainPostingLogs.containsKey(batch.getId()))
                        .collect(Collectors.toList());
                Map<Long, AccountState> resultAccStates;
                Map<Long, StatefulAccount> savedDomainStatefulAcc;
                if (prevOperation == operation && newProtocolBatches.isEmpty()) {
                    log.info("This is duplicate request: {}", operation);
                    savedDomainStatefulAcc = accountService.getStatefulAccounts(
                            postingPlan.getBatchList(),
                            postingPlan.getId(),
                            isFinalOperation(operation)
                    );
                    resultAccStates = savedDomainStatefulAcc.values().stream()
                            .collect(Collectors.toMap(acc -> acc.getId(), acc -> acc.getAccountState()));
                } else {
                    savedDomainStatefulAcc = accountService.getStatefulExclusiveAccounts(postingPlan.getBatchList());
                    AccounterValidator.validateAccounts(newProtocolBatches, savedDomainStatefulAcc);
                    log.debug("Saving posting batches: {}", newProtocolBatches);
                    List<PostingLog> newDomainPostingLogs = postingPlan.getBatchList()
                            .stream()
                            .flatMap(batch -> batch.getPostings().stream().map(posting -> ProtocolConverter
                                    .convertToDomainPosting(posting, batch, currDomainPlanLog)))
                            .collect(Collectors.toList());
                    log.info("New posting logs are {}", newDomainPostingLogs);
                    planService.addPostingLogs(newDomainPostingLogs);
                    if (PostingOperation.HOLD.equals(operation)) {
                        List<PostingLog> savedDomainPostingLogList = savedDomainPostingLogs.values().stream()
                                .flatMap(Collection::stream)
                                .collect(Collectors.toList());
                        resultAccStates = accountService.holdAccounts(
                                postingPlan.getId(),
                                postingPlan.getBatchList().get(0),
                                newDomainPostingLogs,
                                savedDomainPostingLogList,
                                savedDomainStatefulAcc
                        );
                    } else {
                        resultAccStates = accountService.commitOrRollback(
                                operation,
                                postingPlan.getId(),
                                newDomainPostingLogs,
                                savedDomainStatefulAcc
                        );
                    }
                }
                log.info("Result account state is {}", resultAccStates);
                return accountService.getStatefulAccounts(savedDomainStatefulAcc, () -> resultAccStates);
            }
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PostingPlan getPlan(String planId) throws TException {
        log.info("New GetPlan request, id: {}", planId);

        com.rbkmoney.shumway.domain.PostingPlanLog domainPostingPlan;
        try {
            domainPostingPlan = planService.getSharedPostingPlan(planId);
        } catch (Exception e) {
            log.error("Failed to get posting plan log", e);
            if (e instanceof DaoException) {
                throw new WUnavailableResultException(e);
            }
            throw new TException(e);
        }
        if (domainPostingPlan == null) {
            log.warn("Not found plan with id: {}", planId);
            throw new PlanNotFound(planId);
        }
        Map<Long, List<PostingLog>> domainBatchLogs;
        try {
            domainBatchLogs = planService.getPostingLogs(planId, PostingOperation.HOLD);
        } catch (Exception e) {
            log.error("Failed to get posting logs", e);
            if (e instanceof DaoException) {
                throw new WUnavailableResultException(e);
            }
            throw new TException(e);
        }
        List<PostingBatch> protocolBatchList = domainBatchLogs.entrySet().stream()
                .map(entry -> convertFromDomainToBatch(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        PostingPlan protocolPlan = new PostingPlan(planId, protocolBatchList);
        log.info("Response: {}", protocolPlan);
        return protocolPlan;
    }

    @Override
    public long createAccount(AccountPrototype accountPrototype) throws TException {
        log.info("New CreateAccount request, proto: {}", accountPrototype);
        com.rbkmoney.shumway.domain.Account domainPrototype = convertToDomainAccount(accountPrototype);
        long response;
        try {
            response = accountService.createAccount(domainPrototype);
        } catch (Exception e) {
            log.error("Failed to create account", e);
            if (e instanceof DaoException) {
                throw new WUnavailableResultException(e);
            }
            throw new TException(e);
        }
        log.info("Response: {}", response);
        return response;
    }

    @Override
    public Account getAccountByID(long id) throws TException {
        log.info("New GetAccountById request, id: {}", id);
        StatefulAccount domainAccount;
        try {
            domainAccount = accountService.getStatefulAccount(id);
        } catch (Exception e) {
            log.error("Failed to get account", e);
            if (e instanceof DaoException) {
                throw new WUnavailableResultException(e);
            }
            throw new TException(e);
        }
        if (domainAccount == null) {
            log.warn("Not found account with id: {}", id);
            throw new AccountNotFound(id);
        }
        Account response = convertFromDomainAccount(domainAccount);
        log.info("Response: {}", response);
        return response;
    }

}
