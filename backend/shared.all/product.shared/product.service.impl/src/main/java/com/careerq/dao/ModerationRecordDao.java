package com.careerq.dao;

import com.careerq.dataobject.ModerationRecordDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface ModerationRecordDao {

    @Insert("""
            insert into moderation_records (
                target_type, target_id, stage, model_version, risk_labels, decision, operator_id, note,
                created_at
            ) values (
                #{targetType}, #{targetId}, #{stage}, #{modelVersion}, #{riskLabels}, #{decision},
                #{operatorId}, #{note}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModerationRecordDO moderationRecord);
}
