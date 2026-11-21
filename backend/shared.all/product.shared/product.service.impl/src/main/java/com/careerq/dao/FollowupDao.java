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
                question_id, hr_id, root_answer_id, sender_id, sender_role, body, scope, status, created_at
            ) values (
                #{questionId}, #{hrId}, #{rootAnswerId}, #{senderId}, #{senderRole}, #{body}, #{scope}, #{status}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FollowupDO followup);

    @Select("""
            select f.* from followups f where f.scope = 'public' and f.question_id = #{questionId}
            and f.root_answer_id is not null
            and (f.status = 'published' or f.sender_id = #{senderId} and f.status in ('pending_machine', 'pending_human', 'rejected')
                 or f.status = 'removed' and exists (
                    select 1 from answers a where a.question_id = f.question_id and a.followup_id = f.id
                      and (a.status = 'published' or a.author_id = #{senderId}
                           and a.status in ('pending_machine', 'pending_human', 'rejected'))))
            order by f.created_at, f.id
            """)
    List<FollowupDO> findVisible(@Param("questionId") Long questionId, @Param("senderId") Long senderId);

    @Select("select * from followups where scope = 'public' and root_answer_id is not null and status = 'pending_human' order by id")
    List<FollowupDO> findPending();

    @Select("select * from followups where id = #{id} and scope = 'public' and root_answer_id is not null for update")
    Optional<FollowupDO> findByIdForUpdate(Long id);

    @Select("select * from followups where id = #{id} and scope = 'public' and root_answer_id is not null")
    Optional<FollowupDO> findById(Long id);

    @Update("update followups set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
