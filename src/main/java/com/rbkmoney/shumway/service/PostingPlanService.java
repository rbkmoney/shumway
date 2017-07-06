package com.rbkmoney.shumway.service;

import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.PostingPlanLog;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class PostingPlanService {
    private PostingPlanDao postingPlanDao;

    public PostingPlanService(PostingPlanDao postingPlanDao) {
        this.postingPlanDao = postingPlanDao;
    }

    public Map<Long, List<PostingLog>> getPostingLogs(String planId, PostingOperation operation) {
        return postingPlanDao.getPostingLogs(planId, operation);
    }

    public Map<Long, List<PostingLog>> getPostingLogs(String planId, Collection<Long> batchIds, PostingOperation operation) {
        return postingPlanDao.getPostingLogs(planId, batchIds, operation);
    }

    public PostingPlanLog getSharedPostingPlan(String planId) {
        return postingPlanDao.getSharedPlanLog(planId);
    }

    public PostingPlanLog getExclusivePostingPlan(String planId) {
        return postingPlanDao.getExclusivePlanLog(planId);
    }

    /**
     * @return Entry, contains old plan as a key and new/updated plan as a value
     * */
    public Map.Entry<PostingPlanLog, PostingPlanLog>  updatePostingPlan(PostingPlanLog planLog, PostingOperation overridableOperation) {
        PostingPlanLog oldPlanLog = postingPlanDao.getExclusivePlanLog(planLog.getPlanId());
        PostingPlanLog newPlanLog = postingPlanDao.updatePlanLog(planLog, overridableOperation);

        return new AbstractMap.SimpleEntry<>(oldPlanLog, newPlanLog);
    }

    /**
     * @return Entry, contains old plan as a key and new/updated plan as a value
     * */
    public Map.Entry<PostingPlanLog, PostingPlanLog> createOrUpdatePostingPlan(PostingPlanLog planLog) {
        PostingPlanLog oldPlanLog = postingPlanDao.getExclusivePlanLog(planLog.getPlanId());
        PostingPlanLog newPlanLog = postingPlanDao.addOrUpdatePlanLog(planLog);
        return new AbstractMap.SimpleEntry<>(oldPlanLog, newPlanLog);
    }

    public void addPostingLogs(List<PostingLog> postingLogs) {
        postingPlanDao.addPostingLogs(postingLogs);
    }


}
