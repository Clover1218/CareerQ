package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.InteractionDO;
import com.careerq.favorite.FavoriteDtos.FavoriteView;
import com.careerq.security.AuthUser;
import com.careerq.service.FavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final Set<String> TARGET_TYPES = Set.of("question", "answer");

    private final InteractionDao interactionDao;
    private final QuestionDao questionDao;
    private final AnswerDao answerDao;

    public FavoriteServiceImpl(InteractionDao interactionDao,
                               QuestionDao questionDao,
                               AnswerDao answerDao) {
        this.interactionDao = interactionDao;
        this.questionDao = questionDao;
        this.answerDao = answerDao;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<FavoriteView> list(AuthUser user, String targetType, Long cursor, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        String filter = normalizeTargetType(targetType);
        if (cursor != null && cursor <= 0) throw new BusinessException(ErrorCode.INVALID_PARAM, "收藏游标无效");
        // 先过滤不可见内容再分页，避免整页为空但仍有后续收藏。
        List<InteractionDO> rows = interactionDao.findVisibleFavorites(user.getId(), filter, cursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<InteractionDO> page = hasMore ? rows.subList(0, size) : rows;

        List<FavoriteView> items = new ArrayList<>();
        for (InteractionDO row : page) {
            FavoriteView view = resolve(row);
            if (view != null) {
                items.add(view);
            }
        }
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new PageResult<>(items, nextCursor);
    }

    private FavoriteView resolve(InteractionDO row) {
        return switch (row.getTargetType()) {
            case "question" -> questionDao.findById(row.getTargetId())
                    .filter(question -> "public".equals(question.getScope()) && "published".equals(question.getStatus()))
                    .map(question -> new FavoriteView("question", question.getId(), question.getId(), null, question.getTitle(),
                            excerpt(question.getBody()), row.getCreatedAt()))
                    .orElse(null);
            case "answer" -> answerDao.findById(row.getTargetId())
                    .filter(answer -> "published".equals(answer.getStatus()))
                    .filter(answer -> questionDao.findById(answer.getQuestionId()).filter(question -> "published".equals(question.getStatus())).isPresent())
                    .map(answer -> {
                        String title = questionDao.findById(answer.getQuestionId())
                                .map(question -> "回答：" + question.getTitle())
                                .orElse("回答");
                        return new FavoriteView("answer", answer.getId(), answer.getQuestionId(), answer.getFollowupId(), title,
                                excerpt(answer.getBody()), row.getCreatedAt());
                    })
                    .orElse(null);
            default -> null;
        };
    }

    private String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return null;
        }
        String value = targetType.trim().toLowerCase();
        if (!TARGET_TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 只能是 question/answer");
        }
        return value;
    }

    private String excerpt(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
    }
}
