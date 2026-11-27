package com.careerq.dao;

import com.careerq.roundtable.RoundtableDtos.Candidate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface RoundtableDao {

    record PublishedAnswer(long answerId, long authorId, String nickname, String company, String position,
                           String avatarUrl, long authorAnswerCount) {}

    @Select("""
            select q.id as question_id, q.title, count(v.student_id) as votes
            from roundtable_nominations n
            join questions q on q.id = n.question_id and q.scope = 'public' and q.visibility = 'VISIBLE'
            left join roundtable_votes v on v.week_start = n.week_start and v.question_id = n.question_id
            where n.week_start = #{week}
            group by q.id, q.title
            order by votes desc, q.id
            limit #{limit}
            """)
    List<Candidate> candidates(@Param("week") LocalDate week, @Param("limit") int limit);

    @Select("""
            select q.id from questions q
            where q.scope = 'public' and q.visibility = 'VISIBLE'
              and exists (select 1 from answers a where a.question_id = q.id
                            and a.followup_id is null and a.visibility = 'VISIBLE')
            order by q.answer_count desc, q.id desc
            limit 1
            """)
    Long fallbackQuestionId();

    @Select("""
            select ranked.id, ranked.author_id, coalesce(u.nickname, '用户'), p.company_display,
                   p.position, u.avatar_url, ranked.author_answer_count
            from (
                select a.id, a.author_id, a.created_at,
                       row_number() over (partition by a.author_id order by a.created_at, a.id) as author_rank,
                       count(*) over (partition by a.author_id) as author_answer_count
                from answers a
                where a.question_id = #{questionId} and a.followup_id is null and a.visibility = 'VISIBLE'
            ) ranked
            left join users u on u.id = ranked.author_id
            left join hr_profiles p on p.user_id = u.id and u.role = 'hr'
            where ranked.author_rank = 1
            order by ranked.created_at, ranked.id
            """)
    List<PublishedAnswer> publishedAuthors(long questionId);

    @Select("select question_id from roundtable_votes where week_start = #{week} and student_id = #{studentId}")
    Long findVote(@Param("week") LocalDate week, @Param("studentId") long studentId);

    @Select("select count(*) from roundtable_nominations where week_start = #{week} and question_id = #{questionId}")
    int hasNomination(@Param("week") LocalDate week, @Param("questionId") long questionId);

    @Insert("""
            insert into roundtable_nominations (week_start, question_id) values (#{week}, #{questionId})
            on duplicate key update question_id = #{questionId}
            """)
    void nominate(@Param("week") LocalDate week, @Param("questionId") long questionId);

    @Insert("""
            insert into roundtable_votes (week_start, student_id, question_id)
            values (#{week}, #{studentId}, #{questionId})
            on duplicate key update question_id = #{questionId}
            """)
    void vote(@Param("week") LocalDate week, @Param("studentId") long studentId, @Param("questionId") long questionId);
}
