package com.careerq.dao;

import com.careerq.dataobject.HrAnswerRecordDO;
import com.careerq.hr.HrPersonalDtos.HrContributionStats;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.Optional;

public interface HrPersonalDao {
    @Select("""
            select a.*, q.title as question_title, q.status as question_status, f.root_answer_id
            from answers a join questions q on q.id = a.question_id
            left join followups f on f.id = a.followup_id and f.question_id = a.question_id
            where a.id = #{answerId} and a.author_id = #{hrId} and q.scope = 'public'
              and (a.followup_id is null or f.scope = 'public' and f.root_answer_id is not null)
            """)
    Optional<HrAnswerRecordDO> answer(@Param("hrId") Long hrId, @Param("answerId") Long answerId);
    @Select("select id from users where id = #{id} for update")
    Long lockUser(Long id);

    @Update("update users set nickname = #{nickname}, updated_at = current_timestamp where id = #{id}")
    int updateNickname(@Param("id") Long id, @Param("nickname") String nickname);

    @Select("""
            select coalesce(sum(case when a.followup_id is null then 1 else 0 end), 0) as root_answer_count,
                   coalesce(sum(case when a.followup_id is not null then 1 else 0 end), 0) as followup_reply_count,
                   coalesce(sum(a.like_count), 0) as like_count, coalesce(sum(a.useful_count), 0) as useful_count
            from answers a join questions q on q.id = a.question_id
            where a.author_id = #{hrId} and a.status = 'published'
              and q.scope = 'public' and q.status = 'published'
              and (a.followup_id is null or exists (
                  select 1 from followups f where f.id = a.followup_id and f.question_id = a.question_id
                    and f.scope = 'public' and f.root_answer_id is not null))
            """)
    HrContributionStats stats(Long hrId);

    @Select("""
            <script>
            select a.*, q.title as question_title, q.status as question_status, f.root_answer_id
            from answers a join questions q on q.id = a.question_id
            left join followups f on f.id = a.followup_id and f.question_id = a.question_id
            where a.author_id = #{hrId} and q.scope = 'public'
              and (a.followup_id is null or f.scope = 'public' and f.root_answer_id is not null)
            <if test="cursor != null">and a.id &lt; #{cursor}</if>
            <if test="kind == 'root'">and a.followup_id is null</if>
            <if test="kind == 'reply'">and a.followup_id is not null</if>
            <choose>
              <when test="status == 'reviewing'">and a.status in ('pending_machine', 'pending_human')</when>
              <when test="status == 'withdrawn'">and a.status = 'removed' and a.removal_source = 'author'</when>
              <when test="status == 'downlisted'">and a.status = 'removed' and a.removal_source = 'operator'</when>
              <when test="status == 'removed'">and a.status = 'removed'</when>
              <when test="status != 'all'">and a.status = #{status}</when>
            </choose>
            order by a.id desc limit #{limit}
            </script>
            """)
    List<HrAnswerRecordDO> answers(@Param("hrId") Long hrId, @Param("kind") String kind,
                                    @Param("status") String status, @Param("cursor") Long cursor,
                                    @Param("limit") int limit);
}
