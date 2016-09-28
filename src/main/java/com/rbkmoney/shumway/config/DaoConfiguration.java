package com.rbkmoney.shumway.config;

import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.dao.impl.AccountDaoImpl;
import com.rbkmoney.shumway.dao.impl.PostingPlanDaoImpl;
import org.jooq.Schema;
import org.jooq.impl.SchemaImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

/**
 * Created by vpankrashkin on 30.06.16.
 */
@Configuration
public class DaoConfiguration {

    @Bean(name = "accountDao")
    @DependsOn("dbInitializer")
    public AccountDao accountDao(DataSource dataSource) {
        return new AccountDaoImpl(dataSource);
    }

    @Bean(name = "postingPlanDao")
    @DependsOn("dbInitializer")
    public PostingPlanDao postingPlanDao(DataSource dataSource) {
        return new PostingPlanDaoImpl(dataSource);
    }


    @Bean
    public Schema dbSchema() {
        return new SchemaImpl("shm");
    }

}
