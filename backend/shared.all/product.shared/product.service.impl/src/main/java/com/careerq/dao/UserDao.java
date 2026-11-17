package com.careerq.dao;

import com.careerq.dataobject.UserDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

public interface UserDao {

    @Insert("""
            insert into users (
                role, phone_hash, phone_enc, phone_verified_at, nickname, school, major, grade, age_group,
                status, created_at, updated_at
            ) values (
                #{role}, #{phoneHash}, #{phoneEnc}, #{phoneVerifiedAt}, #{nickname}, #{school}, #{major},
                #{grade}, #{ageGroup}, #{status}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserDO user);

    @Select("select * from users where id = #{id}")
    Optional<UserDO> findById(Long id);

    @Select("select * from users where phone_hash = #{phoneHash}")
    Optional<UserDO> findByPhoneHash(String phoneHash);

    @Select("select count(*) from users")
    long count();

    @Update("update users set role = #{role}, updated_at = current_timestamp where id = #{id}")
    int updateRole(UserDO user);

    @Update("""
            update users set school = #{school}, major = #{major}, grade = #{grade},
                degree = #{degree}, graduation_year = #{graduationYear}, updated_at = current_timestamp
            where id = #{id}
            """)
    int updateProfile(UserDO user);
}
