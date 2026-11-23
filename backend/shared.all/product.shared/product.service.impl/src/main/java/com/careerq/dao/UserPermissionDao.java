package com.careerq.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 额外授权（目前仅 arbitrate），与 users.role 是“或”的关系。 */
public interface UserPermissionDao {

    @Select("select permission from user_permissions where user_id = #{userId} order by permission")
    List<String> findPermissions(@Param("userId") Long userId);

    @Insert("""
            insert into user_permissions (user_id, permission, created_at)
            values (#{userId}, #{permission}, current_timestamp)
            """)
    int insert(@Param("userId") Long userId, @Param("permission") String permission);
}