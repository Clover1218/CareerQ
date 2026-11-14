package com.careerq.report;

import com.careerq.dao.ReportDao;
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

    private static final Set<String> TARGET_TYPES = Set.of("question", "answer", "post", "public_post");

    private final ReportDao reportDao;

    public ReportServiceImpl(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    @Override
    @Transactional
    public void create(AuthUser reporter, CreateRequest request) {
        String targetType = request.targetType().trim().toLowerCase();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
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
