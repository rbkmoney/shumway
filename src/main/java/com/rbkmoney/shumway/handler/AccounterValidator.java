package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.InvalidPostingParams;
import com.rbkmoney.damsel.accounter.Posting;
import com.rbkmoney.damsel.accounter.PostingPlan;
import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.PostingLog;
import org.apache.thrift.TException;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 05.10.16.
 */
public class AccounterValidator {
    public static void validateStatic(PostingPlan postingPlan) throws TException {
        Map<Posting, String> errors = new HashMap<>();

        for (Posting posting : postingPlan.getBatch()) {
            List<String> errorMessages = new ArrayList<>();
            if (posting.getFromId() == posting.getToId()) {
                errorMessages.add("Source and target accounts cannot be the same");
            }
            if (posting.getAmount() < 0) {
                errorMessages.add("Amount cannot be negative");
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
                    String message = String.format("Saved posting with id: '%d' is not found in received data", postingLog.getPostingId());
                    errors.put(ProtocolConverter.convertFromDomainToPosting(postingLog), message);
                }
                continue;
            }

            if (posting.getAmount() != postingLog.getAmount()) {
                String message = String.format("Incorrect amount: actual '%d', expected '%d'", posting.getAmount(), postingLog.getAmount());
                errorMessages.add(message);
            }

            if (posting.getFromId() != postingLog.getFromAccountId()) {
                String message = String.format("Incorrect from_id: actual '%d', expected '%d'", posting.getFromId(), postingLog.getFromAccountId());
                errorMessages.add(message);
            }

            if (posting.getToId() != postingLog.getToAccountId()) {
                String message = String.format("Incorrect to_id: actual '%d', expected '%d'", posting.getToId(), postingLog.getToAccountId());
                errorMessages.add(message);
            }

            if (!posting.getCurrencySymCode().equals(postingLog.getCurrSymCode())) {
                String message = String.format("Incorrect currency_sym_code: actual '%d', expected '%d'", posting.getCurrencySymCode(), postingLog.getCurrSymCode());
                errorMessages.add(message);
            }

            if (!postingLog.getDescription().equals(postingLog.getDescription())) {
                String message = String.format("Incorrect description: actual '%s', expected '%s'", posting.getDescription(), postingLog.getDescription());
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
                throw new InvalidPostingParams(createErrMap(newProtocolPostingLogs, "Posting not found"));
            }
        } else if (newProtocolPostingLogs.size() == 0){
            //all postings're already saved, check that data is the same, check if we have some saved postings not presented in received data
            validateEqualToSavedPostings(receivedProtocolPlan.getBatch(), savedDomainPostingLogs, skipMissing);
            return returnNew ? newProtocolPostingLogs : receivedProtocolPlan.getBatch();
        } else {
            //some new and some saved postings in one plan, generate errors for new postings
            throw new InvalidPostingParams(createErrMap(newProtocolPostingLogs, "New and old postings received in same plan, new posting is not allowed"));
        }
    }


    public static void validateAccounts(List<Posting> newProtocolPostings, Map<Long, Account> domainAccountMap) throws TException {
        Map<Posting, String> errors = new HashMap<>();
        for (Posting posting : newProtocolPostings) {
            List<String> errorMessages = new ArrayList<>();
            com.rbkmoney.shumway.domain.Account fromAccount = domainAccountMap.get(posting.getFromId());
            com.rbkmoney.shumway.domain.Account toAccount = domainAccountMap.get(posting.getToId());
            if (fromAccount == null) {
                errorMessages.add(String.format("Source account not found by id: %d", posting.getFromId()));
            } else if (!fromAccount.getCurrSymCode().equals(posting.getCurrencySymCode())) {
                errorMessages.add(String.format("Account (%d) currency code is not equal: expected: %s, actual: %s", fromAccount.getId(), fromAccount.getCurrSymCode(), posting.getCurrencySymCode()));
            }

            if (toAccount == null) {
                errorMessages.add(String.format("Target account not found by id: %d", posting.getToId()));
            } else if (!toAccount.getCurrSymCode().equals(posting.getCurrencySymCode())) {
                errorMessages.add(String.format("Account (%d) currency code is not equal: expected: %s, actual: %s", toAccount.getId(), toAccount.getCurrSymCode(), posting.getCurrencySymCode()));
            }

            if (!errorMessages.isEmpty()) {
                errors.put(posting, generateMessage(errorMessages));
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidPostingParams(errors);
        }
    }


    private static List<Posting> getNewPostings(List<Posting> receivedProtocolPostings, List<PostingLog> savedDomainPostings) {
        Set<Long> savedPostingIds = savedDomainPostings.stream().map(postingLog -> postingLog.getPostingId()).collect(Collectors.toSet());
        return receivedProtocolPostings.stream().filter(posting -> !savedPostingIds.contains(posting.getId())).collect(Collectors.toList());
    }

    private static String generateMessage(Collection<String> msgs) {
        return StringUtils.collectionToDelimitedString(msgs, "; ");
    }

}
