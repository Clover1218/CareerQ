package com.careerq.dao;

import com.careerq.dataobject.HrProfileDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface HrProfileDao {

    @Insert("""
            insert into hr_profiles (user_id, company_display, industry, position, verify_status, verify_material_ref, verified_at)
            values (#{userId}, #{companyDisplay}, #{industry}, #{position}, #{verifyStatus}, #{verifyMaterialRef}, #{verifiedAt})
            """)
    int insert(HrProfileDO profile);

    @Select("select * from hr_profiles where user_id = #{userId}")
    Optional<HrProfileDO> findByUserId(Long userId);
}