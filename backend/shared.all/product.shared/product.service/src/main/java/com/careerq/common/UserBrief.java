package com.careerq.common;

/** 公开可见的用户简介；HR 额外带公司/职位，用于回答区线程头。 */
public record UserBrief(Long id, String nickname, String company, String position) {

    public UserBrief(Long id, String nickname) {
        this(id, nickname, null, null);
    }
}
