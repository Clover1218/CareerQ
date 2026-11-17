package com.careerq.dao;

import com.careerq.dataobject.QuestionTagDO;
import com.careerq.dataobject.TagDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

public interface TagDao {

    @Insert("""
            insert into tags (
                name, slug, category, weight
            ) values (
                #{name}, #{slug}, #{category}, #{weight}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TagDO tag);

    @Select("select * from tags where slug = #{slug}")
    Optional<TagDO> findBySlug(String slug);

    @Select("select * from tags where slug = #{slug} for update")
    Optional<TagDO> findBySlugForUpdate(String slug);

    /** 固定目录标签：category 为空的是历史上自动创建的自定义标签，不进入选择器。 */
    @Select("""
            select * from tags
            where category is not null
            order by weight desc, name
            """)
    List<TagDO> findCategorized();

    @Select("""
            select * from tags
            where category = #{category}
            order by weight desc, name
            limit #{limit}
            """)
    List<TagDO> findByCategory(@Param("category") String category, @Param("limit") int limit);

    @Select("""
            select * from tags
            where category is not null
              and (lower(name) like lower(concat('%', #{keyword}, '%'))
                   or lower(slug) like lower(concat('%', #{keyword}, '%')))
            order by weight desc, name
            limit #{limit}
            """)
    List<TagDO> search(@Param("keyword") String keyword, @Param("limit") int limit);

    @Select("""
            select * from tags
            where category is not null
            order by weight desc, name
            limit #{limit}
            """)
    List<TagDO> findRecommended(@Param("limit") int limit);

    @Insert("insert into question_tags (question_id, tag_id) values (#{questionId}, #{tagId})")
    int linkToQuestion(@Param("questionId") Long questionId, @Param("tagId") Long tagId);

    @Select("""
            select t.name from question_tags qt join tags t on t.id = qt.tag_id
            where qt.question_id = #{questionId} order by t.id
            """)
    List<String> findNamesByQuestionId(Long questionId);

    /** Feed 批量取标签，避免逐条查询造成 N+1。 */
    @Select("""
            <script>
            select qt.question_id, t.id as tag_id, t.name, t.slug, t.category, t.weight
            from question_tags qt join tags t on t.id = qt.tag_id
            where qt.question_id in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            order by qt.question_id, t.id
            </script>
            """)
    List<QuestionTagDO> findTagsByQuestionIds(@Param("ids") List<Long> questionIds);
}
