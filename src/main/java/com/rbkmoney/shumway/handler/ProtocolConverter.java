package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.AccountPrototype;
import com.rbkmoney.damsel.accounter.Posting;
import com.rbkmoney.damsel.accounter.PostingBatch;
import com.rbkmoney.damsel.accounter.PostingPlan;
import com.rbkmoney.shumway.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 19.09.16.
 */
public class ProtocolConverter {

    public static Account convertToDomainAccount(AccountPrototype protocolPrototype) {
        return new Account(0, Instant.now(), protocolPrototype.getCurrencySymCode(), protocolPrototype.getDescription());
    }

    public static com.rbkmoney.damsel.accounter.Account convertFromDomainAccount(StatefulAccount domainAccount) {
        AccountState accountState = domainAccount.getAccountState();
        com.rbkmoney.damsel.accounter.Account protocolAccount = new com.rbkmoney.damsel.accounter.Account(domainAccount.getId(), accountState.getOwnAmount(), accountState.getMaxAvailableAmount(), accountState.getMinAvailableAmount(), domainAccount.getCurrSymCode());
        protocolAccount.setDescription(domainAccount.getDescription());
        return protocolAccount;
    }

    public static Posting convertFromDomainToPosting(PostingLog domainPostingLog) {
        return new Posting(domainPostingLog.getFromAccountId(), domainPostingLog.getToAccountId(), domainPostingLog.getAmount(), domainPostingLog.getCurrSymCode(), domainPostingLog.getDescription());
    }

    public static PostingLog convertToDomainPosting(Posting protocolPosting,  com.rbkmoney.shumway.domain.PostingPlanLog currentDomainPlanLog) {
        return new PostingLog(0, currentDomainPlanLog.getId(), currentDomainPlanLog.getLastBatchId(), protocolPosting.getFromId(), protocolPosting.getToId(), protocolPosting.getAmount(), Instant.now(), currentDomainPlanLog.getLastOperation(), protocolPosting.getCurrencySymCode(), protocolPosting.getDescription());
    }

    public static PostingPlanLog convertToDomainPlan(PostingPlan protocolPostingPlan, PostingOperation domainPostingOperation) {
        PostingPlanLog domainPlanLog = new PostingPlanLog(0, protocolPostingPlan.getId(), Instant.now(), domainPostingOperation, protocolPostingPlan.getBatchList().get(0).getId());
        return domainPlanLog;
    }

    public static PostingBatch convertFromDomainToBatch(long batchId, List<PostingLog> domainPostings) {
        return new PostingBatch(batchId, domainPostings.stream().map(ProtocolConverter::convertFromDomainToPosting).collect(Collectors.toList()));
    }

}
