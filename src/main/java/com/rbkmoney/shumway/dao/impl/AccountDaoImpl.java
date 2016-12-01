package com.rbkmoney.shumway.dao.impl;

import com.rbkmoney.shumway.dao.AccountDao;
import com.rbkmoney.shumway.dao.DaoException;
import com.rbkmoney.shumway.domain.Account;
import com.rbkmoney.shumway.domain.AccountLog;
import com.rbkmoney.shumway.domain.AccountState;
import com.rbkmoney.shumway.domain.Pair;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by vpankrashkin on 17.09.16.
 */
public class AccountDaoImpl  extends NamedParameterJdbcDaoSupport implements AccountDao {
    private final AccountMapper accountMapper = new AccountMapper();
    private final AmountStatePairMapper amountStatePairMapper = new AmountStatePairMapper();
    private static final int BATCH_SIZE = 1000;

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
        final String sql = "INSERT INTO shm.account_log(plan_id, batch_id, account_id, operation, own_amount, min_amount, max_amount, creation_time, credit, merged) VALUES (?, ?, ?, ?::shm.posting_operation_type, ?, ?, ?, ?, ?, ?)";
        int[][] updateCounts = getJdbcTemplate().batchUpdate(sql, logs, BATCH_SIZE,
                (ps, argument) -> {
                    ps.setLong(1, argument.getPlanId());
                    ps.setLong(2, argument.getBatchId());
                    ps.setLong(3, argument.getAccountId());
                    ps.setString(4, argument.getOperation().getKey());
                    ps.setLong(5, argument.getOwnAmount());
                    ps.setLong(6, argument.getMinAmount());
                    ps.setLong(7, argument.getMaxAmount());
                    ps.setTimestamp(8, Timestamp.from(argument.getCreationTime()));
                    ps.setBoolean(9, argument.isCredit());
                    ps.setBoolean(10, argument.isMerged());
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
        if (ids.isEmpty()) {
            return Collections.emptyList();
        } else {
            final String sql = "SELECT id, curr_sym_code, creation_time, description FROM shm.account WHERE id in ("+StringUtils.collectionToDelimitedString(ids, ",")+")";
            try {
                return getJdbcTemplate().query(sql, accountMapper);
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
    }

    @Override
    public List<Account> getExclusive(Collection<Long> ids) throws DaoException {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        } else {
            final String sql = "SELECT id, curr_sym_code, creation_time, description FROM shm.account WHERE id in ("+StringUtils.collectionToDelimitedString(ids, ",")+") FOR UPDATE ";
            try {
                return getJdbcTemplate().query(sql, accountMapper);
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
    }

    @Override
    public List<Account> getShared(Collection<Long> ids) throws DaoException {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        } else {
            final String sql = "SELECT id, curr_sym_code, creation_time, description FROM shm.account WHERE id in ("+StringUtils.collectionToDelimitedString(ids, ",")+") FOR SHARE ";
            try {
                return getJdbcTemplate().query(sql, accountMapper);
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
    }

    @Override
    public AccountState getAccountState(long accountId) throws DaoException {
        final String sql = "select \n" +
                "  account_id, \n" +
                "  sum(own_amount) as total_own_amount,  \n" +
                "  sum(min_amount) as total_min_amount, \n" +
                "  sum(max_amount) as total_max_amount\n" +
                "from shm.account_log \n" +
                "where account_id = :account_id  \n" +
                "group by account_id";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("account_id", accountId);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, amountStatePairMapper).getValue();
        } catch (EmptyResultDataAccessException e) {
            return new AccountState();
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public Map<Long, AccountState> getAccountStates(List<Long> accountIds) throws DaoException {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        } else {
            final String sql = "select \n" +
                    "  account_id, \n" +
                    "  sum(own_amount) as total_own_amount,  \n" +
                    "  sum(min_amount) as total_min_amount, \n" +
                    "  sum(max_amount) as total_max_amount\n" +
                    "from shm.account_log \n" +
                    "where account_id in (" + StringUtils.collectionToDelimitedString(accountIds, ",") + ")\n" +
                    "group by account_id";
            try {
               return fillAbsentValues(accountIds, getJdbcTemplate().query(sql, amountStatePairMapper).stream().collect(Collectors.toMap(pair -> pair.getKey(), pair -> pair.getValue())));
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
    }

    @Override
    public Map<Long, AccountState> getAccountStatesUpTo(List<Long> accountIds, long planId) throws DaoException {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        } else {
            MapSqlParameterSource params = new MapSqlParameterSource("plan_id", planId);
            final String sql = "select \n" +
                    "  account_id, \n" +
                    "  sum(own_amount) as total_own_amount,  \n" +
                    "  sum(min_amount) as total_min_amount, \n" +
                    "  sum(max_amount) as total_max_amount\n" +
                    "from shm.account_log \n" +
                    "where \n" +
                    "  account_id in (" + StringUtils.collectionToDelimitedString(accountIds, ",") + ") \n" +
                    "  and plan_id <= :plan_id \n" +
                    "group by account_id\n";
            try {
                return fillAbsentValues(accountIds, getNamedParameterJdbcTemplate().query(sql, params, amountStatePairMapper).stream().collect(Collectors.toMap(pair -> pair.getKey(), pair -> pair.getValue())));
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
    }

    @Override
    public Map<Long, AccountState> getAccountStatesUpTo(List<Long> accountIds, long planId, long batchId) throws DaoException {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        } else {
            MapSqlParameterSource params = new MapSqlParameterSource("plan_id", planId);
            params.addValue("batch_id", batchId);
            final String sql = "select \n" +
                    "  account_id, \n" +
                    "  sum(own_amount) as total_own_amount,  \n" +
                    "  sum(min_amount) as total_min_amount, \n" +
                    "  sum(max_amount) as total_max_amount\n" +
                    "from shm.account_log \n" +
                    "where \n" +
                    "  account_id in (" + StringUtils.collectionToDelimitedString(accountIds, ",") + ") \n" +
                    "  and plan_id < :plan_id or (plan_id = :plan_id and batch_id <= :batch_id) \n" +
                    "group by account_id\n";
            try {
                return fillAbsentValues(accountIds, getNamedParameterJdbcTemplate().query(sql, params, amountStatePairMapper).stream().collect(Collectors.toMap(pair -> pair.getKey(), pair -> pair.getValue())));
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
    }

    private Map<Long, AccountState> fillAbsentValues(List<Long> accountIds, Map<Long, AccountState> stateMap) {
        accountIds.stream().forEach(id -> stateMap.putIfAbsent(id, new AccountState()));
        return stateMap;
    }

    private static class AmountStatePairMapper implements RowMapper<Pair<Long, AccountState>> {
        @Override
        public Pair<Long, AccountState> mapRow(ResultSet rs, int rowNum) throws SQLException {
            long accountId = rs.getLong("account_id");
            long ownAmount = rs.getLong("total_own_amount");
            long minAvailableAmount = rs.getLong("total_min_amount");
            long maxAvailableAmount = rs.getLong("total_max_amount");
            AccountState accountState = new AccountState(ownAmount, minAvailableAmount, maxAvailableAmount);
            return new Pair<>(accountId, accountState);
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

