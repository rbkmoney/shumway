package com.rbkmoney.shumway.handler;

import com.rbkmoney.damsel.shumaich.*;
import com.rbkmoney.shumway.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShumaichServiceHandler implements AccounterSrv.Iface {

    private final AccounterSrv.Iface shumaichClient;
    private final ShumpuneServiceHandler shumpuneServiceHandler;
    private final AccountService accountService;

    @Override
    public Clock hold(PostingPlanChange postingPlanChange, Clock clock) throws TException {
        try {
            log.info("Shumaich in shumway hold method called with postingPlanChange: {}", postingPlanChange);
            Clock holdClock = shumaichClient.hold(postingPlanChange, clock);
            accountService.createAccountsIfDontExist(postingPlanChange);
            shumpuneServiceHandler.hold(ShumaichProtocolConverter.convertToOldPostingPlanChange(postingPlanChange));
            return holdClock;
        } catch (NotReady ex) {
            log.error("Shumpune is not ready yet", ex);
            throw ex;
        }
    }

    @Override
    public Clock commitPlan(PostingPlan postingPlan, Clock clock) throws TException {
        try {
            log.info("Shumaich in shumway commitPlan method called with postingPlan: {}", postingPlan);
            Clock commitClock = shumaichClient.commitPlan(postingPlan, clock);
            shumpuneServiceHandler.commitPlan(ShumaichProtocolConverter.convertToOldPostingPlan(postingPlan));
            return commitClock;
        } catch (NotReady ex) {
            throw new NotReady(ex);
        }
    }

    @Override
    public Clock rollbackPlan(PostingPlan postingPlan, Clock clock) throws TException {
        try {
            log.info("Shumaich in shumway rollbackPlan method called with postingPlan: {}", postingPlan);
            Clock rollbackClock = shumaichClient.rollbackPlan(postingPlan, clock);
            shumpuneServiceHandler.rollbackPlan(ShumaichProtocolConverter.convertToOldPostingPlan(postingPlan));
            return rollbackClock;
        } catch (NotReady ex) {
            throw new NotReady(ex);
        }
    }

    @Override
    public Balance getBalanceByID(long accountId, Clock clock) throws TException {
        try {
            log.info("Shumaich in shumway getBalanceByID method called with accountId: {}", accountId);
            return shumaichClient.getBalanceByID(accountId, clock);
        } catch (NotReady ex) {
            throw new NotReady(ex);
        }
    }

    @Override
    public Account getAccountByID(long accountId, Clock clock) throws TException {
        try {
            log.info("Shumaich in shumway getAccountByID method called with accountId: {}", accountId);
            return shumaichClient.getAccountByID(accountId, clock);
        } catch (NotReady ex) {
            throw new NotReady(ex);
        }
    }

}
