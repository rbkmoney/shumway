package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.shumpune.*;

import java.util.stream.Collectors;

public class ShumpuneProtocolConverter {


    public static com.rbkmoney.damsel.accounter.PostingBatch convertToOldBatch(com.rbkmoney.damsel.shumpune.PostingBatch batch) {
        return new com.rbkmoney.damsel.accounter.PostingBatch()
                .setId(batch.getId())
                .setPostings(batch.getPostings().stream()
                        .map(posting -> new com.rbkmoney.damsel.accounter.Posting()
                                .setAmount(posting.getAmount())
                                .setCurrencySymCode(posting.getCurrencySymCode())
                                .setDescription(posting.getDescription())
                                .setFromId(posting.getFromId())
                                .setToId(posting.getToId())
                        )
                        .collect(Collectors.toList()));
    }

    public static com.rbkmoney.damsel.shumpune.Posting convertToNewPosting(com.rbkmoney.damsel.accounter.Posting posting) {
        return new com.rbkmoney.damsel.shumpune.Posting()
                .setAmount(posting.getAmount())
                .setCurrencySymCode(posting.getCurrencySymCode())
                .setDescription(posting.getDescription())
                .setFromId(posting.getFromId())
                .setToId(posting.getToId());
    }

    public static com.rbkmoney.damsel.accounter.PostingPlan convertToOldPostingPlan(com.rbkmoney.damsel.shumpune.PostingPlan plan) {
        return new com.rbkmoney.damsel.accounter.PostingPlan()
                .setBatchList(plan.getBatchList().stream()
                        .map(ShumpuneProtocolConverter::convertToOldBatch)
                        .collect(Collectors.toList()))
                .setId(plan.getId());
    }

    public static com.rbkmoney.damsel.shumpune.PostingBatch convertToNewPostingBatch(com.rbkmoney.damsel.accounter.PostingBatch postingBatch) {
        return new com.rbkmoney.damsel.shumpune.PostingBatch()
                .setId(postingBatch.getId())
                .setPostings(postingBatch.getPostings().stream()
                        .map(ShumpuneProtocolConverter::convertToNewPosting)
                        .collect(Collectors.toList()));
    }

    public static PostingPlan convertToNewPostingPlan(com.rbkmoney.damsel.accounter.PostingPlan plan) {
        return new PostingPlan()
                .setBatchList(plan.getBatchList().stream()
                        .map(ShumpuneProtocolConverter::convertToNewPostingBatch)
                        .collect(Collectors.toList()));
    }

    public static Account convertToNewAccount(com.rbkmoney.damsel.accounter.Account accountByID) {
        return new Account()
                .setCreationTime(accountByID.getCreationTime())
                .setCurrencySymCode(accountByID.getCurrencySymCode())
                .setDescription(accountByID.getDescription())
                .setId(accountByID.getId());
    }

    public static Balance convertToNewBalance(com.rbkmoney.damsel.accounter.Account accountByID) {
        return new Balance()
                .setId(accountByID.getId())
                .setMaxAvailableAmount(accountByID.getMaxAvailableAmount())
                .setMinAvailableAmount(accountByID.getMinAvailableAmount())
                .setOwnAmount(accountByID.getOwnAmount());
    }

    public static com.rbkmoney.damsel.accounter.AccountPrototype convertToOldAccountPrototype(AccountPrototype prototype) {
        return new com.rbkmoney.damsel.accounter.AccountPrototype()
                .setCreationTime(prototype.getCreationTime())
                .setCurrencySymCode(prototype.getCurrencySymCode())
                .setDescription(prototype.getDescription());
    }

    public static com.rbkmoney.damsel.accounter.PostingPlanChange convertToOldPostingPlanChange(PostingPlanChange postingPlanChange) {
        PostingBatch batch = postingPlanChange.getBatch();
        return new com.rbkmoney.damsel.accounter.PostingPlanChange()
                .setId(postingPlanChange.getId())
                .setBatch(ShumpuneProtocolConverter.convertToOldBatch(batch));
    }
}
