package com.careerq.dao;

import com.careerq.common.UserBrief;
import com.careerq.dataobject.UserDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
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

    @Select("""
            <script>
            select id, nickname from users
            where
            <choose>
                <when test="ids != null and !ids.isEmpty()">
                    id in
                    <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                </when>
                <otherwise>1 = 0</otherwise>
            </choose>
            </script>
            """)
    List<UserBrief> findBriefsByIds(@Param("ids") List<Long> ids);

    @Select("select * from users where phone_hash = #{phoneHash}")
    Optional<UserDO> findByPhoneHash(String phoneHash);

    @Select("select count(*) from users")
    long count();

    @Update("update users set role = #{role}, updated_at = current_timestamp where id = #{id}")
    int updateRole(UserDO user);

    @Update("""
            update users set school = #{school}, major = #{major}, grade = #{grade},
                degree = #{degree}, graduation_year = #{graduationYear}, avatar_url = #{avatarUrl},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateProfile(UserDO user);

    @Update("""
            <script>
            update users
            <set>
                <if test="nickname != null">nickname = #{nickname},</if>
                <if test="school != null">school = #{school},</if>
                <if test="major != null">major = #{major},</if>
                <if test="grade != null">grade = #{grade},</if>
                <if test="degree != null">degree = #{degree},</if>
                <if test="graduationYear != null">graduation_year = #{graduationYear},</if>
                updated_at = current_timestamp
            </set>
            where id = #{id}
            </script>
            """)
    int patchProfile(UserDO user);
    @Update("update users set avatar_url = #{avatarUrl}, updated_at = current_timestamp where id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}
