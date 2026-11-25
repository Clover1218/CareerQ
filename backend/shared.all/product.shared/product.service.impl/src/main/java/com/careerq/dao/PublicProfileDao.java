package com.careerq.dao;

import com.careerq.dataobject.HrAnswerRecordDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.profile.PublicProfileDtos.Statistics;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PublicProfileDao {
    String PUBLIC_ANSWERS = """
            from answers a join questions q on q.id = a.question_id
            left join followups f on f.id = a.followup_id and f.question_id = q.id
            left join answers root on root.id = f.root_answer_id and root.question_id = q.id
            where a.author_id = #{id} and a.visibility = 'VISIBLE'
              and q.scope = 'public' and q.visibility = 'VISIBLE'
              and (a.followup_id is null or (
                  f.scope = 'public' and f.visibility in ('VISIBLE', 'REMOVED')
                  and root.followup_id is null and root.author_id = f.hr_id
                  and root.visibility in ('VISIBLE', 'REMOVED')))
            """;

    @Select("""
            <script>
            select * from questions where author_id = #{id} and scope = 'public' and visibility = 'VISIBLE'
            <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc limit #{limit}
            </script>
            """)
    List<QuestionDO> questions(@Param("id") Long id, @Param("cursor") Long cursor, @Param("limit") int limit);

    @Select("""
            <script>
            select a.*, q.title as question_title, f.root_answer_id
            """ + PUBLIC_ANSWERS + """
            <if test="cursor != null">and a.id &lt; #{cursor}</if>
            order by a.id desc limit #{limit}
            </script>
            """)
    List<HrAnswerRecordDO> answers(@Param("id") Long id, @Param("cursor") Long cursor, @Param("limit") int limit);

    @Select("""
            select count(*) as question_count, coalesce(sum(answer_count), 0) as received_answer_count,
                0 as root_answer_count, 0 as followup_reply_count,
                coalesce(sum(like_count), 0) as like_count, 0 as useful_count
            from questions where author_id = #{id} and scope = 'public' and visibility = 'VISIBLE'
            """)
    Statistics studentStats(Long id);

    @Select("""
            select 0 as question_count, 0 as received_answer_count,
                coalesce(sum(case when a.followup_id is null then 1 else 0 end), 0) as root_answer_count,
                coalesce(sum(case when a.followup_id is not null then 1 else 0 end), 0) as followup_reply_count,
                coalesce(sum(a.like_count), 0) as like_count, coalesce(sum(a.useful_count), 0) as useful_count
            """ + PUBLIC_ANSWERS)
    Statistics hrStats(Long id);
}
