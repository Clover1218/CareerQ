package com.careerq.dao;

import com.careerq.dataobject.QuestionDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface QuestionDao {

    @Insert("""
            insert into questions (
                scope, author_id, title, body, status, assigned_hr_id, closed_at, first_answer_at,
                answer_count, useful_count, created_at, updated_at
            ) values (
                #{scope}, #{authorId}, #{title}, #{body}, #{status}, #{assignedHrId}, #{closedAt},
                #{firstAnswerAt}, #{answerCount}, #{usefulCount}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QuestionDO question);

    @Select("select * from questions where id = #{id}")
    Optional<QuestionDO> findById(Long id);

    @Select("select * from questions where id = #{id} for update")
    Optional<QuestionDO> findByIdForUpdate(Long id);

    @Select("select * from questions where status = #{status} order by id")
    List<QuestionDO> findByStatusOrderByIdAsc(String status);

    @Select("select count(*) from questions where status = #{status}")
    long countByStatus(String status);

    @Select("""
            <script>
            select q.* from questions q
            where q.scope = #{scope} and q.status = #{status}
            <if test="cursor != null">and q.id &lt; #{cursor}</if>
            <if test="unanswered">and q.answer_count = 0</if>
            <if test="tag != null">
                and exists (
                    select 1 from question_tags qt join tags t on t.id = qt.tag_id
                    where qt.question_id = q.id and t.slug = #{tag}
                )
            </if>
            order by q.id desc limit #{limit}
            </script>
            """)
    List<QuestionDO> list(@Param("scope") String scope, @Param("status") String status,
                          @Param("cursor") Long cursor, @Param("tag") String tag,
                          @Param("unanswered") boolean unanswered, @Param("limit") int limit);

    @Update("update questions set status = #{status}, updated_at = current_timestamp where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("""
            update questions set status = #{status}, assigned_hr_id = #{assignedHrId},
                closed_at = #{closedAt}, updated_at = current_timestamp
            where id = #{id}
            """)
    int updatePrivateState(QuestionDO question);

    @Update("""
            update questions set answer_count = answer_count + 1,
                first_answer_at = coalesce(first_answer_at, current_timestamp), updated_at = current_timestamp
            where id = #{id}
            """)
    int recordAnswer(Long id);

    @Update("""
            update questions set useful_count = useful_count + 1, updated_at = current_timestamp where id = #{id}
            """)
    int incrementUseful(Long id);

    @Update("""
            update questions set useful_count = case when useful_count > 0 then useful_count - 1 else 0 end,
                updated_at = current_timestamp
            where id = #{id}
            """)
    int decrementUseful(Long id);

    @Update("""
            update questions set like_count = like_count + 1, updated_at = current_timestamp where id = #{id}
            """)
    int incrementLike(Long id);

    @Update("""
            update questions set like_count = case when like_count > 0 then like_count - 1 else 0 end,
                updated_at = current_timestamp
            where id = #{id}
            """)
    int decrementLike(Long id);
}
