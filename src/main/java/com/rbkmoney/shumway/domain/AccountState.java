package com.rbkmoney.shumway.domain;

import java.util.Objects;

/**
 * Created by vpankrashkin on 18.09.16.
 */
public class AccountState {
    private final long ownAmount;
    private final long minAccumulatedDiff;
    private final long maxAccumulatedDuff;

    public AccountState() {
        this(0, 0, 0);
    }

    public AccountState(long ownAccumulatedAmount, long minAccumulatedDiff, long maxAccumulatedDuff) {
        this.ownAmount = ownAccumulatedAmount;
        this.minAccumulatedDiff = minAccumulatedDiff;
        this.maxAccumulatedDuff = maxAccumulatedDuff;
    }

    public long getOwnAmount() {
        return ownAmount;
    }

    public long getMaxAccumulatedDuff() {
        return maxAccumulatedDuff;
    }

    public long getMinAccumulatedDiff() {
        return minAccumulatedDiff;
    }

    public long getMaxAvailableAmount() {
        return ownAmount + maxAccumulatedDuff;
    }

    public long getMinAvailableAmount() {
        return ownAmount + minAccumulatedDiff;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountState)) return false;
        AccountState that = (AccountState) o;
        return ownAmount == that.ownAmount &&
                maxAccumulatedDuff == that.maxAccumulatedDuff &&
                minAccumulatedDiff == that.minAccumulatedDiff;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownAmount, maxAccumulatedDuff, minAccumulatedDiff);
    }
}
