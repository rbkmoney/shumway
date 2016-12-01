package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public class AccountLog {
    private final long id;
    private final long batchId;
    private final long planId;
    private final Instant creationTime;
    private final long accountId;
    private final PostingOperation operation;
    private final long amount;
    private final long ownAmount;
    private final long ownAmountDelta;
    private final boolean credit;
    private final boolean merged;

    public AccountLog(long id, long batchId, long planId, Instant creationTime, long accountId, PostingOperation operation, long amount, long ownAmount, long ownAmountDelta, boolean credit, boolean merged) {
        this.id = id;
        this.batchId = batchId;
        this.planId = planId;
        this.creationTime = creationTime;
        this.accountId = accountId;
        this.operation = operation;
        this.amount = amount;
        this.ownAmount = ownAmount;
        this.ownAmountDelta = ownAmountDelta;
        this.credit = credit;
        this.merged = merged;
    }

    public long getId() {
        return id;
    }

    public long getBatchId() {
        return batchId;
    }

    public long getPlanId() {
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

    public long getAmount() {
        return amount;
    }

    public long getOwnAmountDelta() {
        return ownAmountDelta;
    }

    public long getOwnAmount() {
        return ownAmount;
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
                amount == that.amount &&
                ownAmount == that.ownAmount &&
                ownAmountDelta == that.ownAmountDelta &&
                credit == that.credit &&
                merged == that.merged &&
                Objects.equals(planId, that.planId) &&
                Objects.equals(creationTime, that.creationTime) &&
                operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, batchId, planId, creationTime, accountId, operation, amount, ownAmount, ownAmountDelta, credit, merged);
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
                ", amount=" + amount +
                ", ownAmount=" + ownAmount +
                ", ownAmountDelta=" + ownAmountDelta +
                ", credit=" + credit +
                ", merged=" + merged +
                '}';
    }
}
