package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.InvalidPostingParams;
import com.rbkmoney.damsel.accounter.Posting;
import com.rbkmoney.damsel.accounter.PostingBatch;
import com.rbkmoney.damsel.accounter.PostingPlan;
import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.StatefulAccount;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 05.10.16.
 */
public class AccounterValidator {
    public static final String SOURCE_TARGET_ACC_EQUAL_ERR = "Source and target accounts cannot be the same";
    public static final String AMOUNT_NEGATIVE_ERR = "Amount cannot be negative";
    public static final String POSTING_PLAN_NOT_FOUND_ERR = "Posting plan not found: %s";
    public static final String SAVED_POSTING_NOT_FOUND_ERR = "Saved posting not found in batch: %d";
    public static final String RECEIVED_POSTING_NOT_FOUND_ERR = "Received posting not found in batch: %d";
    public static final String SRC_ACC_NOT_FOUND_ERR = "Source account not found by id: %d in batch: %d";
    public static final String DST_ACC_NOT_FOUND_ERR = "Target account not found by id: %d in batch: %d";
    public static final String ACC_CURR_CODE_NOT_EQUAL_ERR = "Account (%d) currency code is not equal: expected: %s, actual: %s in batch: %d";
    public static final String POSTING_PLAN_STATE_CHANGE_ERR = "Unable to change plan state: %s from: %s to %s";
    public static final String POSTING_PLAN_EMPTY = "Plan ($s) has no batches inside";
    public static final String POSTING_BATCH_EMPTY = "Posting batch (%d) has no postings inside";
    public static final String POSTING_BATCH_DUPLICATE = "Batch (%d) has duplicate in received list";
    public static final String POSTING_BATCH_ID_RANGE_VIOLATION = "Batch in plan (%d) is not allowed to have long MAX or MIN value";
    public static final String POSTING_BATCH_COUNT_VIOLATION = "Too many batches in posting plan (%s)";
    public static final String POSTING_BATCH_ID_VIOLATION = "Batch has id %d lower than saved id: %d";

    private static final BiFunction<Posting, PostingLog, Boolean> postingComparator = (posting, postingLog) -> {
        if (posting.getAmount() != postingLog.getAmount()) {
            return false;
        } else if (posting.getFromId() != postingLog.getFromAccountId()) {
            return false;
        } else if (posting.getToId() != postingLog.getToAccountId()) {
            return false;
        } else return posting.getCurrencySymCode().equals(postingLog.getCurrSymCode());
    };

    private static final BiFunction<Collection<Posting>, PostingLog, Boolean> containsPostingLog = (postings, postingLog) ->
            postings.stream().filter(posting -> postingComparator.apply(posting, postingLog)).findFirst().isPresent();

    private static final BiFunction<Collection<PostingLog>, Posting, Boolean> containsPosting = (postingLogs, posting) ->
            postingLogs.stream().filter(postingLog -> postingComparator.apply(posting, postingLog)).findFirst().isPresent();

    public static final Logger log = LoggerFactory.getLogger(AccounterValidator.class);

    public static void validateStaticPostings(PostingPlan postingPlan) throws TException {
        Map<Posting, String> errors = new HashMap<>();
        for (PostingBatch batch : postingPlan.getBatchList()) {
            for (Posting posting : batch.getPostings()) {
                List<String> errorMessages = new ArrayList<>();
                if (posting.getFromId() == posting.getToId()) {
                    errorMessages.add(SOURCE_TARGET_ACC_EQUAL_ERR);
                }
                if (posting.getAmount() < 0) {
                    errorMessages.add(AMOUNT_NEGATIVE_ERR);
                }
                if (!errorMessages.isEmpty()) {
                    errors.put(posting, generateMessage(errorMessages));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidPostingParams(errors);
        }

    }

    public static void validateEqualToSavedPostings(Map<Long, List<Posting>> receivedProtocolPostingLogs, Map<Long, List<PostingLog>> savedDomainPostingLogs, boolean skipMissing) throws TException {
        Map<Posting, String> wrongPostings = compareToSavedPostings(receivedProtocolPostingLogs, savedDomainPostingLogs, skipMissing);
        if (!wrongPostings.isEmpty()) {
            throw new InvalidPostingParams(wrongPostings);
        }
    }

    public static Map<Posting, String> compareToSavedPostings(Map<Long, List<Posting>> receivedProtocolPostingsMap, Map<Long, List<PostingLog>> savedDomainPostingsMap, boolean finalOp) {
        Map<Posting, List<String>> errors = new HashMap<>();
        Set<Long> commonIds = new HashSet<>(savedDomainPostingsMap.keySet());
        commonIds.retainAll(receivedProtocolPostingsMap.keySet());

        BiConsumer<Posting, String> addError = (posting, msg) -> errors.compute(
                posting,
                (postingLog, list) -> {
                    list = (list == null ? new ArrayList<>() : list);
                    list.add(msg);
                    return list;
                }
        );

        //todo move to separate method, change err type to InvalidRequest
        long maxSavedBatchId = savedDomainPostingsMap.keySet().stream().mapToLong(i -> i.longValue()).max().orElse(Long.MIN_VALUE);
        long maxNewReceivedBatchId = receivedProtocolPostingsMap.keySet().stream().filter(i -> !commonIds.contains(i)).mapToLong(i -> i.longValue()).min().orElse(Long.MAX_VALUE);
        if (maxNewReceivedBatchId < maxSavedBatchId) {
            List<Posting> postingLogs = receivedProtocolPostingsMap.get(maxNewReceivedBatchId);
            postingLogs.stream().forEach(posting -> addError.accept(posting, String.format(POSTING_BATCH_ID_VIOLATION, maxNewReceivedBatchId, maxSavedBatchId)));
        }


        for (Long batchId : commonIds) {

            List<PostingLog> savedDomainPostings = savedDomainPostingsMap.get(batchId);
            List<Posting> receivedProtocolPostings = receivedProtocolPostingsMap.get(batchId);

            for (PostingLog postingLog : savedDomainPostings) {
                if (!containsPostingLog.apply(receivedProtocolPostings, postingLog)) {
                    addError.accept(ProtocolConverter.convertFromDomainToPosting(postingLog), String.format(SAVED_POSTING_NOT_FOUND_ERR, batchId));
                }
            }

            for (Posting posting : receivedProtocolPostings) {
                if (!containsPosting.apply(savedDomainPostings, posting)) {
                    addError.accept(posting, String.format(RECEIVED_POSTING_NOT_FOUND_ERR, batchId));
                }
            }
        }


        if (finalOp) {
            savedDomainPostingsMap.entrySet()
                    .stream()
                    .filter(entry -> !receivedProtocolPostingsMap.containsKey(entry.getKey()))
                    .flatMap(entry -> entry.getValue().stream())
                    .forEach(postingLog -> addError.accept(ProtocolConverter.convertFromDomainToPosting(postingLog), String.format(SAVED_POSTING_NOT_FOUND_ERR, postingLog.getBatchId())));

            receivedProtocolPostingsMap.entrySet()
                    .stream()
                    .filter(entry -> !savedDomainPostingsMap.containsKey(entry.getKey())).forEach(
                    entry -> entry.getValue()
                            .stream()
                            .forEach(posting -> addError.accept(posting, String.format(RECEIVED_POSTING_NOT_FOUND_ERR, entry.getKey())))
            );
        }

        return errors.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> generateMessage(entry.getValue())));
    }

    public static void validatePlanBatches(PostingPlan receivedProtocolBathPlan, Map<Long, List<PostingLog>> savedDomainBatchLogs, boolean finalOp) throws TException {
        Map<Long, List<Posting>> receivedProtocolBatchLogs = receivedProtocolBathPlan.getBatchList().stream().collect(Collectors.toMap(PostingBatch::getId, PostingBatch::getPostings));
        validateEqualToSavedPostings(receivedProtocolBatchLogs, savedDomainBatchLogs, finalOp);
    }


    public static void validateAccounts(List<PostingBatch> newProtocolPostings, Map<Long, StatefulAccount> domainAccountMap) throws TException {
        Map<Posting, String> errors = new HashMap<>();
        for (PostingBatch newProtocolBatch : newProtocolPostings) {
            for (Posting posting : newProtocolBatch.getPostings()) {
                List<String> errorMessages = new ArrayList<>();
                com.rbkmoney.shumway.domain.Account fromAccount = domainAccountMap.get(posting.getFromId());
                com.rbkmoney.shumway.domain.Account toAccount = domainAccountMap.get(posting.getToId());
                if (fromAccount == null) {
                    errorMessages.add(String.format(SRC_ACC_NOT_FOUND_ERR, posting.getFromId(), newProtocolBatch.getId()));
                } else if (!fromAccount.getCurrSymCode().equals(posting.getCurrencySymCode())) {
                    errorMessages.add(String.format(ACC_CURR_CODE_NOT_EQUAL_ERR, fromAccount.getId(), fromAccount.getCurrSymCode(), posting.getCurrencySymCode(), newProtocolBatch.getId()));
                }

                if (toAccount == null) {
                    errorMessages.add(String.format(DST_ACC_NOT_FOUND_ERR, posting.getToId(), newProtocolBatch.getId()));
                } else if (!toAccount.getCurrSymCode().equals(posting.getCurrencySymCode())) {
                    errorMessages.add(String.format(ACC_CURR_CODE_NOT_EQUAL_ERR, toAccount.getId(), toAccount.getCurrSymCode(), posting.getCurrencySymCode(), newProtocolBatch.getId()));
                }

                if (!errorMessages.isEmpty()) {
                    errors.put(posting, generateMessage(errorMessages));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidPostingParams(errors);
        }
    }

    public static TException validatePlanNotFixedResult(com.rbkmoney.shumway.domain.PostingPlanLog receivedDomainPlanLog, com.rbkmoney.shumway.domain.PostingPlanLog oldDomainPlanLog, boolean failIfNoPlan) {
        log.warn("Posting plan log create/update is not performed");
        if (oldDomainPlanLog == null) {
            if (failIfNoPlan) {
                log.error("Failed to create new posting plan and no matching plan was saved in db. This is inconsistency problem that might be fatal");
                return new TException("Failed to create or update plan [cannot be resolved automatically]");
            } else {
                log.warn("No matching plan was found in db. This plan is probably not created");
                return new InvalidRequest(Arrays.asList(String.format(AccounterValidator.POSTING_PLAN_NOT_FOUND_ERR, receivedDomainPlanLog.getPlanId())));
            }
        } else {
            log.warn("Unable to change posting plan state: {} to new state: {}, [overridable: {}]", oldDomainPlanLog, receivedDomainPlanLog, isOverridable(oldDomainPlanLog.getLastOperation(), receivedDomainPlanLog.getLastOperation()));
            return new InvalidRequest(Arrays.asList(String.format(AccounterValidator.POSTING_PLAN_STATE_CHANGE_ERR, receivedDomainPlanLog.getPlanId(), oldDomainPlanLog.getLastOperation(), receivedDomainPlanLog.getLastOperation())));
        }
    }

    public static void validateStaticPlanBatches(PostingPlan receivedPostingPlan, boolean finalOp) throws InvalidRequest {
        Set<Long> batchIds = new HashSet<>();
        if (receivedPostingPlan.getBatchListSize() < 1) {
            log.warn("Plan {} has not batches inside", receivedPostingPlan.getId());
            throw new InvalidRequest(Arrays.asList(String.format(POSTING_PLAN_EMPTY, receivedPostingPlan.getId())));
        }
        if (!finalOp && receivedPostingPlan.getBatchListSize() != 1) {
            log.warn("More than one batch was received with not final operation on plan {}", receivedPostingPlan.getId());
            throw new InvalidRequest(Arrays.asList(String.format(POSTING_BATCH_COUNT_VIOLATION, receivedPostingPlan.getId())));
        }
        for (PostingBatch postingBatch : receivedPostingPlan.getBatchList()) {
            if (postingBatch.getPostingsSize() < 1) {
                log.warn("Batch {} has no postings inside", postingBatch.getId());
                throw new InvalidRequest(Arrays.asList(String.format(POSTING_BATCH_EMPTY, postingBatch.getId())));
            }
            if (!batchIds.add(postingBatch.getId())) {
                log.warn("Batch {} has duplicate in received list", postingBatch.getId());
                throw new InvalidRequest(Arrays.asList(String.format(POSTING_BATCH_DUPLICATE, postingBatch.getId())));
            }
        }
        if (batchIds.contains(Long.MIN_VALUE) || batchIds.contains(Long.MAX_VALUE)) {
            log.warn("Batch in plan {} is not allowed to have long MAX or MIN value", receivedPostingPlan.getId());
            throw new InvalidRequest(Arrays.asList(String.format(POSTING_BATCH_ID_RANGE_VIOLATION, receivedPostingPlan.getId())));
        }
    }

    public static String generateMessage(Collection<String> msgs) {
        return StringUtils.collectionToDelimitedString(msgs, "; ");
    }

    public static boolean isOverridable(PostingOperation source, PostingOperation target) {
        return source == PostingOperation.HOLD;
    }

}
