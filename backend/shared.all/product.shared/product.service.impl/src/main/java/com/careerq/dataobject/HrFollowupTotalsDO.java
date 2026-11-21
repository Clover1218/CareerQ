package com.careerq.dataobject;

public class HrFollowupTotalsDO {
    private long pendingQuestionCount;
    private long pendingFollowupCount;

    public long getPendingQuestionCount() { return pendingQuestionCount; }
    public void setPendingQuestionCount(long pendingQuestionCount) { this.pendingQuestionCount = pendingQuestionCount; }
    public long getPendingFollowupCount() { return pendingFollowupCount; }
    public void setPendingFollowupCount(long pendingFollowupCount) { this.pendingFollowupCount = pendingFollowupCount; }
}
