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
    private final long ownAmountSum;
    private final long maxAvailableAmountSum;
    private final long minAvailableAmountSum;
    private final long ownDiff;
    private final long minDiff;
    private final long maxDiff;
    private final boolean credit;
    private final boolean merged;

    public AccountLog(long id, long batchId, String planId, Instant creationTime, long accountId, PostingOperation operation, long ownAmountSum, long maxAvailableAmountSum, long minAvailableAmountSum, long ownDiff, long minDiff, long maxDiff, boolean credit, boolean merged) {
        this.id = id;
        this.batchId = batchId;
        this.planId = planId;
        this.creationTime = creationTime;
        this.ownAmountSum = ownAmountSum;
        this.maxAvailableAmountSum = maxAvailableAmountSum;
        this.minAvailableAmountSum = minAvailableAmountSum;
        this.accountId = accountId;
        this.operation = operation;
        this.ownDiff = ownDiff;
        this.minDiff = minDiff;
        this.maxDiff = maxDiff;
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

    public long getOwnAmountSum() {
        return ownAmountSum;
    }

    public long getMaxAvailableAmountSum() {
        return maxAvailableAmountSum;
    }

    public long getMinAvailableAmountSum() {
        return minAvailableAmountSum;
    }

    public long getOwnDiff() {
        return ownDiff;
    }

    public long getMinDiff() {
        return minDiff;
    }

    public long getMaxDiff() {
        return maxDiff;
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
        return getId() == that.getId() &&
                getBatchId() == that.getBatchId() &&
                getAccountId() == that.getAccountId() &&
                getOwnAmountSum() == that.getOwnAmountSum() &&
                getMaxAvailableAmountSum() == that.getMaxAvailableAmountSum() &&
                getMinAvailableAmountSum() == that.getMinAvailableAmountSum() &&
                getOwnDiff() == that.getOwnDiff() &&
                getMinDiff() == that.getMinDiff() &&
                getMaxDiff() == that.getMaxDiff() &&
                isCredit() == that.isCredit() &&
                isMerged() == that.isMerged() &&
                Objects.equals(getPlanId(), that.getPlanId()) &&
                Objects.equals(getCreationTime(), that.getCreationTime()) &&
                getOperation() == that.getOperation();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getBatchId(), getPlanId(), getCreationTime(), getAccountId(), getOperation(), getOwnAmountSum(), getMaxAvailableAmountSum(), getMinAvailableAmountSum(), getOwnDiff(), getMinDiff(), getMaxDiff(), isCredit(), isMerged());
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
                ", ownAmountSum=" + ownAmountSum +
                ", maxAvailableAmountSum=" + maxAvailableAmountSum +
                ", minAvailableAmountSum=" + minAvailableAmountSum +
                ", ownDiff=" + ownDiff +
                ", minDiff=" + minDiff +
                ", maxDiff=" + maxDiff +
                ", credit=" + credit +
                ", merged=" + merged +
                '}';
    }
}
