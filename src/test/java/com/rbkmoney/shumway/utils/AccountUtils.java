package com.rbkmoney.shumway.utils;

import com.rbkmoney.damsel.accounter.*;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.SupportAccountDao;
import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.AccountState;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;

@Slf4j
public class AccountUtils {
    private static final long RETRY_TIMEOUT = 5000;

    public static List<Long> createAccs(int N, SupportAccountDao supportAccountDao) {
        long startTime = System.currentTimeMillis();
        Account domainPrototype =  new Account(0, Instant.now(), "RUB", "Test");
        List<Long> ids = supportAccountDao.add(domainPrototype, N);
        log.warn("CreateAccs({}) execution time: {}ms", N, (System.currentTimeMillis() - startTime));
        return ids;
    }

    public static void startCircleCheck(AccounterSrv.Iface client, List<Long> accs, long expectedAmount){
        long startTime = System.currentTimeMillis();
        for(long accId: accs){
            com.rbkmoney.damsel.accounter.Account acc = retry(() -> client.getAccountByID(accId));
            assertEquals("Acc ID: " + acc.getId(), expectedAmount, acc.getOwnAmount());
            assertEquals("Acc ID: " + acc.getId(), expectedAmount, acc.getMaxAvailableAmount());
            assertEquals("Acc ID: " + acc.getId(), expectedAmount, acc.getMinAvailableAmount());
        }
        log.warn("Check amounts on accs: {}ms", (System.currentTimeMillis() - startTime));
    }

    public static void startCircleCheck(AccountDao accountDao, List<Long> accs, long expectedAmount){
        long startTime = System.currentTimeMillis();

        Map<Long, AccountState> states =  accountDao.getAccountStates(accs);
        for(Map.Entry<Long, AccountState> e: states.entrySet()){
            assertEquals("Acc ID: " + e.getKey(), expectedAmount, e.getValue().getOwnAmount());
            assertEquals("Acc ID: " + e.getKey(), expectedAmount, e.getValue().getMaxAvailableAmount());
            assertEquals("Acc ID: " + e.getKey(), expectedAmount, e.getValue().getMinAvailableAmount());
        }
        log.warn("Check amounts on accs: {}ms", (System.currentTimeMillis() - startTime));
    }

    public static double startCircleTransfer(AccounterSrv.Iface client, List<Long> accs,
                                           int numberOfThreads,
                                           int sizeOfQueue,
                                           long amount) throws InterruptedException {
        return startCircleTransfer(client, accs, numberOfThreads, sizeOfQueue, amount, 1);
    }

    // returns time(ms) per one committed transfer
    public static double startCircleTransfer(AccounterSrv.Iface client, List<Long> accs,
                                           int numberOfThreads,
                                           int sizeOfQueue,
                                           long amount,
                                           int numberOfRounds) throws InterruptedException {
        final ExecutorService executorService = newFixedThreadPoolWithQueueSize(numberOfThreads, sizeOfQueue);
        long startTime = System.currentTimeMillis();
        int transferCounter = 0;
        for(int j=0; j < numberOfRounds; j++) {
            for (int i = 0; i < accs.size(); i++) {
                final long from = accs.get(i);
                final long to = accs.get((i + 1) % accs.size());
                final int transferId = transferCounter++;

                executorService.submit(() -> makeTransfer(client, from, to, amount, startTime + "_"+ transferId));
            }
        }
        log.warn("All transactions submitted.");

        executorService.shutdown();
        boolean success = executorService.awaitTermination(sizeOfQueue, TimeUnit.SECONDS);
        if(!success){
            log.error("Waiting was terminated by timeout");
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log.warn("Transactions execution time from start: {}ms", totalTime);
        return ((double) totalTime) / (numberOfRounds * accs.size());
    }

    // returns time(ms) per one committed transfer (p->r->m->p)
    public static double emulateRealTransfer(AccounterSrv.Iface client,  List<Long> providerAccs, List<Long> rbkMoneyAccs, List<Long> merchantAccs,
                                             int numberOfRounds,
                                             int numberOfThreads,
                                             int sizeOfQueue) throws InterruptedException {
        final ExecutorService executorService = newFixedThreadPoolWithQueueSize(numberOfThreads, sizeOfQueue);
        long startTime = System.currentTimeMillis();

        final Random random = new Random();

        for(int j=0; j < numberOfRounds; j++) {
            final long provider = providerAccs.get(random.nextInt(providerAccs.size()));
            final long rbkMoney = rbkMoneyAccs.get(random.nextInt(rbkMoneyAccs.size()));
            final long merchant = merchantAccs.get(random.nextInt(merchantAccs.size()));

            final int transferId = j;

                executorService.submit(() -> makeRealTransfer(client, provider, merchant, rbkMoney, transferId));
        }
        log.info("All transactions submitted.");

        executorService.shutdown();
        boolean success = executorService.awaitTermination(sizeOfQueue, TimeUnit.SECONDS);
        if(!success){
            log.error("Waiting was terminated by timeout");
        }
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Transfer (p->r->m->p) execution time from start: {}ms", totalTime);
        return ((double) totalTime) / numberOfRounds;
    }

    // transferId - unique id, used for PostingPlan id
    public static void makeTransfer(AccounterSrv.Iface client, long from, long to, long amount, String transferId){
        final String ppid = transferId;
        final PostingPlan postingPlan = createNewPostingPlan(ppid, from, to, amount);
        makeTransfer(client, postingPlan);
    }

    public static void makeRealTransfer(AccounterSrv.Iface client, long provider, long merchant, long rbkMoney, int transferId){
        final String ppid = System.currentTimeMillis() + "_" + transferId;
        Posting p1 = new Posting(provider, rbkMoney, 100, "RUB", "Desc");
        Posting p2 = new Posting(rbkMoney, merchant, 95, "RUB", "Desc");
        Posting p3 = new Posting(merchant, provider, 1, "RUB", "Desc");
        PostingPlan postingPlan = new PostingPlan(ppid, Arrays.asList(new PostingBatch(1, Arrays.asList(p1, p2, p3))));

        makeTransfer(client, postingPlan);
    }

    private static void makeTransfer(AccounterSrv.Iface client, PostingPlan postingPlan){
        postingPlan.getBatchList().stream().forEach(batch ->
                retry(() -> client.hold(new PostingPlanChange(postingPlan.getId(), batch))));

        retry(() -> client.commitPlan(postingPlan));
    }

    public static PostingPlan createNewPostingPlan(String ppid, long accFrom, long accTo, long amount){
        Posting posting = new Posting(accFrom, accTo, amount, "RUB", "Desc");
        return new PostingPlan(ppid, Arrays.asList(new PostingBatch(1, Arrays.asList(posting))));
    }

    private static <T> T retry(Callable<T> a){
        return new SimpleRetrier<>(a, RETRY_TIMEOUT).retry();
    }

    private static class SimpleRetrier<T>{
        private final Logger log = LoggerFactory.getLogger(this.getClass());
        private Callable<T> action;
        private long timeout;

        SimpleRetrier(Callable<T> action, long timeout) {
            this.action = action;
            this.timeout = timeout;
        }

        T retry(){
            while (true){
                try {
                    return action.call();
                }catch (Exception e){
                    log.error("Exception during doing some retryable actions. Wait {} ms and execute.", timeout);
                    log.error("Description: ",e);
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException ignored) {
                        log.warn("Interrupted during waiting for execute.");
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private static ExecutorService newFixedThreadPoolWithQueueSize(int nThreads, int queueSize) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize, true), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Deprecated
    // use SupportAccountDao to  keep you time
    public static List<Long> createAccs(int N, AccounterSrv.Iface client) throws TException {
        ArrayList<Long> accs = new ArrayList<>();
        for(int i=0; i < N; i++){
            accs.add(createAcc(client));
        }

        return accs;
    }

    @Deprecated
    // use SupportAccountDao to  keep you time
    public static long createAcc(AccounterSrv.Iface client) throws TException {
        AccountPrototype prototype = new AccountPrototype("RUB");
        prototype.setDescription("Test");
        return client.createAccount(prototype);
    }
}
