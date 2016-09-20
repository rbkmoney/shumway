package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 17.09.16.
 */
public class StatefulAccount extends Account {
    private AmountState amountState;

    public StatefulAccount(long id, Instant creationTime, String currSymCode, String description, AmountState amountState) {
        super(id, creationTime, currSymCode, description);
        this.amountState = amountState;
    }

    public StatefulAccount(Account prototype, AmountState amountState) {
        super(prototype);
        this.amountState = amountState;
    }

    public AmountState getAmountState() {
        return amountState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatefulAccount)) return false;
        if (!super.equals(o)) return false;
        StatefulAccount that = (StatefulAccount) o;
        return Objects.equals(amountState, that.amountState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), amountState);
    }

    @Override
    public String toString() {
        return "StatefulAccount{" +
                "amountState=" + amountState +
                "} " + super.toString();
    }
}
