package com.careerq.dao;

import com.careerq.dataobject.InteractionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InteractionDao {

    @Insert("""
            insert into interactions (
                user_id, target_type, target_id, type, created_at
            ) values (
                #{userId}, #{targetType}, #{targetId}, #{type}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InteractionDO interaction);

    @Delete("""
            delete from interactions
            where user_id = #{userId} and target_type = #{targetType}
              and target_id = #{targetId} and type = #{type}
            """)
    int delete(@Param("userId") Long userId,
               @Param("targetType") String targetType,
               @Param("targetId") Long targetId,
               @Param("type") String type);

    @Select("""
            select exists (
                select 1 from interactions
                where user_id = #{userId} and target_type = #{targetType}
                  and target_id = #{targetId} and type = #{type}
            )
            """)
    boolean exists(@Param("userId") Long userId,
                   @Param("targetType") String targetType,
                   @Param("targetId") Long targetId,
                   @Param("type") String type);

    /** 一页问题一次查询，仅返回当前会话用户本人的互动。 */
    @Select("""
            <script>
            select target_id, type from interactions
            where user_id = #{userId} and target_type = 'question'
            <choose>
              <when test="questionIds != null and !questionIds.isEmpty()">
                and target_id in
                <foreach collection="questionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
              </when>
              <otherwise>and 1 = 0</otherwise>
            </choose>
            </script>
            """)
    List<InteractionDO> findQuestionInteractions(@Param("userId") Long userId,
                                                @Param("questionIds") List<Long> questionIds);

    @Select("""
            <script>
            select i.* from interactions i
            left join answers a on i.target_type = 'answer' and a.id = i.target_id
            join questions q on q.id = case when i.target_type = 'question' then i.target_id else a.question_id end
            where i.user_id = #{userId} and i.type = 'favorite' and i.target_type in ('question', 'answer')
              and q.scope = 'public' and q.status = 'published'
              and (i.target_type = 'question' or a.status = 'published')
            <if test="targetType != null">and i.target_type = #{targetType}</if>
            <if test="cursor != null">and i.id &lt; #{cursor}</if>
            order by i.id desc limit #{limit}
            </script>
            """)
    List<InteractionDO> findVisibleFavorites(@Param("userId") Long userId, @Param("targetType") String targetType,
                                            @Param("cursor") Long cursor, @Param("limit") int limit);

    @Select("""
            select count(*) from interactions i
            where i.type = 'like' and (
                (i.target_type = 'question' and exists (
                    select 1 from questions q where q.id = i.target_id and q.scope = 'public' and q.author_id = #{authorId}
                )) or
                (i.target_type = 'answer' and exists (
                    select 1 from answers a join questions q on q.id = a.question_id
                    where a.id = i.target_id and q.scope = 'public' and a.author_id = #{authorId}
                ))
            )
            """)
    long countLikesReceived(Long authorId);

    @Select("""
            <script>
            select * from interactions
            where user_id = #{userId} and type = #{type} and target_type in ('question', 'answer')
            <if test="targetType != null"> and target_type = #{targetType}</if>
            <if test="cursor != null"> and id &lt; #{cursor}</if>
            order by id desc limit #{limit}
            </script>
            """)
    List<InteractionDO> findByUserAndType(@Param("userId") Long userId,
                                          @Param("type") String type,
                                          @Param("targetType") String targetType,
                                          @Param("cursor") Long cursor,
                                          @Param("limit") int limit);
}
