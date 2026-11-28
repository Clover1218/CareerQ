package com.careerq.question;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.common.UserBrief;
import com.careerq.followup.FollowupDtos.FollowupView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "标题不能为空")
            @Size(max = 200, message = "标题不能超过 200 字")
            String title,
            @NotBlank(message = "问题描述不能为空")
            @Size(max = 5000, message = "描述不能超过 5000 字")
            String body,
            List<String> tags,
            String scope) {
    }

    public record QuestionView(
            Long id,
            String scope,
            String displayStatus,
            String status,
            String title,
            String body,
            List<String> tags,
            UserBrief author,
            LocalDateTime createdAt,
            int answerCount,
            int followupCount,
            int usefulCount,
            int likeCount,
            int favoriteCount,
            LocalDateTime firstAnswerAt) {
    }

    public record HrAnswerSummary(
            Long id,
            Long followupId,
            String body,
            String lengthType,
String displayStatus,
            String status,
            String removalSource,
            LocalDateTime createdAt) {
    }

    public record HrFollowupSummary(int pendingCount, int reviewingCount, Long firstPendingFollowupId,
                                    LocalDateTime latestPendingAt) {
    }

    public record HrAnsweredQuestionView(QuestionView question, HrAnswerSummary myAnswer, HrFollowupSummary followups) {
    }

    public record HrAnsweredQuestionPage(List<HrAnsweredQuestionView> items, Long nextCursor,
                                         LocalDateTime nextCursorAt, long pendingQuestionCount,
                                         long pendingFollowupCount) {
    }

    public record HrAnsweredPublicQuestionView(QuestionView question, boolean hasPendingAnswer) {
    }

    /** 公开 feed 卡片：包含公开作者信息，正文由前端按行折叠。 */
    public record QuestionFeedItem(
            Long id,
            String title,
            String excerpt,
            List<String> tags,
            UserBrief author,
            int answerCount,
            int followupCount,
            int replyCount,
            int likeCount,
            int favoriteCount,
            LocalDateTime createdAt,
            List<String> matchedTags,
            ViewerState viewer) {
    }

    /** 推荐流分页：游标不透明，使用字符串而不是公共 PageResult 的 Long。 */
    public record FeedPage(List<QuestionFeedItem> items, String nextCursor) {
    }

    public enum FeedSort {
        RECOMMEND,
        LATEST,
        HOT
    }

    /** 公开列表筛选条件；period 保留原始枚举值，便于游标指纹在翻页时保持稳定。 */
    public record FeedQuery(
            String q,
            List<String> tags,
            boolean matchAll,
            String period,
            LocalDateTime from,
            LocalDateTime to,
            FeedSort sort) {
    }

    /** 当前查看者对问题/回答的互动状态。 */
    public record ViewerState(boolean liked, boolean usefuled, boolean favorited, boolean disliked) {
    }

    public record QuestionDetail(QuestionView question, List<AnswerView> answers, List<FollowupView> followups,
                                 ViewerState viewer) {
    }

    /** 展开全文需要的完整问题内容。 */
    public record QuestionContentView(QuestionView question, ViewerState viewer) {
    }

    /** HR 主回答、HR 追问回复与学生 FollowUp 合并后的单条时间线内容。 */
    public record ThreadItemView(
            String key,
            String kind,
            Long id,
            Long followupId,
            /** 追问的目标 HR，用于答主标注与权限判断。 */
            Long hrId,
            /** 对应主回答；为空表示尚不能确定归属的旧追问。 */
            Long rootAnswerId,
            String body,
            UserBrief author,
            boolean hr,
String displayStatus,
            String status,
            /** 仅 HR 回答/回复的移除来源；学生追问为空。 */
            String removalSource,
            int likeCount,
            boolean liked,
            LocalDateTime createdAt) {
    }

    public record ThreadView(List<ThreadItemView> items, ViewerState viewer,
                             int answerCount, int followupCount, int replyCount) {
    }
}
