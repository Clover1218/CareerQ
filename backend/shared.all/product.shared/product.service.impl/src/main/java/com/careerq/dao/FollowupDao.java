package com.careerq.dao;

import com.careerq.dataobject.FollowupDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FollowupDao {

    @Insert("""
            insert into followups (
                question_id, sender_id, sender_role, body, scope, status, created_at
            ) values (
                #{questionId}, #{senderId}, #{senderRole}, #{body}, #{scope}, #{status}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FollowupDO followup);

    @Select("select * from followups where question_id = #{questionId} and status = #{status} order by id")
    List<FollowupDO> findByQuestionIdAndStatusOrderByIdAsc(@Param("questionId") Long questionId,
                                                          @Param("status") String status);
}
