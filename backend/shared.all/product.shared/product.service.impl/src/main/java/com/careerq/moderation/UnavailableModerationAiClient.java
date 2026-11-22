package com.careerq.moderation;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 审核客户端占位实现：未配置真实服务时按“需人工复核”处理，避免内容被误放行。
 */
@Component
public class UnavailableModerationAiClient implements ModerationAiClient {

    @Override
    public ModerationAiResult review(ModerationAiInput input) {
        return new ModerationAiResult("review", RiskLevel.UNRATED, List.of(), null,
                "AI 审核服务未配置");
    }
}