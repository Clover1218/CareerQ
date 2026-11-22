package com.careerq.dao;

import com.careerq.dataobject.FollowupDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FollowupDao {

    @Insert("""
            insert into followups (
                question_id, hr_id, root_answer_id, sender_id, sender_role, body, scope,
                moderation_status, visibility, risk_level, moderation_reason, moderated_at, created_at
            ) values (
                #{questionId}, #{hrId}, #{rootAnswerId}, #{senderId}, #{senderRole}, #{body}, #{scope},
                #{moderationStatus}, #{visibility}, #{riskLevel}, #{moderationReason}, #{moderatedAt}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FollowupDO followup);

    @Select("""
<script>
            select f.* from followups f where f.scope = 'public' and f.question_id = #{questionId}
            and f.root_answer_id is not null
            and (
                f.visibility = 'VISIBLE'
                or (#{senderId} is not null and f.sender_id = #{senderId} and f.visibility = 'HIDDEN')
                or #{operator} = true
                or (f.visibility = 'REMOVED' and exists (
                    select 1 from answers a where a.question_id = f.question_id and a.followup_id = f.id
                      and (a.visibility = 'VISIBLE'
                           or (#{senderId} is not null and a.author_id = #{senderId}
                               and a.visibility in ('HIDDEN', 'REMOVED')))))
            )
            order by f.created_at, f.id
            </script>
            """)
    List<FollowupDO> findVisible(@Param("questionId") Long questionId,
                                 @Param("senderId") Long senderId,
                                 @Param("operator") boolean operator);

@Select("select * from followups where scope = 'public' and moderation_status = 'L3' order by id")
    List<FollowupDO> findPending();

    @Select("select count(*) from followups where moderation_status = #{status}")
    long countByModerationStatus(String status);

    @Select("select count(*) from followups where visibility = #{visibility}")
    long countByVisibility(String visibility);

    @Select("""
            select count(*) from followups
            where moderation_status = #{status} and visibility = #{visibility}
            """)
    long countByModerationStatusAndVisibility(@Param("status") String status,
                                              @Param("visibility") String visibility);

    @Select("select count(*) from followups where sender_id = #{senderId} and created_at >= #{since}")
    long countBySenderSince(@Param("senderId") Long senderId, @Param("since") LocalDateTime since);

    @Select("select * from followups where id = #{id} and scope = 'public'")
    Optional<FollowupDO> findById(Long id);

    @Select("select * from followups where id = #{id} and scope = 'public' for update")
    Optional<FollowupDO> findByIdForUpdate(Long id);

    @Update("""
            update followups set moderation_status = #{moderationStatus}, visibility = #{visibility},
                risk_level = #{riskLevel}, moderation_reason = #{reason}, moderated_at = current_timestamp
            where id = #{id}
            """)
    int updateModeration(@Param("id") Long id,
                         @Param("moderationStatus") String moderationStatus,
                         @Param("visibility") String visibility,
                         @Param("riskLevel") String riskLevel,
                         @Param("reason") String reason);

    @Update("""
            <script>
            update followups set moderation_status = #{toStatus}, visibility = #{visibility},
                risk_level = #{riskLevel}, moderation_reason = #{reason}, moderated_at = current_timestamp,
                review_attempts = review_attempts + 1
            where id = #{id} and moderation_status in
            <foreach collection="expected" item="item" open="(" separator="," close=")">#{item}</foreach>
            </script>
            """)
    int updateModerationIfStatusIn(@Param("id") Long id,
                                   @Param("expected") List<String> expected,
                                   @Param("toStatus") String toStatus,
                                   @Param("visibility") String visibility,
                                   @Param("riskLevel") String riskLevel,
                                   @Param("reason") String reason);

    @Update("""
            <script>
            update followups set visibility = #{visibility}, moderation_reason = #{reason},
                moderated_at = current_timestamp
            where id = #{id} and moderation_status in
            <foreach collection="expectedStatus" item="item" open="(" separator="," close=")">#{item}</foreach>
              and visibility in
            <foreach collection="expectedVisibility" item="item" open="(" separator="," close=")">#{item}</foreach>
            </script>
            """)
    int updateVisibilityIf(@Param("id") Long id,
                           @Param("expectedStatus") List<String> expectedStatus,
                           @Param("expectedVisibility") List<String> expectedVisibility,
                           @Param("visibility") String visibility,
                           @Param("reason") String reason);

    @Update("""
            update followups set moderation_status = 'L3', visibility = 'HIDDEN',
                moderation_reason = #{reason}, moderated_at = current_timestamp
            where id = #{id} and moderation_status in ('APPROVED', 'REJECTED')
            """)
    int undoModerationIf(@Param("id") Long id, @Param("reason") String reason);

    @Select("""
            select count(*) from followups
            where moderation_status = 'L3' and review_attempts >= 2
            """)
    long countArbitration();

    @Update("""
            update followups set moderation_status = 'WITHDRAWN', visibility = 'REMOVED'
            where id = #{id} and visibility in ('VISIBLE', 'HIDDEN')
            """)
    int withdraw(Long id);

    @Select("""
            select max(f.id) from followups f
            where f.question_id = #{questionId} and f.hr_id = #{hrId}
              and f.root_answer_id = #{rootAnswerId}
              and f.sender_role = 'student' and f.scope = 'public'
              and f.visibility != 'REMOVED'
            """)
    Long findLatestId(@Param("questionId") Long questionId, @Param("hrId") Long hrId,
                      @Param("rootAnswerId") Long rootAnswerId);
}
