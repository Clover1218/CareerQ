package com.careerq.dao;

import com.careerq.dataobject.AnswerDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface AnswerDao {

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

    @Select("select * from answers where id = #{id}")
    Optional<AnswerDO> findById(Long id);

    @Select("select * from answers where id = #{id} for update")
    Optional<AnswerDO> findByIdForUpdate(Long id);

    @Select("select * from answers where status = #{status} order by id")
    List<AnswerDO> findByStatusOrderByIdAsc(String status);

    @Select("select count(*) from answers where status = #{status}")
    long countByStatus(String status);

    @Select("select count(*) from answers where author_id = #{authorId} and status = 'published'")
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
            select * from answers where question_id = #{questionId}
            and (status = 'published' or author_id = #{authorId} and status in ('pending_human', 'rejected'))
            order by id
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
