package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public class AccountLog {
    private final long id;
    private final long batchId;
    private final String planId;
    private final Instant creationTime;
    private final long accountId;
    private final PostingOperation operation;
    private final long ownAmountDiff;
    private final long negDiff;
    private final long posDiff;
    private final boolean credit;
    private final boolean merged;

    public AccountLog(long id, long batchId, String planId, Instant creationTime, long accountId, PostingOperation operation, long ownAmountDiff, long negDiff, long posDiff, boolean credit, boolean merged) {
        this.id = id;
        this.batchId = batchId;
        this.planId = planId;
        this.creationTime = creationTime;
        this.accountId = accountId;
        this.operation = operation;
        this.ownAmountDiff = ownAmountDiff;
        this.negDiff = negDiff;
        this.posDiff = posDiff;
        this.credit = credit;
        this.merged = merged;
    }

    public long getId() {
        return id;
    }

    public long getBatchId() {
        return batchId;
    }

    public String getPlanId() {
        return planId;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public long getAccountId() {
        return accountId;
    }

    public PostingOperation getOperation() {
        return operation;
    }


    public long getOwnAmountDiff() {
        return ownAmountDiff;
    }

    public long getNegDiff() {
        return negDiff;
    }

    public long getPosDiff() {
        return posDiff;
    }

    public boolean isCredit() {
        return credit;
    }

    public boolean isMerged() {
        return merged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountLog)) return false;
        AccountLog that = (AccountLog) o;
        return id == that.id &&
                batchId == that.batchId &&
                accountId == that.accountId &&
                ownAmountDiff == that.ownAmountDiff &&
                negDiff == that.negDiff &&
                posDiff == that.posDiff &&
                credit == that.credit &&
                merged == that.merged &&
                Objects.equals(planId, that.planId) &&
                Objects.equals(creationTime, that.creationTime) &&
                operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, batchId, planId, creationTime, accountId, operation, ownAmountDiff, negDiff, posDiff, credit, merged);
    }

    @Override
    public String toString() {
        return "AccountLog{" +
                "id=" + id +
                ", batchId=" + batchId +
                ", planId='" + planId + '\'' +
                ", creationTime=" + creationTime +
                ", accountId=" + accountId +
                ", operation=" + operation +
                ", ownAmountDiff=" + ownAmountDiff +
                ", negDiff=" + negDiff +
                ", posDiff=" + posDiff +
                ", credit=" + credit +
                ", merged=" + merged +
                '}';
    }
}
