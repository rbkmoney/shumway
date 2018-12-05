package com.rbkmoney.shumway.config;

import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.dao.impl.AccountDaoImplNew;
import com.rbkmoney.shumway.dao.impl.PostingPlanDaoImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Created by vpankrashkin on 30.06.16.
 */
@Configuration
public class DaoConfiguration {

    @Bean(name = "accountDao")
    public AccountDao accountDao(DataSource dataSource) {
        return new AccountDaoImplNew(dataSource);
    }

    @Bean(name = "postingPlanDao")
    public PostingPlanDao postingPlanDao(DataSource dataSource) {
        return new PostingPlanDaoImpl(dataSource);
    }

}
