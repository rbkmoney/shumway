package com.rbkmoney.shumway.service;

import com.rbkmoney.shumway.dao.PostingPlanDao;
import com.rbkmoney.shumway.domain.PostingLog;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.domain.PostingPlanLog;

import java.util.List;

/**
 * Created by vpankrashkin on 16.09.16.
 */
public class PostingPlanService {
    private PostingPlanDao postingPlanDao;

    public PostingPlanService(PostingPlanDao postingPlanDao) {
        this.postingPlanDao = postingPlanDao;
    }

    public List<PostingLog> getPostingLogs(String planId, PostingOperation operation) {
        return postingPlanDao.getPostingLogs(planId, operation);
    }

    public PostingPlanLog getSharedPostingPlan(String planId) {
        return postingPlanDao.getSharedPlanLog(planId);
    }

    public PostingPlanLog getExclusivePostingPlan(String planId) {
        return postingPlanDao.getExclusivePlanLog(planId);
    }

    public PostingPlanLog updatePostingPlan(PostingPlanLog planLog, PostingOperation overridableOperation) {
        return postingPlanDao.updatePlanLog(planLog, overridableOperation);
    }

    public PostingPlanLog createOrUpdatePostingPlan(PostingPlanLog planLog) {
        return postingPlanDao.addOrUpdatePlanLog(planLog);
    }

    public void addPostingLogs(List<PostingLog> postingLogs) {
        postingPlanDao.addPostingLogs(postingLogs);
    }

    public boolean isOverridable(PostingOperation source, PostingOperation target) {
        return source == PostingOperation.HOLD;
    }


}
