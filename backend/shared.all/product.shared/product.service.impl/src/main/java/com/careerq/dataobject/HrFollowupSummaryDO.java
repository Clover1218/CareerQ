package com.careerq.dataobject;

import java.time.LocalDateTime;

/** 当前 HR 在一道公开问题下的追问待办，属于查询结果而非新增业务表。 */
public class HrFollowupSummaryDO {
    private Long questionId;
    private int pendingCount;
    private int reviewingCount;
    private Long firstPendingFollowupId;
    private LocalDateTime latestPendingAt;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public int getReviewingCount() { return reviewingCount; }
    public void setReviewingCount(int reviewingCount) { this.reviewingCount = reviewingCount; }
    public Long getFirstPendingFollowupId() { return firstPendingFollowupId; }
    public void setFirstPendingFollowupId(Long firstPendingFollowupId) { this.firstPendingFollowupId = firstPendingFollowupId; }
    public LocalDateTime getLatestPendingAt() { return latestPendingAt; }
    public void setLatestPendingAt(LocalDateTime latestPendingAt) { this.latestPendingAt = latestPendingAt; }
}
