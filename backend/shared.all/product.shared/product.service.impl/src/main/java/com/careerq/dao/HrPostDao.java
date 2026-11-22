package com.careerq.dao;

import com.careerq.dataobject.HrPostDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Optional;

public interface HrPostDao {
    @Select("select role from users where id = #{authorId} for update")
    String lockAuthorRole(Long authorId);
    @Insert("""
            insert into hr_posts (author_id, client_key, title, body, status, version, created_at, updated_at)
            values (#{authorId}, #{clientKey}, #{title}, #{body}, 'draft', 1, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HrPostDO post);

    @Insert("""
            insert into hr_posts (author_id, client_key, title, body, status, version, created_at, updated_at, submitted_at)
            values (#{authorId}, #{clientKey}, #{title}, #{body}, 'published', 1, #{createdAt}, #{updatedAt}, #{submittedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDemoPublished(HrPostDO post);

    @Select("""
            <script>
            select p.id, p.title, p.body, p.updated_at, u.nickname as author_nickname
            from hr_posts p join users u on u.id = p.author_id
            where p.status = 'published' and u.role = 'hr'
            <if test="cursor != null">and p.id &lt; #{cursor}</if>
            order by p.id desc limit #{limit}
            </script>
            """)
    List<HrPostDO> listPublished(@Param("cursor") Long cursor, @Param("limit") int limit);

    @Select("""
            select p.id, p.title, p.body, p.updated_at, u.nickname as author_nickname
            from hr_posts p join users u on u.id = p.author_id
            where p.id = #{id} and p.status = 'published' and u.role = 'hr'
            """)
    Optional<HrPostDO> findPublished(Long id);

    @Select("select * from hr_posts where author_id = #{authorId} and client_key = #{clientKey}")
    Optional<HrPostDO> findByClientKey(@Param("authorId") Long authorId, @Param("clientKey") String clientKey);
    @Select("select * from hr_posts where id = #{id} and author_id = #{authorId} and status != 'deleted'")
    Optional<HrPostDO> findOwn(@Param("authorId") Long authorId, @Param("id") Long id);
    @Select("select * from hr_posts where id = #{id} and author_id = #{authorId} and status != 'deleted' for update")
    Optional<HrPostDO> lockOwn(@Param("authorId") Long authorId, @Param("id") Long id);

    @Select("""
            <script>
            select * from hr_posts where author_id = #{authorId} and status != 'deleted'
            <if test="status != 'all'">and status = #{status}</if>
            <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc limit #{limit}
            </script>
            """)
    List<HrPostDO> list(@Param("authorId") Long authorId, @Param("status") String status,
                        @Param("cursor") Long cursor, @Param("limit") int limit);
    @Update("""
            update hr_posts set title = #{title}, body = #{body}, status = #{status}, version = version + 1,
                submitted_at = #{submittedAt}, updated_at = #{updatedAt}
            where id = #{id} and author_id = #{authorId} and version = #{version}
            """)
    int update(HrPostDO post);
    @Delete("delete from hr_post_tags where post_id = #{postId}")
    int clearTags(Long postId);
    @Insert("insert into hr_post_tags (post_id, tag_id) values (#{postId}, #{tagId})")
    int linkTag(@Param("postId") Long postId, @Param("tagId") Long tagId);
    @Select("select t.name from hr_post_tags pt join tags t on t.id = pt.tag_id where pt.post_id = #{postId} order by t.id")
    List<String> tags(Long postId);
}
