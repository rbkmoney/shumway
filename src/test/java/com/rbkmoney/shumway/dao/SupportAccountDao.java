package com.rbkmoney.shumway.dao;

import com.rbkmoney.shumway.domain.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedRuntimeException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 24.11.16.
 */
@Component
public class SupportAccountDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    public SupportAccountDao(DataSource ds) {
        setDataSource(ds);
    }

    public List<Long> add(Account prototype, int numberOfAccs) throws DaoException {
        final int BATCH_SISE = 10000;
        final List<Long> ids = new ArrayList<>();
        if(numberOfAccs >= BATCH_SISE){
            for(int i=0; i < numberOfAccs/BATCH_SISE; i++){
                ids.addAll(addBatch(prototype, BATCH_SISE));
            }
        }
        if(numberOfAccs % BATCH_SISE != 0){
            ids.addAll(addBatch(prototype, numberOfAccs % BATCH_SISE ));
        }

        return ids;
    }

    private List<Long> addBatch(Account prototype, int numberOfAccs) throws DaoException {
        List<String> insertValues = new ArrayList<>();
        for(int i=0; i < numberOfAccs; i++){
            insertValues.add("(:curr_sym_code, :creation_time, :description)");
        }
        final String sql =
                "INSERT INTO shm.account(curr_sym_code, creation_time, description) " +
                        "VALUES " + String.join(", ", insertValues) +
                        " RETURNING id;";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("curr_sym_code", prototype.getCurrSymCode());
        params.addValue("creation_time", Timestamp.from(prototype.getCreationTime()));
        params.addValue("description", prototype.getDescription());
        try {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            int updateCount = getNamedParameterJdbcTemplate().update(sql, params, keyHolder);
            if (updateCount != numberOfAccs) {
                throw new DaoException("Accounts creation returned unexpected update count: " + updateCount);
            }
            return keyHolder.getKeyList().stream().map(m -> (Long)m.get("id")).collect(Collectors.toList());
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }
}
