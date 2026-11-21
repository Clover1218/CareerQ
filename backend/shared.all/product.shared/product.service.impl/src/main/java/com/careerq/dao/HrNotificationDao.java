package com.careerq.dao;

import com.careerq.dataobject.HrNotificationDO;
import com.careerq.hr.HrNotificationDtos.HrUnreadCounts;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Optional;

public interface HrNotificationDao {
    @Insert("""
            insert into hr_notifications (recipient_id, event_key, type, category, question_id, answer_id,
                followup_id, created_at)
            values (#{recipientId}, #{eventKey}, #{type}, #{category}, #{questionId}, #{answerId},
                #{followupId}, #{createdAt})
            on duplicate key update event_key = event_key
            """)
    int insert(HrNotificationDO notification);

    @Select("""
            <script>
            select * from hr_notifications where recipient_id = #{recipientId}
              and id &lt;= #{snapshotId}
            <if test="category != 'all'">and category = #{category}</if>
            <if test="unreadOnly">and read_at is null</if>
            <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc limit #{limit}
            </script>
            """)
    List<HrNotificationDO> list(@Param("recipientId") Long recipientId, @Param("category") String category,
                                 @Param("unreadOnly") boolean unreadOnly, @Param("cursor") Long cursor,
                                 @Param("snapshotId") long snapshotId,
                                 @Param("limit") int limit);

    @Select("""
            select coalesce(sum(case when read_at is null then 1 else 0 end), 0) as total,
              coalesce(sum(case when read_at is null and category = 'followup' then 1 else 0 end), 0) as followup,
              coalesce(sum(case when read_at is null and category = 'system' then 1 else 0 end), 0) as `system`,
              coalesce(max(id), 0) as snapshot_id
            from hr_notifications where recipient_id = #{recipientId}
            """)
    HrUnreadCounts unread(Long recipientId);

    @Select("select coalesce(max(id), 0) from hr_notifications where recipient_id = #{recipientId}")
    long snapshot(Long recipientId);

    @Select("select * from hr_notifications where id = #{id} and recipient_id = #{recipientId}")
    Optional<HrNotificationDO> find(@Param("recipientId") Long recipientId, @Param("id") Long id);

    @Update("update hr_notifications set read_at = coalesce(read_at, current_timestamp) where id = #{id} and recipient_id = #{recipientId}")
    int read(@Param("recipientId") Long recipientId, @Param("id") Long id);

    @Update("update hr_notifications set read_at = current_timestamp where recipient_id = #{recipientId} and id <= #{throughId} and read_at is null")
    int readAll(@Param("recipientId") Long recipientId, @Param("throughId") long throughId);
}
