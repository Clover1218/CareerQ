package com.careerq.dao;

import com.careerq.dataobject.HrFollowupSummaryDO;
import com.careerq.dataobject.HrFollowupTotalsDO;
import com.careerq.dataobject.QuestionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 只查询当前 HR 的公开追问；按追问统计，避免一条追问的多次回复造成重复计数。 */
public interface HrFollowupDao {
    String UNANSWERED = """
            not exists (select 1 from answers a
                where a.question_id = f.question_id and a.followup_id = f.id and a.author_id = #{hrId}
                  and a.visibility != 'REMOVED' and a.moderation_status != 'REJECTED')
            """;
    String REVIEWING = """
            not exists (select 1 from answers a
                where a.question_id = f.question_id and a.followup_id = f.id and a.author_id = #{hrId}
                  and a.visibility = 'VISIBLE')
            and exists (select 1 from answers a
                where a.question_id = f.question_id and a.followup_id = f.id and a.author_id = #{hrId}
                  and a.visibility = 'HIDDEN' and a.moderation_status in ('L1', 'L2', 'L3'))
            """;
    String SUMMARY = "select f.question_id, sum(case when " + UNANSWERED + " then 1 else 0 end) as pending_count, "
            + "sum(case when " + REVIEWING + " then 1 else 0 end) as reviewing_count, "
            + "min(case when " + UNANSWERED + " then f.id end) as first_pending_followup_id, "
            + "max(case when " + UNANSWERED + " then f.created_at end) as latest_pending_at "
            + """
            from followups f join questions q on q.id = f.question_id
            where f.hr_id = #{hrId} and f.scope = 'public' and f.visibility = 'VISIBLE'
              and f.root_answer_id is not null
              and f.sender_role = 'student' and q.scope = 'public' and q.visibility = 'VISIBLE'
              and exists (select 1 from answers own_answer
                  where own_answer.question_id = q.id and own_answer.author_id = #{hrId})
            """;

    @Select("<script>" + SUMMARY + " and f.question_id in "
            + "<foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " group by f.question_id</script>")
    List<HrFollowupSummaryDO> summarizeQuestions(@Param("hrId") Long hrId,
                                                @Param("questionIds") List<Long> questionIds);

    @Select("<script>select tasks.* from (" + SUMMARY + " group by f.question_id) tasks "
            + "where tasks.pending_count &gt; 0 "
            + "<if test='cursor != null'>and (tasks.latest_pending_at &lt; #{cursorAt} "
            + "or (tasks.latest_pending_at = #{cursorAt} and tasks.question_id &lt; #{cursor}))</if>"
            + " order by tasks.latest_pending_at desc, tasks.question_id desc limit #{limit}</script>")
    List<HrFollowupSummaryDO> listPending(@Param("hrId") Long hrId, @Param("cursor") Long cursor,
                                         @Param("cursorAt") LocalDateTime cursorAt, @Param("limit") int limit);

    @Select("select count(*) as pending_question_count, coalesce(sum(tasks.pending_count), 0) as pending_followup_count "
            + "from (" + SUMMARY + " group by f.question_id) tasks where tasks.pending_count > 0")
    HrFollowupTotalsDO totals(@Param("hrId") Long hrId);

    @Select("""
            <script>
            select q.* from questions q where q.scope = 'public' and q.visibility = 'VISIBLE' and q.id in
            <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            and exists (select 1 from answers a where a.question_id = q.id and a.author_id = #{hrId})
            </script>
            """)
    List<QuestionDO> questions(@Param("hrId") Long hrId, @Param("questionIds") List<Long> questionIds);
}
