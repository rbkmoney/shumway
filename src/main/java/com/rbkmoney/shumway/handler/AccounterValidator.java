package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.InvalidPostingParams;
import com.rbkmoney.damsel.accounter.Posting;
import com.rbkmoney.damsel.accounter.PostingPlan;
import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 05.10.16.
 */
public class AccounterValidator {
    public static final String SOURCE_TARGET_ACC_EQUAL_ERR = "Source and target accounts cannot be the same";
    public static final String AMOUNT_NEGATIVE_ERR = "Amount cannot be negative";
    public static final String POSTING_PLAN_NOT_FOUND_ERR = "Posting plan not found: %s";
    public static final String SAVED_POSTING_NOT_FOUND_ERR = "Saved posting with id: '%d' is not found in received data";
    public static final String POSTING_AMOUNT_NOT_EQUAL_ERR = "Incorrect amount: actual '%d', expected '%d'";
    public static final String POSTING_ACC_NOT_EQUAL_ERR = "Incorrect %s: actual '%d', expected '%d'";
    public static final String POSTING_CURR_CODE_NOT_EQUAL_ERR = "Incorrect currency_sym_code: actual '%d', expected '%d'";
    public static final String POSTING_DESCR_NOT_EQUAL_ERR = "Incorrect description: actual '%s', expected '%s'";
    public static final String POSTING_NOT_FOUND_ERR = "Posting not found";
    public static final String POSTING_NEW_OLD_RECEIVED_ERR = "New and old postings received in same plan, new posting is not allowed";
    public static final String SRC_ACC_NOT_FOUND_ERR = "Source account not found by id: %d";
    public static final String DST_ACC_NOT_FOUND_ERR = "Target account not found by id: %d";
    public static final String ACC_CURR_CODE_NOT_EQUAL_ERR = "Account (%d) currency code is not equal: expected: %s, actual: %s";
    public static final String POSTING_PLAN_STATE_CHANGE_ERR = "Unable to change plan state: %s from: %s to %s";

    public static final Logger log = LoggerFactory.getLogger(AccounterValidator.class);

    public static void validateStatic(PostingPlan postingPlan) throws TException {
        Map<Posting, String> errors = new HashMap<>();

        for (Posting posting : postingPlan.getBatch()) {
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
        if (!errors.isEmpty()) {
            throw new InvalidPostingParams(errors);
        }

    }

    public static Map<Posting, String> createErrMap(List<Posting> postings, String message) {
        Map<Posting, String> wrongPostings = new HashMap<>();
        for (Posting posting : postings) {
           wrongPostings.put(posting, message);
        }
        return wrongPostings;
    }

    public static void validateEqualToSavedPostings(List<Posting> receivedProtocolPostings, List<PostingLog> savedDomainPostingLogs, boolean skipMissing) throws TException {
        Map<Posting, String> wrongPostings = compareToSavedPostings(receivedProtocolPostings, savedDomainPostingLogs, skipMissing);
        if (!wrongPostings.isEmpty()) {
            throw new InvalidPostingParams(wrongPostings);
        }
    }

     public static Map<Posting, String> compareToSavedPostings(List<Posting> receivedProtocolPostings, List<PostingLog> savedPostings, boolean skipMissing) {
         Map<Long, Posting> receivedProtocolPostingMap = receivedProtocolPostings.stream().collect(Collectors.toMap(posting -> posting.getId(), Function.identity()));

        Map<Posting, String> errors = new HashMap<>();
         for (PostingLog postingLog : savedPostings) {
            List<String> errorMessages = new ArrayList<>();

            Posting posting = receivedProtocolPostingMap.get(postingLog.getPostingId());
            if (posting == null) {
                if (!skipMissing) {
                    String message = String.format(SAVED_POSTING_NOT_FOUND_ERR, postingLog.getPostingId());
                    errors.put(ProtocolConverter.convertFromDomainToPosting(postingLog), message);
                }
                continue;
            }

            if (posting.getAmount() != postingLog.getAmount()) {
                String message = String.format(POSTING_AMOUNT_NOT_EQUAL_ERR, posting.getAmount(), postingLog.getAmount());
                errorMessages.add(message);
            }

            if (posting.getFromId() != postingLog.getFromAccountId()) {
                String message = String.format(POSTING_ACC_NOT_EQUAL_ERR, "from_id", posting.getFromId(), postingLog.getFromAccountId());
                errorMessages.add(message);
            }

            if (posting.getToId() != postingLog.getToAccountId()) {
                String message = String.format(POSTING_ACC_NOT_EQUAL_ERR, "to_id", posting.getToId(), postingLog.getToAccountId());
                errorMessages.add(message);
            }

            if (!posting.getCurrencySymCode().equals(postingLog.getCurrSymCode())) {
                String message = String.format(POSTING_CURR_CODE_NOT_EQUAL_ERR, posting.getCurrencySymCode(), postingLog.getCurrSymCode());
                errorMessages.add(message);
            }

            if (!postingLog.getDescription().equals(postingLog.getDescription())) {
                String message = String.format(POSTING_DESCR_NOT_EQUAL_ERR, posting.getDescription(), postingLog.getDescription());
                errorMessages.add(message);
            }

            if (!errorMessages.isEmpty()) {
                errors.put(posting, generateMessage(errorMessages));
            }
        }

        return errors;
    }

    public static List<Posting> validatePostings(PostingPlan receivedProtocolPlan, List<PostingLog> savedDomainPostingLogs, boolean returnNew) throws TException {
        boolean skipMissing = returnNew;
        List<Posting> newProtocolPostingLogs = getNewPostings(receivedProtocolPlan.getBatch(), savedDomainPostingLogs);
        if (newProtocolPostingLogs.size() == receivedProtocolPlan.getBatchSize() && receivedProtocolPlan.getBatchSize() != 0) {
            //all postings're new, no comparison required
            if (returnNew) {
                return newProtocolPostingLogs;
            } else {
                throw new InvalidPostingParams(createErrMap(newProtocolPostingLogs, POSTING_NOT_FOUND_ERR));
            }
        } else if (newProtocolPostingLogs.size() == 0){
            //all postings're already saved, check that data is the same, check if we have some saved postings not presented in received data
            validateEqualToSavedPostings(receivedProtocolPlan.getBatch(), savedDomainPostingLogs, skipMissing);
            return returnNew ? newProtocolPostingLogs : receivedProtocolPlan.getBatch();
        } else {
            //some new and some saved postings in one plan, generate errors for new postings
            throw new InvalidPostingParams(createErrMap(newProtocolPostingLogs, POSTING_NEW_OLD_RECEIVED_ERR));
        }
    }


    public static void validateAccounts(List<Posting> newProtocolPostings, Map<Long, Account> domainAccountMap) throws TException {
        Map<Posting, String> errors = new HashMap<>();
        for (Posting posting : newProtocolPostings) {
            List<String> errorMessages = new ArrayList<>();
            com.rbkmoney.shumway.domain.Account fromAccount = domainAccountMap.get(posting.getFromId());
            com.rbkmoney.shumway.domain.Account toAccount = domainAccountMap.get(posting.getToId());
            if (fromAccount == null) {
                errorMessages.add(String.format(SRC_ACC_NOT_FOUND_ERR, posting.getFromId()));
            } else if (!fromAccount.getCurrSymCode().equals(posting.getCurrencySymCode())) {
                errorMessages.add(String.format(ACC_CURR_CODE_NOT_EQUAL_ERR, fromAccount.getId(), fromAccount.getCurrSymCode(), posting.getCurrencySymCode()));
            }

            if (toAccount == null) {
                errorMessages.add(String.format(DST_ACC_NOT_FOUND_ERR, posting.getToId()));
            } else if (!toAccount.getCurrSymCode().equals(posting.getCurrencySymCode())) {
                errorMessages.add(String.format(ACC_CURR_CODE_NOT_EQUAL_ERR, toAccount.getId(), toAccount.getCurrSymCode(), posting.getCurrencySymCode()));
            }

            if (!errorMessages.isEmpty()) {
                errors.put(posting, generateMessage(errorMessages));
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidPostingParams(errors);
        }
    }

    public static TException validatePlanNotFixedResult(com.rbkmoney.shumway.domain.PostingPlanLog receivedDomainPlanLog, com.rbkmoney.shumway.domain.PostingPlanLog oldDomainPlanLog, boolean failIfNoPlan) {
        log.warn("Posting plan log create/update is not performed, trying to get saved plan");
        if (oldDomainPlanLog == null) {
            if (failIfNoPlan) {
                log.error("Failed to create new posting plan and no matching plan was saved in db. This is inconsistency problem that might be fatal");
                return  new TException("Failed to create or update plan [cannot be resolved automatically]");
            } else {
                log.warn("No matching plan was found in db. This plan is probably not created");
                return  new InvalidRequest(Arrays.asList(String.format(AccounterValidator.POSTING_PLAN_NOT_FOUND_ERR, receivedDomainPlanLog.getPlanId())));
            }
        } else {
            log.warn("Unable to change posting plan state: {} to new state: {}, [overridable: {}]", oldDomainPlanLog, receivedDomainPlanLog, isOverridable(oldDomainPlanLog.getLastOperation(), receivedDomainPlanLog.getLastOperation()));
            return new InvalidRequest(Arrays.asList(String.format(AccounterValidator.POSTING_PLAN_STATE_CHANGE_ERR, receivedDomainPlanLog.getPlanId(), (oldDomainPlanLog == null ? "" : oldDomainPlanLog.getLastOperation()), receivedDomainPlanLog.getLastOperation())));
        }
    }


    private static List<Posting> getNewPostings(List<Posting> receivedProtocolPostings, List<PostingLog> savedDomainPostings) {
        Set<Long> savedPostingIds = savedDomainPostings.stream().map(postingLog -> postingLog.getPostingId()).collect(Collectors.toSet());
        return receivedProtocolPostings.stream().filter(posting -> !savedPostingIds.contains(posting.getId())).collect(Collectors.toList());
    }

    public static String generateMessage(Collection<String> msgs) {
        return StringUtils.collectionToDelimitedString(msgs, "; ");
    }

    public static boolean isOverridable(PostingOperation source, PostingOperation target) {
        return source == PostingOperation.HOLD;
    }

}
