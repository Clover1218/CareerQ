package com.careerq.dao;

import com.careerq.dataobject.PublicPostDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PublicPostDao {

    @Insert("""
            insert into public_posts (
                original_question_id, title, question_body, hr_answer, anonymized, status, published_at,
                created_at
            ) values (
                #{originalQuestionId}, #{title}, #{questionBody}, #{hrAnswer}, #{anonymized}, #{status},
                #{publishedAt}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PublicPostDO publicPost);

    @Select("select * from public_posts where id = #{id}")
    Optional<PublicPostDO> findById(Long id);

    @Select("select * from public_posts where status = #{status} order by id")
    List<PublicPostDO> findByStatusOrderByIdAsc(String status);

    @Select("select count(*) from public_posts where status = #{status}")
    long countByStatus(String status);

    @Select("select * from public_posts where original_question_id = #{originalQuestionId}")
    Optional<PublicPostDO> findByOriginalQuestionId(Long originalQuestionId);

    @Select("""
            <script>
            select * from public_posts where status = 'published'
            order by published_at desc
            <if test="limit != null">limit #{limit}</if>
            </script>
            """)
    List<PublicPostDO> findPublished(@Param("limit") Integer limit);

    @Update("""
            <script>
            update public_posts set status = #{status}
            <if test="publishedAt != null">, published_at = #{publishedAt}</if>
            where id = #{id}
            </script>
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("publishedAt") LocalDateTime publishedAt);
}
