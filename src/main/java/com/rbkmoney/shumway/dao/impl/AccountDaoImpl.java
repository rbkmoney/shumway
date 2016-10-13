package com.rbkmoney.shumway.dao.impl;

import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.DaoException;
import com.rbkmoney.shumway.domain.*;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 17.09.16.
 */
public class AccountDaoImpl  extends NamedParameterJdbcDaoSupport implements AccountDao {
    private final AccountMapper accountMapper = new AccountMapper();
    private final AmountStatePairMapper amountStatePairMapper = new AmountStatePairMapper();

    public AccountDaoImpl(DataSource ds) {
        setDataSource(ds);
    }

    @Override
    public long add(Account prototype) throws DaoException {
        final String sql = "INSERT INTO shm.account(curr_sym_code, creation_time, description) VALUES (:curr_sym_code, :creation_time, :description) returning id;";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("curr_sym_code", prototype.getCurrSymCode());
        params.addValue("creation_time", Timestamp.from(prototype.getCreationTime()));
        params.addValue("description", prototype.getDescription());
        try {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            int updateCount = getNamedParameterJdbcTemplate().update(sql, params, keyHolder);
            if (updateCount != 1) {
                throw new DaoException("Account creation returned unexpected update count: "+updateCount);
            }
            return keyHolder.getKey().longValue();
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public void addLogs(List<AccountLog> logs) throws DaoException {
        final String sql = "INSERT INTO shm.account_log(plan_id, posting_id, request_id, account_id, creation_time, operation, amount, own_amount, available_amount, credit) VALUES (?, ?, ?, ?, ?, ?::shm.posting_operation_type, ?, ?, ?, ?)";
        int[][] updateCounts = getJdbcTemplate().batchUpdate(sql, logs, logs.size(),
                (ps, argument) -> {
                    ps.setString(1, argument.getPlanId());
                    ps.setLong(2, argument.getPostingId());
                    ps.setLong(3, argument.getRequestId());
                    ps.setLong(4, argument.getAccountId());
                    ps.setTimestamp(5, Timestamp.from(argument.getCreationTime()));
                    ps.setString(6, argument.getOperation().getKey());
                    ps.setLong(7, argument.getAmount());
                    ps.setLong(8, argument.getOwnAmount());
                    ps.setLong(9, argument.getAvailableAmount());
                    ps.setBoolean(10, argument.isCredit());
                });
        boolean checked = false;
        for (int i = 0; i < updateCounts.length; ++i) {
            for (int j = 0; j < updateCounts[i].length; ++j) {
                checked = true;
                if (updateCounts[i][j] != 1) {
                    throw new DaoException("Account log creation returned unexpected update count: "+updateCounts[i][j]);
                }
            }
        }
        if (!checked) {
            throw new DaoException("Account log creation returned unexpected update count [0]");
        }
    }

    @Override
    public Account get(long id) {
        final String sql = "SELECT id, curr_sym_code, creation_time, description FROM shm.account WHERE id=:id";
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, accountMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }

    }

    @Override
    public List<Account> get(Collection<Long> ids) throws DaoException {
        final String sql = "SELECT id, curr_sym_code, creation_time, description FROM shm.account WHERE " + (ids.isEmpty() ? "false" :  "id in ("+StringUtils.collectionToDelimitedString(ids, ",")+")");
        try {
            return getJdbcTemplate().query(sql, accountMapper);
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public AmountState getAmountState(long accountId) throws DaoException {
        final String sql = "select account_log.account_id, SUM(own_amount) as own_amount, SUM(available_amount) as available_amount from shm.account_log where account_id = :account_id group by account_id";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("account_id", accountId);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, amountStatePairMapper).getValue();
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public AmountState getAmountStateUpTo(long accountId, String planId) throws DaoException {
        final String sql = "select account_id, SUM(own_amount) as own_amount, SUM(available_amount) as available_amount from shm.account_log where account_id = :account_id and id <= (select max(id) from shm.account_log where plan_id =:plan_id) GROUP by account_id";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("account_id", accountId);
        params.addValue("plan_id", planId);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, amountStatePairMapper).getValue();
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public Map<Long, AmountState> getAmountStates(List<Long> accountIds) throws DaoException {

        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        } else {
            final String sql = "select account_id, SUM(own_amount) as own_amount, SUM(available_amount) as available_amount from shm.account_log where account_id in ("+ StringUtils.collectionToDelimitedString(accountIds, ",")+") group by account_id";
            try {
                return getJdbcTemplate().query(sql, amountStatePairMapper).stream().collect(Collectors.toMap(pair -> pair.getKey(), pair -> pair.getValue()));
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
    }

    @Override
    public Map<Long, AmountState> getAmountStatesUpTo(List<Long> accountIds, String planId) throws DaoException {
        //TODO rewrite this
        Map<Long, AmountState> stateMap = new HashMap<>();
        for (Long id: accountIds) {
            AmountState amountState = getAmountStateUpTo(id, planId);
            if (amountState != null) {
                stateMap.put(id, amountState);
            }
        }
        return stateMap;
    }

    private static class AmountStatePairMapper implements RowMapper<Pair<Long, AmountState>> {
        @Override
        public Pair<Long, AmountState> mapRow(ResultSet rs, int rowNum) throws SQLException {
            long ownAmount = rs.getLong("own_amount");
            long availableAmout = rs.getLong("available_amount");
            long accountId = rs.getLong("account_id");
            AmountState amountState = new AmountState(ownAmount, availableAmout);
            return new Pair<>(accountId, amountState);
        }
    }

    private static class AccountMapper implements RowMapper<Account> {
        @Override
        public Account mapRow(ResultSet rs, int i) throws SQLException {
            long id = rs.getLong("id");
            String currSymCode = rs.getString("curr_sym_code");
            Instant creationTime = rs.getTimestamp("creation_time").toInstant();
            String description = rs.getString("description");
            return new Account(id, creationTime, currSymCode, description);
        }
    }
}

