package com.careerq.dao;

import com.careerq.dataobject.FollowupDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface FollowupDao {

    @Insert("""
            insert into followups (
                question_id, hr_id, sender_id, sender_role, body, scope, status, created_at
            ) values (
                #{questionId}, #{hrId}, #{senderId}, #{senderRole}, #{body}, #{scope}, #{status}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FollowupDO followup);

    @Select("""
            select * from followups where question_id = #{questionId}
            and (status = 'published' or sender_id = #{senderId} and status in ('pending_human', 'rejected'))
            order by id
            """)
    List<FollowupDO> findVisible(@Param("questionId") Long questionId, @Param("senderId") Long senderId);

    @Select("select * from followups where status = 'pending_human' order by id")
    List<FollowupDO> findPending();

    @Select("select * from followups where id = #{id} for update")
    Optional<FollowupDO> findByIdForUpdate(Long id);

    @Update("update followups set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
