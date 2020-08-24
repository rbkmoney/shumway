package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.shumaich.*;

import java.util.stream.Collectors;

public class ShumaichProtocolConverter {

    public static com.rbkmoney.damsel.shumpune.PostingBatch convertToOldBatch(com.rbkmoney.damsel.shumaich.PostingBatch batch) {
        return new com.rbkmoney.damsel.shumpune.PostingBatch()
                .setId(batch.getId())
                .setPostings(batch.getPostings().stream()
                        .map(posting -> new com.rbkmoney.damsel.shumpune.Posting()
                                .setAmount(posting.getAmount())
                                .setCurrencySymCode(posting.getCurrencySymbolicCode())
                                .setDescription(posting.getDescription())
                                .setFromId(posting.getFromAccount().getId())
                                .setToId(posting.getToAccount().getId())
                        )
                        .collect(Collectors.toList()));
    }

    public static com.rbkmoney.damsel.shumaich.Posting convertToNewPosting(com.rbkmoney.damsel.shumpune.Posting posting) {
        return new com.rbkmoney.damsel.shumaich.Posting()
                .setAmount(posting.getAmount())
                .setCurrencySymbolicCode(posting.getCurrencySymCode())
                .setDescription(posting.getDescription())
                .setFromAccount(new Account(posting.getFromId(), posting.getCurrencySymCode()))
                .setToAccount(new Account(posting.getToId(), posting.getCurrencySymCode()));
    }

    public static com.rbkmoney.damsel.shumpune.PostingPlan convertToOldPostingPlan(com.rbkmoney.damsel.shumaich.PostingPlan plan) {
        return new com.rbkmoney.damsel.shumpune.PostingPlan()
                .setBatchList(plan.getBatchList().stream()
                        .map(ShumaichProtocolConverter::convertToOldBatch)
                        .collect(Collectors.toList()))
                .setId(plan.getId());
    }

    public static com.rbkmoney.damsel.shumaich.PostingBatch convertToNewPostingBatch(com.rbkmoney.damsel.shumpune.PostingBatch postingBatch) {
        return new com.rbkmoney.damsel.shumaich.PostingBatch()
                .setId(postingBatch.getId())
                .setPostings(postingBatch.getPostings().stream()
                        .map(ShumaichProtocolConverter::convertToNewPosting)
                        .collect(Collectors.toList()));
    }

    public static com.rbkmoney.damsel.shumpune.PostingPlanChange convertToOldPostingPlanChange(PostingPlanChange postingPlanChange) {
        PostingBatch batch = postingPlanChange.getBatch();
        return new com.rbkmoney.damsel.shumpune.PostingPlanChange()
                .setId(postingPlanChange.getId())
                .setBatch(ShumaichProtocolConverter.convertToOldBatch(batch));
    }
    
}
