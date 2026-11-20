package com.careerq.dataobject;

import java.time.LocalDateTime;

public class QuestionDO {

    private Long id;

    private String scope;

    private Long authorId;

    private String title;

    private String body;

    private String status;

    private LocalDateTime firstAnswerAt;

    private int answerCount;

    private Boolean hasPendingAnswer;

    private int followupCount;

    private int usefulCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




    public LocalDateTime getFirstAnswerAt() {
        return firstAnswerAt;
    }

    public int getAnswerCount() {
        return answerCount;
    }

    public Boolean getHasPendingAnswer() {
        return hasPendingAnswer;
    }

    public void setHasPendingAnswer(Boolean hasPendingAnswer) {
        this.hasPendingAnswer = hasPendingAnswer;
    }

    public int getFollowupCount() {
        return followupCount;
    }

    public void setFollowupCount(int followupCount) {
        this.followupCount = followupCount;
    }

    public int getLikeCount() {
        return likeCount == null ? 0 : likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public int getFavoriteCount() {
        return favoriteCount == null ? 0 : favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public int getUsefulCount() {
        return usefulCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
