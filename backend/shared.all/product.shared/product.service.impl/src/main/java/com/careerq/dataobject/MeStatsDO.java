package com.careerq.dataobject;

/** 个人主页统计的聚合结果。 */
public class MeStatsDO {

    private long questionCount;

    private long receivedAnswerCount;

    private long authoredAnswerCount;

    private long questionLikeCount;

    private long answerLikeCount;

    public long getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(long questionCount) {
        this.questionCount = questionCount;
    }

    public long getReceivedAnswerCount() {
        return receivedAnswerCount;
    }

    public void setReceivedAnswerCount(long receivedAnswerCount) {
        this.receivedAnswerCount = receivedAnswerCount;
    }

    public long getAuthoredAnswerCount() {
        return authoredAnswerCount;
    }

    public void setAuthoredAnswerCount(long authoredAnswerCount) {
        this.authoredAnswerCount = authoredAnswerCount;
    }

    public long getQuestionLikeCount() {
        return questionLikeCount;
    }

    public void setQuestionLikeCount(long questionLikeCount) {
        this.questionLikeCount = questionLikeCount;
    }

    public long getAnswerLikeCount() {
        return answerLikeCount;
    }

    public void setAnswerLikeCount(long answerLikeCount) {
        this.answerLikeCount = answerLikeCount;
    }
}
