package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public class PostingPlanLog {
    private long id;
    private final String planId;
    private final Instant lastAccessTime;
    private final PostingOperation lastOperation;
    private final long lastBatchId;

    public PostingPlanLog(long id, String planId, Instant lastAccessTime, PostingOperation lastOperation, long lastBatchId) {
        this.id = id;
        this.planId = planId;
        this.lastAccessTime = lastAccessTime;
        this.lastOperation = lastOperation;
        this.lastBatchId = lastBatchId;
    }

    public long getId() {
        return id;
    }

    public String getPlanId() {
        return planId;
    }

    public Instant getLastAccessTime() {
        return lastAccessTime;
    }

    public PostingOperation getLastOperation() {
        return lastOperation;
    }

    public long getLastBatchId() {
        return lastBatchId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostingPlanLog)) return false;
        PostingPlanLog that = (PostingPlanLog) o;
        return lastBatchId == that.lastBatchId &&
                Objects.equals(planId, that.planId) &&
                Objects.equals(lastAccessTime, that.lastAccessTime) &&
                lastOperation == that.lastOperation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, lastAccessTime, lastOperation, lastBatchId);
    }

    @Override
    public String toString() {
        return "PostingPlanLog{" +
                "id='" + id + '\'' +
                "planId='" + planId + '\'' +
                ", lastAccessTime=" + lastAccessTime +
                ", lastOperation=" + lastOperation +
                ", lastBatchId=" + lastBatchId +
                '}';
    }
}
