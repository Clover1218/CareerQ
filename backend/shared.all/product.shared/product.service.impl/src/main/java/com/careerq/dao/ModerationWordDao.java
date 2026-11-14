package com.careerq.dao;

import com.careerq.dataobject.ModerationWordDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ModerationWordDao {

    @Insert("""
            insert into moderation_words (word, category, action, updated_at)
            values (#{word}, #{category}, #{action}, current_timestamp)
            on duplicate key update category = #{category}, action = #{action}, updated_at = current_timestamp
            """)
    int upsert(@Param("word") String word, @Param("category") String category, @Param("action") String action);

    @Select("select * from moderation_words order by id")
    List<ModerationWordDO> findAll();

    @Select("select * from moderation_words where word = #{word} for update")
    ModerationWordDO findByWord(String word);
}
