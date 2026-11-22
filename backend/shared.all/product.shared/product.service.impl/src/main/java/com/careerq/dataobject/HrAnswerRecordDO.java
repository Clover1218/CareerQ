package com.careerq.dataobject;

public class HrAnswerRecordDO extends AnswerDO {
    private String questionTitle;
    private String questionStatus;
    private Long rootAnswerId;

    public String getQuestionTitle() { return questionTitle; }
    public void setQuestionTitle(String questionTitle) { this.questionTitle = questionTitle; }
    public String getQuestionStatus() { return questionStatus; }
    public void setQuestionStatus(String questionStatus) { this.questionStatus = questionStatus; }
    public Long getRootAnswerId() { return rootAnswerId; }
    public void setRootAnswerId(Long rootAnswerId) { this.rootAnswerId = rootAnswerId; }
}
