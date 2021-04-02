package com.rbkmoney.shumway.dao.impl;

import com.rbkmoney.shumway.dao.DaoException;
import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.PostingPlanLog;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;

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
 * Created by vpankrashkin on 18.09.16.
 */
public class PostingPlanDaoImpl extends NamedParameterJdbcDaoSupport implements PostingPlanDao {
    private static final int BATCH_SIZE = 1000;

    private final PostingPlanLogMapper planRowMapper = new PostingPlanLogMapper();
    private final PostingLogMapper postingRowMapper = new PostingLogMapper();

    public PostingPlanDaoImpl(DataSource ds) {
        setDataSource(ds);
    }

    @Override
    public PostingPlanLog addOrUpdatePlanLog(PostingPlanLog planLog) throws DaoException {
        final String sql = "insert into shm.plan_log (plan_id, last_batch_id, last_access_time, last_operation) " +
                "values (:plan_id, :last_batch_id, :last_access_time, :last_operation::shm.posting_operation_type) " +
                "on conflict (plan_id) do update " +
                "set last_access_time=:last_access_time, " +
                "last_operation=:last_operation::shm.posting_operation_type, " +
                "last_batch_id=:last_batch_id " +
                "where shm.plan_log.last_operation=:overridable_operation::shm.posting_operation_type returning *";
        MapSqlParameterSource params = createParams(planLog, PostingOperation.HOLD);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, planRowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public PostingPlanLog updatePlanLog(PostingPlanLog planLog, PostingOperation postingOperation) throws DaoException {
        final String sql = "update shm.plan_log " +
                "set last_access_time=:last_access_time, " +
                "last_operation=:last_operation::shm.posting_operation_type, " +
                "last_batch_id=:last_batch_id " +
                "where plan_id=:plan_id " +
                "and shm.plan_log.last_operation in (" +
                ":overridable_operation::shm.posting_operation_type, " +
                ":same_operation::shm.posting_operation_type" +
                ") returning *";
        MapSqlParameterSource params = createParams(planLog, PostingOperation.HOLD);
        params.addValue("same_operation", postingOperation.getKey());
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, planRowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public PostingPlanLog getExclusivePlanLog(String planId) throws DaoException {
        final String sql = "select * from shm.plan_log where plan_id=:plan_id for update";
        MapSqlParameterSource params = new MapSqlParameterSource("plan_id", planId);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, planRowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public PostingPlanLog getSharedPlanLog(String planId) throws DaoException {
        final String sql = "select * from shm.plan_log where plan_id=:plan_id for share";
        MapSqlParameterSource params = new MapSqlParameterSource("plan_id", planId);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, planRowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public Map<Long, List<PostingLog>> getPostingLogs(String planId, PostingOperation operation) throws DaoException {
        final String sql = "select * from shm.posting_log " +
                "where plan_id = :plan_id and operation = :operation::shm.posting_operation_type";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("plan_id", planId);
        params.addValue("operation", operation.getKey());
        try {
            return getNamedParameterJdbcTemplate().query(sql, params, postingRowMapper).stream()
                    .collect(Collectors.groupingBy(postingLog -> postingLog.getBatchId()));
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public void addPostingLogs(List<PostingLog> postingLogs) throws DaoException {
        final String sql = "INSERT INTO shm.posting_log(plan_id, batch_id, from_account_id, to_account_id, " +
                "creation_time, amount, curr_sym_code, operation, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::shm.posting_operation_type, ?)";
        int[][] updateCounts = getJdbcTemplate().batchUpdate(sql, postingLogs, BATCH_SIZE,
                (ps, argument) -> {
                    ps.setString(1, argument.getPlanId());
                    ps.setLong(2, argument.getBatchId());
                    ps.setLong(3, argument.getFromAccountId());
                    ps.setLong(4, argument.getToAccountId());
                    ps.setTimestamp(5, Timestamp.from(argument.getCreationTime()));
                    ps.setLong(6, argument.getAmount());
                    ps.setString(7, argument.getCurrSymCode());
                    ps.setString(8, argument.getOperation().getKey());
                    ps.setString(9, argument.getDescription());
                });
        boolean checked = false;
        for (int i = 0; i < updateCounts.length; ++i) {
            for (int j = 0; j < updateCounts[i].length; ++j) {
                checked = true;
                if (updateCounts[i][j] != 1) {
                    throw new DaoException(
                            "Posting log creation returned unexpected update count: " + updateCounts[i][j]);
                }
            }
        }
        if (!checked) {
            throw new DaoException("Posting log creation returned unexpected update count [0]");
        }
    }

    private Map<Long, List<PostingLog>> fillAbsentValues(Collection<Long> batchIds,
                                                         Map<Long, List<PostingLog>> stateMap) {
        batchIds.stream().forEach(id -> stateMap.putIfAbsent(id, Collections.emptyList()));
        return stateMap;
    }

    private MapSqlParameterSource createParams(PostingPlanLog planLog, PostingOperation overridableOperation) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("plan_id", planLog.getPlanId());
        params.addValue("last_batch_id", planLog.getLastBatchId());
        params.addValue("last_access_time", Timestamp.from(planLog.getLastAccessTime()));
        params.addValue("last_operation", planLog.getLastOperation().getKey());
        params.addValue("overridable_operation", overridableOperation.getKey());
        return params;
    }

    private static class PostingPlanLogMapper implements RowMapper<PostingPlanLog> {

        @Override
        public PostingPlanLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            String planId = rs.getString("plan_id");
            Instant lastAccessTime = rs.getTimestamp("last_access_time").toInstant();
            PostingOperation lastOperation = PostingOperation.getValueByKey(rs.getString("last_operation"));
            long lastBatchId = rs.getLong("last_batch_id");
            return new PostingPlanLog(planId, lastAccessTime, lastOperation, lastBatchId);
        }
    }

    private static class PostingLogMapper implements RowMapper<PostingLog> {

        @Override
        public PostingLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            long id = rs.getLong("id");
            String planId = rs.getString("plan_id");
            long batchId = rs.getLong("batch_id");
            long fromAccountId = rs.getLong("from_account_id");
            long toAccountId = rs.getLong("to_account_id");
            Instant creationTime = rs.getTimestamp("creation_time").toInstant();
            long amount = rs.getLong("amount");
            String currSymCode = rs.getString("curr_sym_code");
            PostingOperation operation = PostingOperation.getValueByKey(rs.getString("operation"));
            String description = rs.getString("description");
            return new PostingLog(id, planId, batchId, fromAccountId, toAccountId, amount, creationTime, operation,
                    currSymCode, description);
        }
    }
}
