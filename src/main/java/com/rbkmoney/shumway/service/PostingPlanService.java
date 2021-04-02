package com.rbkmoney.shumway.service;

import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.PostingPlanLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class PostingPlanService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private PostingPlanDao postingPlanDao;

    public PostingPlanService(PostingPlanDao postingPlanDao) {
        this.postingPlanDao = postingPlanDao;
    }

    public Map<Long, List<PostingLog>> getPostingLogs(String planId, PostingOperation operation) {
        log.debug("Get posting logs for plan: {}, op: {}", planId, operation);
        Map<Long, List<PostingLog>> result = postingPlanDao.getPostingLogs(planId, operation);
        log.debug("Got posting logs for {} batches", result.size());
        return result;
    }

    public PostingPlanLog getSharedPostingPlan(String planId) {
        log.debug("Get shared plan: {}", planId);
        PostingPlanLog result = postingPlanDao.getSharedPlanLog(planId);
        log.debug("Got shared plan: {}", result);
        return result;
    }

    /**
     * @return Entry, contains old plan as a key and new/updated plan as a value
     */
    public Map.Entry<PostingPlanLog, PostingPlanLog> updatePostingPlan(
            PostingPlanLog planLog,
            PostingOperation overridableOperation
    ) {
        log.debug("Get exclusive plan log: {}", planLog.getPlanId());
        PostingPlanLog oldPlanLog = postingPlanDao.getExclusivePlanLog(planLog.getPlanId());
        log.debug("Update plan log: {}, override op: {}", planLog.getPlanId(), overridableOperation);
        PostingPlanLog newPlanLog = postingPlanDao.updatePlanLog(planLog, overridableOperation);
        log.debug("Updated plan log: {}", newPlanLog);
        return new AbstractMap.SimpleEntry<>(oldPlanLog, newPlanLog);
    }

    /**
     * @return Entry, contains old plan as a key and new/updated plan as a value
     */
    public Map.Entry<PostingPlanLog, PostingPlanLog> createOrUpdatePostingPlan(PostingPlanLog planLog) {
        log.debug("Get exclusive plan log: {}", planLog.getPlanId());
        PostingPlanLog oldPlanLog = postingPlanDao.getExclusivePlanLog(planLog.getPlanId());
        log.debug("Add or update plan log: {}", planLog.getPlanId());
        PostingPlanLog newPlanLog = postingPlanDao.addOrUpdatePlanLog(planLog);
        log.debug("Result plan log: {}", newPlanLog);
        return new AbstractMap.SimpleEntry<>(oldPlanLog, newPlanLog);
    }

    public void addPostingLogs(List<PostingLog> postingLogs) {
        log.debug("Add posting logs: {}", postingLogs);
        postingPlanDao.addPostingLogs(postingLogs);
        log.debug("Added posting logs: {}", postingLogs.size());
    }


}
