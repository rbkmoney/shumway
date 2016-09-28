package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public class PostingPlanLog {
    private final String planId;
    private final Instant lastAccessTime;
    private final PostingOperation lastOperation;
    private final long lastRequestId;

    public PostingPlanLog(String planId, Instant lastAccessTime, PostingOperation lastOperation, long lastRequestId) {
        this.planId = planId;
        this.lastAccessTime = lastAccessTime;
        this.lastOperation = lastOperation;
        this.lastRequestId = lastRequestId;
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

    public long getLastRequestId() {
        return lastRequestId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostingPlanLog)) return false;
        PostingPlanLog that = (PostingPlanLog) o;
        return lastRequestId == that.lastRequestId &&
                Objects.equals(planId, that.planId) &&
                Objects.equals(lastAccessTime, that.lastAccessTime) &&
                lastOperation == that.lastOperation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, lastAccessTime, lastOperation, lastRequestId);
    }

    @Override
    public String toString() {
        return "PostingPlanLog{" +
                "planId='" + planId + '\'' +
                ", lastAccessTime=" + lastAccessTime +
                ", lastOperation=" + lastOperation +
                ", lastRequestId=" + lastRequestId +
                '}';
    }
}
