package com.careerq.dao;

import com.careerq.dataobject.ExperienceDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExperienceDao {

    @Select("select * from user_experiences where user_id = #{userId} order by id asc")
    List<ExperienceDO> findByUserId(Long userId);

    @Delete("delete from user_experiences where user_id = #{userId}")
    int deleteByUserId(Long userId);

    @Insert("""
            insert into user_experiences (
                user_id, type, title, role, start_date, end_date, description, created_at, updated_at
            ) values (
                #{userId}, #{type}, #{title}, #{role}, #{startDate}, #{endDate}, #{description},
                #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExperienceDO experience);
}
