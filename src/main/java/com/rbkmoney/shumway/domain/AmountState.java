package com.rbkmoney.shumway.domain;

import java.util.Objects;

/**
 * Created by vpankrashkin on 18.09.16.
 */
public class AmountState {
    private long ownAmount;
    private long availableAmount;

    public AmountState(long ownAmount, long availableAmount) {
        this.ownAmount = ownAmount;
        this.availableAmount = availableAmount;
    }

    public long getOwnAmount() {
        return ownAmount;
    }

    public long getAvailableAmount() {
        return availableAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AmountState)) return false;
        AmountState that = (AmountState) o;
        return ownAmount == that.ownAmount &&
                availableAmount == that.availableAmount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownAmount, availableAmount);
    }

    @Override
    public String toString() {
        return "AmountState{" +
                "ownAmount=" + ownAmount +
                ", availableAmount=" + availableAmount +
                '}';
    }
}
