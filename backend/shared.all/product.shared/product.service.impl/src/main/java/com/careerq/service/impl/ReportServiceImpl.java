package com.careerq.service.impl;

import com.careerq.dao.ReportDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.AnswerDao;
import com.careerq.dataobject.ReportDO;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.security.AuthUser;
import com.careerq.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Set<String> TARGET_TYPES = Set.of("question", "answer");

    private final ReportDao reportDao;
    private final QuestionDao questionDao;
    private final AnswerDao answerDao;

    public ReportServiceImpl(ReportDao reportDao, QuestionDao questionDao, AnswerDao answerDao) {
        this.reportDao = reportDao;
        this.questionDao = questionDao;
        this.answerDao = answerDao;
    }

    @Override
    @Transactional
    public void create(AuthUser reporter, CreateRequest request) {
        String targetType = request.targetType().trim().toLowerCase();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
        Long questionId = "question".equals(targetType) ? request.targetId()
                : answerDao.findById(request.targetId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "举报目标不存在"))
                        .getQuestionId();
        questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "举报目标不存在"));
        ReportDO report = new ReportDO();
        report.setReporterId(reporter.getId());
        report.setTargetType(targetType);
        report.setTargetId(request.targetId());
        report.setReason(request.reason());
        report.setDetail(request.detail());
        report.setStatus("pending");
        reportDao.insert(report);
    }
}
