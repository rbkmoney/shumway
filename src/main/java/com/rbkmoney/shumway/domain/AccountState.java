package com.rbkmoney.shumway.domain;

import java.util.Objects;

/**
 * Created by vpankrashkin on 18.09.16.
 */
public class AccountState {
    private final long ownAmount;
    private final long minAvailableAmount;
    private final long maxAvailableAmount;

    public AccountState() {
        this(0, 0, 0);
    }

    public AccountState(long ownAmount, long minAvailableAmount, long maxAvailableAmount) {
        this.ownAmount = ownAmount;
        this.minAvailableAmount = minAvailableAmount;
        this.maxAvailableAmount = maxAvailableAmount;
    }

    public long getOwnAmount() {
        return ownAmount;
    }

    public long getMaxAvailableAmount() {
        return maxAvailableAmount;
    }

    public long getMinAvailableAmount() {
        return minAvailableAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountState)) return false;
        AccountState that = (AccountState) o;
        return ownAmount == that.ownAmount &&
                maxAvailableAmount == that.maxAvailableAmount &&
                minAvailableAmount == that.minAvailableAmount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownAmount, maxAvailableAmount, minAvailableAmount);
    }
}
