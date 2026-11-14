package com.careerq.dao;

import com.careerq.dataobject.InteractionDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

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
}
