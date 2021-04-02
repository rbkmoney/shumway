package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public class PostingLog {
    private final long id;
    private final String planId;
    private final long batchId;
    private final long fromAccountId;
    private final long toAccountId;
    private final long amount;
    private final Instant creationTime;
    private final PostingOperation operation;
    private final String currSymCode;
    private final String description;

    public PostingLog(long id, String planId, long batchId, long fromAccountId, long toAccountId, long amount,
                      Instant creationTime, PostingOperation operation, String currSymCode, String description) {
        this.id = id;
        this.planId = planId;
        this.batchId = batchId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.creationTime = creationTime;
        this.operation = operation;
        this.currSymCode = currSymCode;
        this.description = description;
    }

    public long getId() {
        return id;
    }

    public String getPlanId() {
        return planId;
    }

    public long getBatchId() {
        return batchId;
    }


    public long getFromAccountId() {
        return fromAccountId;
    }

    public long getToAccountId() {
        return toAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public PostingOperation getOperation() {
        return operation;
    }

    public String getCurrSymCode() {
        return currSymCode;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostingLog)) {
            return false;
        }
        PostingLog that = (PostingLog) o;
        return id == that.id
                && batchId == that.batchId
                && fromAccountId == that.fromAccountId
                && toAccountId == that.toAccountId
                && amount == that.amount
                && Objects.equals(planId, that.planId)
                && Objects.equals(creationTime, that.creationTime)
                && operation == that.operation
                && Objects.equals(currSymCode, that.currSymCode)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(id, planId, batchId, fromAccountId, toAccountId, amount, creationTime, operation, currSymCode,
                        description);
    }

    @Override
    public String toString() {
        return "PostingLog{" +
                "id=" + id +
                ", planId='" + planId + '\'' +
                ", batchId=" + batchId +
                ", fromAccountId=" + fromAccountId +
                ", toAccountId=" + toAccountId +
                ", amount=" + amount +
                ", creationTime=" + creationTime +
                ", operation=" + operation +
                ", currSymCode='" + currSymCode + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
