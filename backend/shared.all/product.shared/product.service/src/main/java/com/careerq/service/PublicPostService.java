package com.careerq.service;

import java.time.LocalDateTime;
import java.util.List;

public interface PublicPostService {

    List<PostView> list();

    List<PostView> highlights();

    record PostView(Long id, Long originalQuestionId, String title, String questionBody,
                    String hrAnswer, boolean anonymized, LocalDateTime publishedAt) {
    }
}
