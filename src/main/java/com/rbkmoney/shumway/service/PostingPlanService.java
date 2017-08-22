package com.rbkmoney.shumway.service;

import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.PostingPlanLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.Collection;
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
        log.info("Get posting logs for plan: {}, op: {}", planId, operation);
        Map<Long, List<PostingLog>> result = postingPlanDao.getPostingLogs(planId, operation);
        log.info("Got posting logs for {} batches", result.size());
        return result;
    }

    /*public Map<Long, List<PostingLog>> getPostingLogs(String planId, Collection<Long> batchIds, PostingOperation operation) {
        return postingPlanDao.getPostingLogs(planId, batchIds, operation);
    }*/

    public PostingPlanLog getSharedPostingPlan(String planId) {
        log.info("Get shared plan: {}", planId);
        PostingPlanLog result = postingPlanDao.getSharedPlanLog(planId);
        log.info("Got shared plan: {}", result);
        return result;
    }

/*
    public PostingPlanLog getExclusivePostingPlan(String planId) {
        return postingPlanDao.getExclusivePlanLog(planId);
    }
*/

    /**
     * @return Entry, contains old plan as a key and new/updated plan as a value
     * */
    public Map.Entry<PostingPlanLog, PostingPlanLog>  updatePostingPlan(PostingPlanLog planLog, PostingOperation overridableOperation) {
        log.info("Get exclusive plan log: {}", planLog.getPlanId());
        PostingPlanLog oldPlanLog = postingPlanDao.getExclusivePlanLog(planLog.getPlanId());
        log.info("Update plan log: {}, override op: {}", planLog.getPlanId(), overridableOperation);
        PostingPlanLog newPlanLog = postingPlanDao.updatePlanLog(planLog, overridableOperation);
        log.info("Updated plan log: {}", newPlanLog);
        return new AbstractMap.SimpleEntry<>(oldPlanLog, newPlanLog);
    }

    /**
     * @return Entry, contains old plan as a key and new/updated plan as a value
     * */
    public Map.Entry<PostingPlanLog, PostingPlanLog> createOrUpdatePostingPlan(PostingPlanLog planLog) {
        log.info("Get exclusive plan log: {}", planLog.getPlanId());
        PostingPlanLog oldPlanLog = postingPlanDao.getExclusivePlanLog(planLog.getPlanId());
        log.info("Add or update plan log: {}", planLog.getPlanId());
        PostingPlanLog newPlanLog = postingPlanDao.addOrUpdatePlanLog(planLog);
        log.info("Result plan log: {}", newPlanLog);
        return new AbstractMap.SimpleEntry<>(oldPlanLog, newPlanLog);
    }

    public void addPostingLogs(List<PostingLog> postingLogs) {
        log.info("Add posting logs: {}", postingLogs);
        postingPlanDao.addPostingLogs(postingLogs);
        log.info("Added posting logs: {}", postingLogs.size());
    }


}
