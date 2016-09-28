package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.accounter.AccountPrototype;
import com.rbkmoney.damsel.accounter.Posting;
import com.rbkmoney.damsel.accounter.PostingPlan;
import com.rbkmoney.shumway.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Created by vpankrashkin on 19.09.16.
 */
public class ProtocolConverter {
    private static final Logger log = LoggerFactory.getLogger(ProtocolConverter.class);

    public static Account convertToDomainAccount(AccountPrototype protocolPrototype) {
        return new Account(0, Instant.now(), protocolPrototype.getCurrencySymCode(), protocolPrototype.getDescription());
    }

    public static com.rbkmoney.damsel.accounter.Account convertFromDomainAccount(StatefulAccount domainAccount) {
        AmountState amountState = domainAccount.getAmountState();
        com.rbkmoney.damsel.accounter.Account protocolAccount = new com.rbkmoney.damsel.accounter.Account(domainAccount.getId(), amountState != null ? amountState.getOwnAmount() : 0, amountState != null ? amountState.getAvailableAmount() : 0, domainAccount.getCurrSymCode());
        protocolAccount.setDescription(domainAccount.getDescription());
        return protocolAccount;
    }

    public static Posting convertFromDomainToPosting(PostingLog domainPostingLog) {
        Posting protocolPosting = new Posting(domainPostingLog.getPostingId(), domainPostingLog.getFromAccountId(), domainPostingLog.getToAccountId(), domainPostingLog.getAmount(), domainPostingLog.getCurrSymCode(), domainPostingLog.getDescription());
        return protocolPosting;
    }

    public static PostingLog convertToDomainPosting(Posting protocolPosting,  com.rbkmoney.shumway.domain.PostingPlanLog currentDomainPlanLog) {
        PostingLog domainPosting = new PostingLog(0, currentDomainPlanLog.getPlanId(), protocolPosting.getId(), currentDomainPlanLog.getLastRequestId(), protocolPosting.getFromId(), protocolPosting.getToId(), protocolPosting.getAmount(), Instant.now(), currentDomainPlanLog.getLastOperation(), protocolPosting.getCurrencySymCode(), protocolPosting.getDescription());
        return domainPosting;
    }


    public static PostingPlanLog convertToDomainPlan(PostingPlan protocolPostingPlan, PostingOperation domainPostingOperation) {
        PostingPlanLog domainPlanLog = new PostingPlanLog(protocolPostingPlan.getId(), Instant.now(), domainPostingOperation, 0);
        return domainPlanLog;
    }

}
