package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.question.QuestionDtos.FeedPage;
import com.careerq.question.QuestionDtos.FeedQuery;
import com.careerq.question.QuestionDtos.FeedSort;
import com.careerq.security.AuthUsers;
import com.careerq.service.QuestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final QuestionService questionService;

    public FeedController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * 公开问题列表：可选登录。默认推荐流（游客走「质量 + 新鲜度」冷启动排序）；
     * 支持关键词、多标签、时间范围筛选与 recommend/latest/hot 排序。
     */
    @GetMapping("/questions")
    public ApiResponse<FeedPage> questions(Authentication authentication,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String tag,
                                           @RequestParam(required = false) List<String> tags,
                                           @RequestParam(required = false) String tagMode,
                                           @RequestParam(required = false) String period,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(defaultValue = "20") int limit) {
        List<String> selectedTags = new ArrayList<>();
        if (tag != null && !tag.isBlank()) {
            selectedTags.add(tag);
        }
        if (tags != null) {
            selectedTags.addAll(tags);
        }

        LocalDateTime fromTime = parseFilterTime(from, false);
        LocalDateTime toTime = parseFilterTime(to, true);

        FeedQuery query = new FeedQuery(q, selectedTags, matchAll(tagMode), period,
                fromTime, toTime, parseSort(sort));
        return ApiResponse.ok(questionService.feed(AuthUsers.optional(authentication), query, cursor, limit));
    }

    private static FeedSort parseSort(String value) {
        if (value == null || value.isBlank()) {
            return FeedSort.RECOMMEND;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "recommend" -> FeedSort.RECOMMEND;
            case "latest" -> FeedSort.LATEST;
            case "hot" -> FeedSort.HOT;
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "sort 只能是 recommend、latest 或 hot");
        };
    }

    private static boolean matchAll(String tagMode) {
        if (tagMode == null || tagMode.isBlank()) {
            return true;
        }
        return switch (tagMode.trim().toLowerCase(Locale.ROOT)) {
            case "and" -> true;
            case "or" -> false;
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM, "tagMode 只能是 and 或 or");
        };
    }

    /** 同时接受日期时间与纯日期；结束日期按「包含当天」处理，转为次日零点。 */
    private static LocalDateTime parseFilterTime(String value, boolean exclusiveEnd) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // 继续尝试纯日期
        }
        try {
            LocalDate date = LocalDate.parse(text);
            return exclusiveEnd ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "时间格式不合法，请使用 ISO 日期或日期时间");
        }
    }
}
