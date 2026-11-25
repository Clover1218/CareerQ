package com.careerq.dao;

import com.careerq.dataobject.UserDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
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

    @Update("""
            <script>
            update users
            <set>
                <if test="educationPublic != null">education_public = #{educationPublic},</if>
                <if test="publicBio != null">public_bio = #{publicBio},</if>
                updated_at = current_timestamp
            </set>
            where id = #{id} and role = 'student'
            </script>
            """)
    int updatePublicProfile(@Param("id") Long id, @Param("educationPublic") Boolean educationPublic,
                            @Param("publicBio") String publicBio);
    @Update("update users set avatar_url = #{avatarUrl}, updated_at = current_timestamp where id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}
