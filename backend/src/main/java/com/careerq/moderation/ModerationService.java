package com.careerq.moderation;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * M3 的轻量内容裁定：命中高风险词转人工（pending_human），否则直接发布。
 * M5 会替换为「词库 + 内容安全 API + 人审队列」的完整状态机。
 */
@Service
public class ModerationService {

    private static final List<String> REVIEW_WORDS = List.of(
            "代写", "刷单", "加微信", "加QQ", "联系方式", "包过", "内推收费",
            "http://", "https://");

    public String decide(String text) {
        if (text == null || text.isBlank()) {
            return "published";
        }
        String normalized = text.toLowerCase();
        for (String word : REVIEW_WORDS) {
            if (normalized.contains(word)) {
                return "pending_human";
            }
        }
        return "published";
    }
}
