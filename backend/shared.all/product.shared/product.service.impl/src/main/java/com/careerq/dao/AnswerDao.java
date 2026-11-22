package com.careerq.dao;

import com.careerq.dataobject.AnswerDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnswerDao {

    @Update("""
            update answers set visibility = 'REMOVED',
                moderation_status = case when #{source} = 'author' then 'WITHDRAWN' else 'REJECTED' end,
                removal_source = #{source}, removed_by = #{actorId}, removed_at = #{removedAt},
                moderated_at = #{removedAt}, updated_at = #{removedAt}
            where id = #{id} and visibility <> 'REMOVED'
            """)
    int markRemoved(@Param("id") Long id, @Param("source") String source,
                    @Param("actorId") Long actorId, @Param("removedAt") LocalDateTime removedAt);

    @Insert("""
            insert into answers (
                question_id, followup_id, author_id, body, length_type, moderation_status, visibility,
                risk_level, moderation_reason, moderated_at, like_count, useful_count, created_at, updated_at
            ) values (
                #{questionId}, #{followupId}, #{authorId}, #{body}, #{lengthType}, #{moderationStatus}, #{visibility},
                #{riskLevel}, #{moderationReason}, #{moderatedAt}, #{likeCount}, #{usefulCount}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AnswerDO answer);

    @Select("select count(*) from answers where question_id = #{questionId}")
    long countByQuestionId(@Param("questionId") Long questionId);

    @Select("select a.* from answers a where a.id = #{id} and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')")
    Optional<AnswerDO> findById(Long id);

    @Select("select a.* from answers a where a.id = #{id} and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public') for update")
    Optional<AnswerDO> findByIdForUpdate(Long id);

    @Select("select count(*) from answers a where a.moderation_status = #{status} and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')")
    long countByModerationStatus(String status);

    @Select("select count(*) from answers a where a.visibility = #{visibility} and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')")
    long countByVisibility(String visibility);

    @Select("""
            select count(*) from answers a
            where a.moderation_status = #{status} and a.visibility = #{visibility}
              and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')
            """)
    long countByModerationStatusAndVisibility(@Param("status") String status,
                                              @Param("visibility") String visibility);

    @Select("""
            select a.* from answers a
            where a.moderation_status = 'L3'
              and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')
            order by a.id
            """)
    List<AnswerDO> findPending();

    @Select("select count(*) from answers where author_id = #{authorId} and created_at >= #{since}")
    long countByAuthorSince(@Param("authorId") Long authorId, @Param("since") LocalDateTime since);

    @Select("""
            select exists (
                select 1 from answers a
                where a.followup_id = #{followupId} and a.question_id = #{questionId}
                  and a.visibility = 'VISIBLE'
            )
            """)
    boolean hasVisibleReply(@Param("questionId") Long questionId, @Param("followupId") Long followupId);

    @Select("select count(*) from answers a where a.author_id = #{authorId} and a.visibility = 'VISIBLE' and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')")
    long countVisibleByAuthor(Long authorId);

    @Select("""
            <script>
            select a.* from answers a
            join (
                select question_id, max(id) as answer_id
                from answers
                where author_id = #{authorId} and question_id in
                <foreach collection="questionIds" item="questionId" open="(" separator="," close=")">
                    #{questionId}
                </foreach>
                and (followup_id is null or exists (select 1 from followups f
                     where f.id = answers.followup_id and f.question_id = answers.question_id
                       and f.scope = 'public' and f.root_answer_id is not null))
                group by question_id
            ) latest on latest.answer_id = a.id
            order by a.question_id
            </script>
            """)
    List<AnswerDO> findLatestByAuthorAndQuestionIds(@Param("authorId") Long authorId,
                                                    @Param("questionIds") List<Long> questionIds);

    @Select("""
<script>
            select a.* from answers a where a.question_id = #{questionId}
            and (a.followup_id is null or exists (select 1 from followups f
                 where f.id = a.followup_id and f.question_id = a.question_id
                   and f.scope = 'public' and f.root_answer_id is not null))
            and (
                a.visibility = 'VISIBLE'
                or (#{authorId} is not null and a.author_id = #{authorId} and a.visibility in ('HIDDEN', 'REMOVED'))
                or #{operator} = true
                or (a.followup_id is null and a.visibility = 'REMOVED' and exists (
                    select 1 from followups f where f.question_id = a.question_id and f.root_answer_id = a.id and f.scope = 'public'
                      and (f.visibility = 'VISIBLE'
                           or (#{authorId} is not null and f.sender_id = #{authorId} and f.visibility in ('HIDDEN', 'REMOVED'))
                           or (f.visibility = 'REMOVED' and exists (
                               select 1 from answers child where child.followup_id = f.id and child.question_id = f.question_id
                                 and (child.visibility = 'VISIBLE'
                                      or (#{authorId} is not null and child.author_id = #{authorId}
                                          and child.visibility in ('HIDDEN', 'REMOVED'))))))))
            )
            order by a.created_at, a.id
            </script>
            """)
    List<AnswerDO> findVisible(@Param("questionId") Long questionId,
                               @Param("authorId") Long authorId,
                               @Param("operator") boolean operator);

    @Select("select exists (select 1 from answers where question_id = #{questionId} and moderation_status in ('L1', 'L2', 'L3'))")
    boolean hasInProgress(Long questionId);

    @Select("""
            select exists (
                select 1 from answers
                where question_id = #{questionId} and author_id = #{authorId}
                  and followup_id is null and visibility = 'VISIBLE'
            )
            """)
    boolean hasVisibleRootAnswer(@Param("questionId") Long questionId, @Param("authorId") Long authorId);

    @Update("""
            update answers set moderation_status = #{moderationStatus}, visibility = #{visibility},
                risk_level = #{riskLevel}, moderation_reason = #{reason}, moderated_at = current_timestamp,
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateModeration(@Param("id") Long id,
                         @Param("moderationStatus") String moderationStatus,
                         @Param("visibility") String visibility,
                         @Param("riskLevel") String riskLevel,
                         @Param("reason") String reason);

    @Update("""
            <script>
            update answers set moderation_status = #{toStatus}, visibility = #{visibility},
                risk_level = #{riskLevel}, moderation_reason = #{reason}, moderated_at = current_timestamp,
                review_attempts = review_attempts + 1, updated_at = current_timestamp
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
            update answers set visibility = #{visibility}, moderation_reason = #{reason},
                removal_source = case when #{visibility} = 'REMOVED' then 'operator' else null end,
                removed_by = case when #{visibility} = 'REMOVED' then #{actorId} else null end,
                removed_at = case when #{visibility} = 'REMOVED' then current_timestamp else null end,
                moderated_at = current_timestamp, updated_at = current_timestamp
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
                           @Param("reason") String reason,
                           @Param("actorId") Long actorId);

    @Update("""
            update answers set moderation_status = 'APPROVED', visibility = 'VISIBLE',
                moderation_reason = #{reason}, removal_source = null, removed_by = null, removed_at = null,
                moderated_at = current_timestamp, updated_at = current_timestamp
            where id = #{id} and visibility = 'REMOVED' and removal_source = 'operator'
            """)
    int restoreOperatorRemoval(@Param("id") Long id, @Param("reason") String reason);

    @Update("""
            update answers set moderation_status = 'L3', visibility = 'HIDDEN',
                moderation_reason = #{reason}, moderated_at = current_timestamp, updated_at = current_timestamp
            where id = #{id} and moderation_status in ('APPROVED', 'REJECTED')
            """)
    int undoModerationIf(@Param("id") Long id, @Param("reason") String reason);

    @Select("""
            select count(*) from answers a
            where a.moderation_status = 'L3' and a.review_attempts >= 2
              and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')
            """)
    long countArbitration();

    @Update("""
            update answers set moderation_status = 'WITHDRAWN', visibility = 'REMOVED',
                removal_source = 'author', removed_by = #{actorId}, removed_at = current_timestamp,
                moderated_at = current_timestamp, updated_at = current_timestamp
            where id = #{id} and visibility in ('VISIBLE', 'HIDDEN')
            """)
    int withdraw(@Param("id") Long id, @Param("actorId") Long actorId);

    @Update("update answers set like_count = like_count + 1, updated_at = current_timestamp where id = #{id}")
    int incrementLike(Long id);

    @Update("update answers set useful_count = useful_count + 1, updated_at = current_timestamp where id = #{id}")
    int incrementUseful(Long id);

    @Update("update answers set like_count = case when like_count > 0 then like_count - 1 else 0 end, "
            + "updated_at = current_timestamp where id = #{id}")
    int decrementLike(Long id);

    @Update("update answers set useful_count = case when useful_count > 0 then useful_count - 1 else 0 end, "
            + "updated_at = current_timestamp where id = #{id}")
    int decrementUseful(Long id);
}