package com.rbkmoney.shumway.config;

import com.rbkmoney.damsel.shumpune.AccounterSrv;
import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.handler.AccounterHandler;
import com.rbkmoney.shumway.handler.ShumpuneServiceHandler;
import com.rbkmoney.shumway.service.AccountService;
import com.rbkmoney.shumway.service.PostingPlanService;
import com.rbkmoney.woody.thrift.impl.http.THSpawnClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

/**
 * Created by vpankrashkin on 20.09.16.
 */
@Configuration
public class AppConfiguration {

    @Value("${db.jdbc.tr_timeout}")
    private int transactionTimeout;

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transactionTemplate.setTimeout(transactionTimeout);
        return transactionTemplate;
    }

    @Bean
    public AccountService accountService(AccountDao accountDao) {
        return new AccountService(accountDao);
    }

    @Bean
    PostingPlanService postingPlanService(PostingPlanDao postingPlanDao) {
        return new PostingPlanService(postingPlanDao);
    }

    @Bean
    AccounterHandler accounterHandler(AccountService accountService, PostingPlanService postingPlanService, TransactionTemplate transactionTemplate) {
        return new AccounterHandler(accountService, postingPlanService, transactionTemplate);
    }

    @Bean
    ShumpuneServiceHandler shumpuneServiceHandler(AccounterHandler accounterHandler) {
        return new ShumpuneServiceHandler(accounterHandler);
    }

    @Bean
    public com.rbkmoney.damsel.shumaich.AccounterSrv.Iface shumaichClient(
            @Value("${service.shumaich.url}") Resource resource,
            @Value("${service.shumaich.networkTimeout}") int networkTimeout
    ) throws IOException {
        return new THSpawnClientBuilder()
                .withAddress(resource.getURI())
                .withNetworkTimeout(networkTimeout)
                .build(com.rbkmoney.damsel.shumaich.AccounterSrv.Iface.class);
    }

}
