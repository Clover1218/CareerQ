package com.careerq.question;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByStatusOrderByIdAsc(String status);

    long countByStatus(String status);

    Slice<Question> findByScopeAndStatusOrderByIdDesc(String scope, String status, Pageable pageable);

    Slice<Question> findByScopeAndStatusAndIdLessThanOrderByIdDesc(
            String scope, String status, Long cursor, Pageable pageable);

    Slice<Question> findByScopeAndStatusAndAnswerCountOrderByIdDesc(
            String scope, String status, int answerCount, Pageable pageable);

    Slice<Question> findByScopeAndStatusAndAnswerCountAndIdLessThanOrderByIdDesc(
            String scope, String status, int answerCount, Long cursor, Pageable pageable);

    @Query("""
            select distinct q from Question q join q.tags t
            where q.scope = :scope and q.status = :status and t.slug = :tag
            order by q.id desc
            """)
    Slice<Question> findPublishedByTag(@Param("scope") String scope,
                                       @Param("status") String status,
                                       @Param("tag") String tag,
                                       Pageable pageable);

    @Query("""
            select distinct q from Question q join q.tags t
            where q.scope = :scope and q.status = :status and t.slug = :tag and q.id < :cursor
            order by q.id desc
            """)
    Slice<Question> findPublishedByTagBefore(@Param("scope") String scope,
                                             @Param("status") String status,
                                             @Param("tag") String tag,
                                             @Param("cursor") Long cursor,
                                             Pageable pageable);
}
