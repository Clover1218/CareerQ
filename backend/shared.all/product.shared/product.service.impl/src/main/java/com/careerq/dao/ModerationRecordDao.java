package com.careerq.dao;

import com.careerq.dataobject.ModerationRecordDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

public interface ModerationRecordDao {

    @Insert("""
            insert into moderation_records (
                target_type, target_id, stage, model_version, risk_labels, risk_level, confidence,
                from_moderation_status, to_moderation_status, decision, operator_id, note, created_at
            ) values (
                #{targetType}, #{targetId}, #{stage}, #{modelVersion}, #{riskLabels}, #{riskLevel}, #{confidence},
                #{fromModerationStatus}, #{toModerationStatus}, #{decision}, #{operatorId}, #{note}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModerationRecordDO moderationRecord);

    @Select("""
            select * from moderation_records
            where target_type = #{targetType} and target_id = #{targetId}
            order by id
            """)
    List<ModerationRecordDO> findByTarget(@Param("targetType") String targetType,
                                          @Param("targetId") Long targetId);

    @Select("""
            select count(*) from (
                select target_type, target_id from moderation_records
                where operator_id = #{operatorId} and stage in ('human', 'ARBITRATE')
                group by target_type, target_id
            ) reviewed
            """)
    long countReviewedByOperator(Long operatorId);

    @Select("""
            select r.* from moderation_records r
            join (
                select target_type, target_id, max(id) as record_id from moderation_records
                where operator_id = #{operatorId} and stage in ('human', 'ARBITRATE')
                group by target_type, target_id
            ) latest on latest.record_id = r.id
            where r.decision in ('approve', 'reject')
            order by r.id desc
            limit #{limit} offset #{offset}
            """)
    List<ModerationRecordDO> findReviewedByOperator(@Param("operatorId") Long operatorId,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /** 该内容最近一次人工裁定（含仲裁），撤销与 canUndo 以此为准。 */
    @Select("""
            select * from moderation_records
            where target_type = #{targetType} and target_id = #{targetId}
              and stage in ('human', 'ARBITRATE') and decision in ('approve', 'reject')
            order by id desc limit 1
            """)
    Optional<ModerationRecordDO> findLatestAdjudication(@Param("targetType") String targetType,
                                                        @Param("targetId") Long targetId);

    /** 创建时 L2 的结论；allow 表示内容当时已公开，撤销人工裁定后按此恢复可见性。 */
    @Select("""
            select decision from moderation_records
            where target_type = #{targetType} and target_id = #{targetId} and stage = 'L2'
            order by id desc limit 1
            """)
    String findLatestL2Decision(@Param("targetType") String targetType,
                                @Param("targetId") Long targetId);

    /** 本人是否已裁定过该内容（撤销不解除该限制）。 */
    @Select("""
            select exists (
                select 1 from moderation_records
                where operator_id = #{operatorId} and target_type = #{targetType}
                  and target_id = #{targetId} and stage in ('human', 'ARBITRATE')
                  and decision in ('approve', 'reject')
            )
            """)
    boolean hasDecided(@Param("operatorId") Long operatorId,
                       @Param("targetType") String targetType,
                       @Param("targetId") Long targetId);

    @Select("""
            select exists (
                select 1 from moderation_records
                where target_type = #{targetType} and target_id = #{targetId}
                  and stage = 'ARBITRATE' and decision in ('approve', 'reject')
            )
            """)
    boolean existsArbitration(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}