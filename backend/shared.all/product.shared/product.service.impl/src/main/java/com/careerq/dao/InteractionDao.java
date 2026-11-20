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

    @Select("""
            <script>
            select target_id, type from interactions
            where user_id = #{userId} and target_type = #{targetType}
            and
            <choose>
                <when test="targetIds != null and !targetIds.isEmpty()">
                    target_id in
                    <foreach collection="targetIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                </when>
                <otherwise>1 = 0</otherwise>
            </choose>
            </script>
            """)
    List<InteractionDO> findByUserAndTargets(@Param("userId") Long userId,
                                            @Param("targetType") String targetType,
                                            @Param("targetIds") List<Long> targetIds);

    @Select("""
            select count(*) from interactions i
            where i.type = 'like' and (
                (i.target_type = 'question' and exists (
                    select 1 from questions q where q.id = i.target_id and q.author_id = #{authorId}
                )) or
                (i.target_type = 'answer' and exists (
                    select 1 from answers a where a.id = i.target_id and a.author_id = #{authorId}
                ))
            )
            """)
    long countLikesReceived(Long authorId);

    @Select("""
            <script>
            select * from interactions
            where user_id = #{userId} and type = #{type}
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
