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
            update answers set status = 'removed', removal_source = #{source},
                removed_by = #{actorId}, removed_at = #{removedAt}, updated_at = #{removedAt}
            where id = #{id} and status <> 'removed'
            """)
    int markRemoved(@Param("id") Long id, @Param("source") String source,
                    @Param("actorId") Long actorId, @Param("removedAt") LocalDateTime removedAt);

    @Insert("""
            insert into answers (
                question_id, followup_id, author_id, body, length_type, status, like_count, useful_count, created_at,
                updated_at
            ) values (
                #{questionId}, #{followupId}, #{authorId}, #{body}, #{lengthType}, #{status}, #{likeCount},
                #{usefulCount}, #{createdAt}, #{updatedAt}
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

    @Select("select a.* from answers a where a.status = #{status} and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public') order by a.id")
    List<AnswerDO> findByStatusOrderByIdAsc(String status);

    @Select("select count(*) from answers a where a.status = #{status} and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')")
    long countByStatus(String status);

    @Select("select count(*) from answers a where a.author_id = #{authorId} and a.status = 'published' and exists (select 1 from questions q where q.id = a.question_id and q.scope = 'public')")
    long countPublishedByAuthor(Long authorId);

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

    @Select("select * from answers where question_id = #{questionId} and status = #{status} order by id")
    List<AnswerDO> findByQuestionIdAndStatusOrderByIdAsc(@Param("questionId") Long questionId,
                                                        @Param("status") String status);

    @Select("""
            select a.* from answers a where a.question_id = #{questionId}
            and (a.followup_id is null or exists (select 1 from followups f
                 where f.id = a.followup_id and f.question_id = a.question_id
                   and f.scope = 'public' and f.root_answer_id is not null))
            and (a.status = 'published' or a.author_id = #{authorId} and a.status in ('pending_machine', 'pending_human', 'rejected')
                 or a.followup_id is null and a.status = 'removed' and exists (
                    select 1 from followups f where f.question_id = a.question_id and f.root_answer_id = a.id and f.scope = 'public'
                      and (f.status = 'published' or f.status = 'removed' and exists (
                        select 1 from answers child where child.followup_id = f.id and child.question_id = f.question_id
                          and (child.status = 'published' or child.author_id = #{authorId}
                               and child.status in ('pending_machine', 'pending_human', 'rejected'))))))
            order by a.created_at, a.id
            """)
    List<AnswerDO> findVisible(@Param("questionId") Long questionId, @Param("authorId") Long authorId);

    @Select("select exists (select 1 from answers where question_id = #{questionId} and status = 'pending_human')")
    boolean hasPending(Long questionId);

    @Select("""
            select exists (
                select 1 from answers
                where question_id = #{questionId} and author_id = #{authorId}
                  and followup_id is null and status = 'published'
            )
            """)
    boolean hasPublishedRootAnswer(@Param("questionId") Long questionId, @Param("authorId") Long authorId);

    @Update("update answers set status = #{status}, updated_at = current_timestamp where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

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
