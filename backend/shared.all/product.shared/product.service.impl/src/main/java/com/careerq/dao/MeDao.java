package com.careerq.dao;

import com.careerq.dataobject.MeStatsDO;
import org.apache.ibatis.annotations.Select;

public interface MeDao {

    @Select("""
            select
                (select count(*) from questions q
                    where q.author_id = #{userId} and q.status <> 'removed') as question_count,
                (select coalesce(sum(q.answer_count), 0) from questions q
                    where q.author_id = #{userId} and q.status <> 'removed') as received_answer_count,
                (select count(*) from answers a
                    where a.author_id = #{userId} and a.status = 'published') as authored_answer_count,
                (select coalesce(sum(q.like_count), 0) from questions q
                    where q.author_id = #{userId} and q.status <> 'removed') as question_like_count,
                (select coalesce(sum(a.like_count), 0) from answers a
                    where a.author_id = #{userId} and a.status = 'published') as answer_like_count
            """)
    MeStatsDO stats(Long userId);
}
