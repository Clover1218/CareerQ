package com.careerq.followup;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.followup.FollowupDtos.CreateRequest;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FollowupServiceImpl implements FollowupService {

    private static final String PUBLISHED = "published";

    private final FollowupDao followupDao;
    private final QuestionDao questionDao;

    public FollowupServiceImpl(FollowupDao followupDao, QuestionDao questionDao) {
        this.followupDao = followupDao;
        this.questionDao = questionDao;
    }


    @Override
    @Transactional
    public FollowupView create(AuthUser sender, Long questionId, CreateRequest request) {
        QuestionDO question = questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(sender, question);

        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(questionId);
        followup.setSenderId(sender.getId());
        followup.setSenderRole(sender.getRole());
        followup.setBody(request.body());
        followup.setScope(question.getScope());
        followup.setStatus(PUBLISHED);
        followupDao.insert(followup);
        return toView(followup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowupView> list(AuthUser current, Long questionId) {
        QuestionDO question = questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(current, question);
        return followupDao.findByQuestionIdAndStatusOrderByIdAsc(questionId, PUBLISHED)
                .stream().map(this::toView).toList();
    }

    private void requireVisible(AuthUser current, QuestionDO question) {
        if ("public".equals(question.getScope())) {
            // 公开问题的追问可匿名浏览；写入路径由控制器要求登录
            return;
        }
        if (current == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该私密问题");
        }
        boolean isAuthor = current.getId().equals(question.getAuthorId());
        boolean isAssignedHr = current.getId().equals(question.getAssignedHrId());
        boolean isOperator = "operator".equals(current.getRole()) || "admin".equals(current.getRole());
        if (!isAuthor && !isAssignedHr && !isOperator) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该私密问题");
        }
    }

    private FollowupView toView(FollowupDO followup) {
        return new FollowupView(
                followup.getId(),
                followup.getQuestionId(),
                followup.getSenderRole(),
                followup.getBody(),
                followup.getCreatedAt());
    }
}
