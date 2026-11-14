package com.careerq.dao;

import com.careerq.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

/**
 * 用户 DAO（MyBatis Mapper）。SSM 方式访问 MySQL/H2。
 */
@Mapper
public interface UserDao {

    @Select("select id, role, phone_hash, nickname, school, major, grade, age_group, status, created_at, updated_at "
            + "from users where id = #{id}")
    UserDO selectById(Long id);

    @Select("select count(*) from users")
    long count();

    @Insert("insert into users(role, phone_hash, nickname, school, major, grade, age_group, status, created_at, updated_at) "
            + "values(#{role}, #{phoneHash}, #{nickname}, #{school}, #{major}, #{grade}, #{ageGroup}, #{status}, "
            + "#{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserDO user);
}
