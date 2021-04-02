package com.rbkmoney.shumway.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public class Account {
    private final long id;
    private final Instant creationTime;
    private final String currSymCode;
    private final String description;

    public Account(long id, Instant creationTime, String currSymCode, String description) {
        this.id = id;
        this.creationTime = creationTime;
        this.currSymCode = currSymCode;
        this.description = description;
    }

    public Account(Account prototype) {
        this(prototype.getId(), prototype.getCreationTime(), prototype.getCurrSymCode(), prototype.getDescription());
    }

    public long getId() {
        return id;
    }

    public Instant getCreationTime() {
        return creationTime;
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
        if (!(o instanceof Account)) {
            return false;
        }
        Account account = (Account) o;
        return id == account.id
                && Objects.equals(creationTime, account.creationTime)
                && Objects.equals(currSymCode, account.currSymCode)
                && Objects.equals(description, account.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, creationTime, currSymCode, description);
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", creationTime=" + creationTime +
                ", currSymCode='" + currSymCode + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
