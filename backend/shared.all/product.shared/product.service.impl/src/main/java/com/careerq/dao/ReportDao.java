package com.careerq.dao;

import com.careerq.dataobject.ReportDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface ReportDao {

    String CURRENT_CONTENT = "((r.target_type = 'question' and exists (select 1 from questions q "
            + "where q.id = r.target_id and q.scope = 'public')) "
            + "or (r.target_type = 'answer' and exists (select 1 from answers a join questions q "
            + "on q.id = a.question_id where a.id = r.target_id and q.scope = 'public')))";


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

    @Select("select r.* from reports r where r.id = #{id} and " + CURRENT_CONTENT + " for update")
    Optional<ReportDO> findByIdForUpdate(Long id);

    @Select("select r.* from reports r where r.status = #{status} and " + CURRENT_CONTENT + " order by r.id")
    List<ReportDO> findByStatusOrderByIdAsc(String status);

    @Select("select count(*) from reports r where r.status = #{status} and " + CURRENT_CONTENT)
    long countByStatus(String status);

    @Update("update reports set status = #{status}, handled_by = #{handledBy}, handled_at = #{handledAt} where id = #{id}")
    int updateResolution(ReportDO report);
}
