package com.careerq.hr;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class HrPersonalDtos {
    private HrPersonalDtos() {}

    public record HrProfileView(Long id, String nickname, String avatarUrl, String companyDisplay,
                          String industry, String position, String bio, List<String> specialties,
                          String verifyStatus) {}

    /** null 保持原值，空字符串/空列表清空可选资料；认证状态不允许由本人修改。 */
    public record HrProfilePatch(
            @Size(max = 64, message = "昵称不能超过 64 字") String nickname,
            @Size(max = 128, message = "企业展示名不能超过 128 字") String companyDisplay,
            @Size(max = 64, message = "行业不能超过 64 字") String industry,
            @Size(max = 64, message = "职业方向不能超过 64 字") String position,
            @Size(max = 500, message = "个人简介不能超过 500 字") String bio,
            @Size(max = 6, message = "擅长领域最多 6 项")
            List<@Size(max = 24, message = "每个擅长领域不能超过 24 字") String> specialties) {}

    public record HrContributionStats(long rootAnswerCount, long followupReplyCount, long likeCount, long usefulCount) {}

    public record HrAnswerRecord(Long id, Long questionId, String questionTitle, boolean canViewQuestion,
                               Long rootAnswerId, Long followupId, String body, String status,
                               String removalSource, int likeCount, int usefulCount, LocalDateTime createdAt) {}
}
