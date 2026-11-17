package com.careerq.dao;

import com.careerq.dataobject.TagAffinityDO;
import com.careerq.dataobject.TagSignalDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface TagAffinityDao {

    @Select("select * from user_tag_affinity where user_id = #{userId}")
    List<TagAffinityDO> findByUser(Long userId);

    @Select("select * from user_tag_affinity where user_id = #{userId} and tag_id = #{tagId} for update")
    Optional<TagAffinityDO> findForUpdate(@Param("userId") Long userId, @Param("tagId") Long tagId);

    @Insert("""
            insert into user_tag_affinity (user_id, tag_id, score, computed_at, created_at, updated_at)
            values (#{userId}, #{tagId}, #{score}, #{computedAt}, #{createdAt}, #{updatedAt})
            """)
    int insert(TagAffinityDO row);

    @Update("""
            update user_tag_affinity
            set score = #{score}, computed_at = #{computedAt}, updated_at = #{updatedAt}
            where user_id = #{userId} and tag_id = #{tagId}
            """)
    int update(TagAffinityDO row);

    /** 当前仍存在的「问题」互动 → 标签信号；取消互动后行已删除，重算自然回落。 */
    @Select("""
            select qt.tag_id, i.type, i.created_at
            from interactions i
            join question_tags qt on qt.question_id = i.target_id
            where i.user_id = #{userId}
              and i.target_type = 'question'
              and i.type in ('like', 'useful', 'favorite', 'dislike')
            """)
    List<TagSignalDO> findQuestionSignals(Long userId);
}
