package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.shumpune.Account;
import com.rbkmoney.damsel.shumpune.AccountPrototype;
import com.rbkmoney.damsel.shumpune.AccounterSrv;
import com.rbkmoney.damsel.shumpune.Balance;
import com.rbkmoney.damsel.shumpune.Clock;
import com.rbkmoney.damsel.shumpune.LatestClock;
import com.rbkmoney.damsel.shumpune.PostingPlan;
import com.rbkmoney.damsel.shumpune.PostingPlanChange;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShumpuneServiceHandler implements AccounterSrv.Iface {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final AccounterHandler accounterHandler;

    public ShumpuneServiceHandler(AccounterHandler accounterHandler) {
        this.accounterHandler = accounterHandler;
    }

    @Override
    public Clock hold(PostingPlanChange postingPlanChange) throws TException {
        log.info("Shumpune in shumway hold plan: {}", postingPlanChange);
        var planChange = ShumpuneProtocolConverter.convertToOldPostingPlanChange(postingPlanChange);
        accounterHandler.hold(planChange);
        return Clock.latest(new LatestClock());
    }

    @Override
    public Clock commitPlan(PostingPlan plan) throws TException {
        log.info("Shumpune in shumway commitPlan plan: {}", plan);
        accounterHandler.commitPlan(ShumpuneProtocolConverter.convertToOldPostingPlan(plan));
        return Clock.latest(new LatestClock());
    }

    @Override
    public Clock rollbackPlan(PostingPlan plan) throws TException {
        log.info("Shumpune in shumway rollbackPlan plan: {}", plan);
        accounterHandler.rollbackPlan(ShumpuneProtocolConverter.convertToOldPostingPlan(plan));
        return Clock.latest(new LatestClock());
    }

    @Override
    public PostingPlan getPlan(String id) throws TException {
        log.info("Shumpune in shumway getPlan  id: {}", id);
        var plan = accounterHandler.getPlan(id);
        return ShumpuneProtocolConverter.convertToNewPostingPlan(plan);
    }

    @Override
    public Account getAccountByID(long id) throws TException {
        log.info("Shumpune in shumway GetAccountById id: {}", id);
        var accountByID = accounterHandler.getAccountByID(id);
        return ShumpuneProtocolConverter.convertToNewAccount(accountByID);
    }

    @Override
    public Balance getBalanceByID(long id, Clock clock) throws TException {
        log.info("Shumpune in shumway getBalanceByID id: {}", id);
        var accountByID = accounterHandler.getAccountByID(id);
        return ShumpuneProtocolConverter.convertToNewBalance(accountByID);
    }

    @Override
    public long createAccount(AccountPrototype prototype) throws TException {
        log.info("Shumpune in shumway createAccount prototype: {}", prototype);
        var accountPrototype = ShumpuneProtocolConverter.convertToOldAccountPrototype(prototype);
        return accounterHandler.createAccount(accountPrototype);
    }
}
