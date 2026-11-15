package com.careerq.dao;

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

    @Insert("insert into question_tags (question_id, tag_id) values (#{questionId}, #{tagId})")
    int linkToQuestion(@Param("questionId") Long questionId, @Param("tagId") Long tagId);

    @Select("""
            select t.name from question_tags qt join tags t on t.id = qt.tag_id
            where qt.question_id = #{questionId} order by t.id
            """)
    List<String> findNamesByQuestionId(Long questionId);
}
