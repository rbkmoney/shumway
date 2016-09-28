package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public class AccountLog {
    private final long id;
    private final long requestId;
    private final long postingId;
    private final String planId;
    private final Instant creationTime;
    private final long accountId;
    private final PostingOperation operation;
    private final long amount;

    public AccountLog(long id, long requestId, long postingId, String planId, Instant creationTime, long accountId, PostingOperation operation, long amount) {
        this.id = id;
        this.requestId = requestId;
        this.postingId = postingId;
        this.planId = planId;
        this.creationTime = creationTime;
        this.accountId = accountId;
        this.operation = operation;
        this.amount = amount;
    }

    public long getId() {
        return id;
    }

    public long getRequestId() {
        return requestId;
    }

    public long getPostingId() {
        return postingId;
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

    public long getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountLog)) return false;
        AccountLog that = (AccountLog) o;
        return id == that.id &&
                postingId == that.postingId &&
                requestId == that.requestId &&
                accountId == that.accountId &&
                amount == that.amount &&
                Objects.equals(planId, that.planId) &&
                Objects.equals(creationTime, that.creationTime) &&
                operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, postingId, planId, requestId, creationTime, accountId, operation, amount);
    }

    @Override
    public String toString() {
        return "AccountLog{" +
                "id=" + id +
                ", requestId=" + requestId +
                ", postingId=" + postingId +
                ", planId='" + planId + '\'' +
                ", creationTime=" + creationTime +
                ", accountId=" + accountId +
                ", operation=" + operation +
                ", amount=" + amount +
                '}';
    }
}
