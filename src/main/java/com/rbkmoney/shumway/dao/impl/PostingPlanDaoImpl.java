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
import java.util.List;

/**
 * Created by vpankrashkin on 18.09.16.
 */
public class PostingPlanDaoImpl extends NamedParameterJdbcDaoSupport implements PostingPlanDao {
    private final PostingPlanLogMapper planRowMapper = new PostingPlanLogMapper();
    private final PostingLogMapper postingRowMapper = new PostingLogMapper();

    public PostingPlanDaoImpl(DataSource ds) {
        setDataSource(ds);
    }

    @Override
    public PostingPlanLog addOrUpdatePlanLog(PostingPlanLog planLog) throws DaoException {
        final  String sql = "insert into shm.plan_log (plan_id, last_access_time, last_operation) values (:plan_id, :last_access_time, :last_operation::shm.posting_operation_type) on conflict (plan_id) do update set last_access_time=:last_access_time, last_operation=:last_operation::shm.posting_operation_type, last_request_id=shm.plan_log.last_request_id+1 where shm.plan_log.last_operation=:overridable_operation::shm.posting_operation_type returning *";
        MapSqlParameterSource params = createParams(planLog, PostingOperation.HOLD);
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, planRowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw  new DaoException(e);
        }
    }

    @Override
    public PostingPlanLog updatePlanLog(PostingPlanLog planLog, PostingOperation postingOperation) throws DaoException {
        final String sql = "update shm.plan_log set last_access_time=:last_access_time, last_operation=:last_operation::shm.posting_operation_type, last_request_id=shm.plan_log.last_request_id+1  where plan_id=:plan_id and shm.plan_log.last_operation in (:overridable_operation::shm.posting_operation_type, :overridable_operation2::shm.posting_operation_type) returning *";
        MapSqlParameterSource params = createParams(planLog, PostingOperation.HOLD);
        params.addValue("overridable_operation2", postingOperation.getKey());
        try {
            return getNamedParameterJdbcTemplate().queryForObject(sql, params, planRowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw  new DaoException(e);
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
            throw  new DaoException(e);
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
            throw  new DaoException(e);
        }
    }

    @Override
    public List<PostingLog> getPostingLogs(String planId, PostingOperation operation) throws DaoException {
        final String sql = "select * from shm.posting_log where plan_id = :plan_id and operation = :operation::shm.posting_operation_type";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("plan_id", planId);
        params.addValue("operation", operation.getKey());
        try {
            return getNamedParameterJdbcTemplate().query(sql, params, postingRowMapper);
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public void addPostingLogs(List<PostingLog> postingLogs) throws DaoException {
        final String sql = "INSERT INTO shm.posting_log(plan_id, posting_id, request_id, from_account_id, to_account_id, creation_time, amount, curr_sym_code, operation, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::shm.posting_operation_type, ?)";
        int[][] updateCounts = getJdbcTemplate().batchUpdate(sql, postingLogs, postingLogs.size(),
                (ps, argument) -> {
                    ps.setString(1, argument.getPlanId());
                    ps.setLong(2, argument.getPostingId());
                    ps.setLong(3, argument.getRequestId());
                    ps.setLong(4, argument.getFromAccountId());
                    ps.setLong(5, argument.getToAccountId());
                    ps.setTimestamp(6, Timestamp.from(argument.getCreationTime()));
                    ps.setLong(7, argument.getAmount());
                    ps.setString(8, argument.getCurrSymCode());
                    ps.setString(9, argument.getOperation().getKey());
                    ps.setString(10, argument.getDescription());
                });
        boolean checked = false;
        for (int i = 0; i < updateCounts.length; ++i) {
            for (int j = 0; j < updateCounts[i].length; ++j) {
                checked = true;
                if (updateCounts[i][j] != 1) {
                    throw new DaoException("Posting log creation returned unexpected update count: "+updateCounts[i][j]);
                }
            }
        }
        if (!checked) {
            throw new DaoException("Posting log creation returned unexpected update count [0]");
        }
    }

    private MapSqlParameterSource createParams(PostingPlanLog planLog, PostingOperation overridableOperation) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("plan_id", planLog.getPlanId());
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
            long lastRequestId = rs.getLong("last_request_id");
            return new PostingPlanLog(planId, lastAccessTime, lastOperation, lastRequestId);
        }
    }

    private static class PostingLogMapper implements RowMapper<PostingLog> {

        @Override
        public PostingLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            long id = rs.getLong("id");
            String planId = rs.getString("plan_id");
            long postingId = rs.getLong("posting_id");
            long requestId = rs.getLong("request_id");
            long fromAccountId = rs.getLong("from_account_id");
            long toAccountId = rs.getLong("to_account_id");
            Instant creationTime = rs.getTimestamp("creation_time").toInstant();
            long amount = rs.getLong("amount");
            String currSymCode = rs.getString("curr_sym_code");
            PostingOperation operation = PostingOperation.getValueByKey(rs.getString("operation"));
            String description = rs.getString("description");
            return new PostingLog(id, planId, postingId, requestId, fromAccountId, toAccountId, amount, creationTime, operation, currSymCode, description);
        }
    }
}
