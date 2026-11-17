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

    /** 公开 feed 摘要：不含作者；正文不做字数截断，由前端按行折叠。 */
    public record QuestionFeedItem(
            Long id,
            String title,
            String excerpt,
            List<String> tags,
            int answerCount,
            int followupCount,
            int replyCount,
            int likeCount,
            int favoriteCount,
            LocalDateTime createdAt,
            List<String> matchedTags) {
    }

    /** 推荐流分页：游标不透明，使用字符串而不是公共 PageResult 的 Long。 */
    public record FeedPage(List<QuestionFeedItem> items, String nextCursor) {
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

    /** HR 正式回答与 FollowUp 平铺后的单条内容。 */
    public record ThreadItemView(
            String key,
            String kind,
            Long id,
            String body,
            UserBrief author,
            boolean hr,
            String status,
            int likeCount,
            boolean liked,
            LocalDateTime createdAt) {
    }

    public record ThreadView(List<ThreadItemView> items, ViewerState viewer,
                             int answerCount, int followupCount, int replyCount) {
    }
}
