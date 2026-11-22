package com.careerq.moderation;

import java.util.List;

public interface ModerationAiClient {

    ModerationAiResult review(ModerationAiInput input);

    record ModerationAiInput(String targetType, String title, String body,
                             String questionTitle, String authorRole, List<String> l1Labels) {
    }

    record ModerationAiResult(String decision, RiskLevel risk, List<String> labels,
                              Double confidence, String reason) {
    }
}