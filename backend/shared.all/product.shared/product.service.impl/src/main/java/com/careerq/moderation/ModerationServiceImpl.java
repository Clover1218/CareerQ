package com.careerq.moderation;

import com.careerq.dao.ModerationRecordDao;
import com.careerq.dao.ModerationWordDao;
import com.careerq.dataobject.ModerationRecordDO;
import com.careerq.dataobject.ModerationWordDO;
import com.careerq.service.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 内容裁定 + 审核留痕。
 * 判定顺序：词库（block/review）→ 内置高风险规则 → 直接发布。
 */
@Service
public class ModerationServiceImpl implements ModerationService {

    private static final String PUBLISHED = "published";
    private static final String PENDING_HUMAN = "pending_human";
    private static final String REJECTED = "rejected";

    private static final List<String> REVIEW_WORDS = List.of(
            "代写", "刷单", "加微信", "加QQ", "联系方式", "包过", "内推收费",
            "http://", "https://");

    private final ModerationWordDao wordDao;
    private final ModerationRecordDao recordDao;

    public ModerationServiceImpl(ModerationWordDao wordDao,
                                 ModerationRecordDao recordDao) {
        this.wordDao = wordDao;
        this.recordDao = recordDao;
    }

    @Override
    public String decide(String text) {
        if (text == null || text.isBlank()) {
            return PUBLISHED;
        }
        String normalized = text.toLowerCase();
        for (ModerationWordDO item : wordDao.findAll()) {
            if (normalized.contains(item.getWord().toLowerCase())) {
                return "block".equals(item.getAction()) ? REJECTED : PENDING_HUMAN;
            }
        }
        for (String word : REVIEW_WORDS) {
            if (normalized.contains(word)) {
                return PENDING_HUMAN;
            }
        }
        return PUBLISHED;
    }

    @Override
    @Transactional
    public void recordMachine(String targetType, Long targetId, String decision) {
        record(targetType, targetId, "machine", "rule-v1", decision, null, null);
    }

    @Override
    @Transactional
    public void recordHuman(String targetType, Long targetId, String decision, Long operatorId, String note) {
        record(targetType, targetId, "human", null, decision, operatorId, note);
    }

    private void record(String targetType, Long targetId, String stage, String modelVersion,
                        String decision, Long operatorId, String note) {
        ModerationRecordDO record = new ModerationRecordDO();
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setStage(stage);
        record.setModelVersion(modelVersion);
        record.setDecision(decision);
        record.setOperatorId(operatorId);
        record.setNote(note);
        recordDao.insert(record);
    }
}
