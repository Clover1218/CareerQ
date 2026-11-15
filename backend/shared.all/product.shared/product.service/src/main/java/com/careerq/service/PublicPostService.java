package com.careerq.service;

import com.careerq.security.AuthUser;

import java.time.LocalDateTime;
import java.util.List;

public interface PublicPostService {

    List<PostView> list(AuthUser viewer);

    List<PostView> highlights(AuthUser viewer);

    record PostView(Long id, Long originalQuestionId, String title, String questionBody,
                    String hrAnswer, boolean anonymized, LocalDateTime publishedAt,
                    boolean liked, boolean favorited) {
    }
}