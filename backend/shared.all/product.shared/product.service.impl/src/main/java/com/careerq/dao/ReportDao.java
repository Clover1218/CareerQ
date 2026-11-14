package com.careerq.dao;

import com.careerq.dataobject.ReportDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface ReportDao {

    @Insert("""
            insert into reports (
                reporter_id, target_type, target_id, reason, detail, status, handled_by, handled_at,
                created_at
            ) values (
                #{reporterId}, #{targetType}, #{targetId}, #{reason}, #{detail}, #{status}, #{handledBy},
                #{handledAt}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReportDO report);

    @Select("select * from reports where id = #{id} for update")
    Optional<ReportDO> findByIdForUpdate(Long id);

    @Select("select * from reports where status = #{status} order by id")
    List<ReportDO> findByStatusOrderByIdAsc(String status);

    @Select("select count(*) from reports where status = #{status}")
    long countByStatus(String status);

    @Update("update reports set status = #{status}, handled_by = #{handledBy}, handled_at = #{handledAt} where id = #{id}")
    int updateResolution(ReportDO report);
}
