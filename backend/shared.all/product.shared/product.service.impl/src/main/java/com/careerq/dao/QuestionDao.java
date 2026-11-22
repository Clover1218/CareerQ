package com.careerq.dao;

import com.careerq.dataobject.QuestionDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuestionDao {

    @Insert("""
            insert into questions (
                scope, author_id, title, body, moderation_status, visibility, risk_level,
                moderation_reason, moderated_at, first_answer_at, answer_count, useful_count,
                created_at, updated_at
            ) values (
                #{scope}, #{authorId}, #{title}, #{body}, #{moderationStatus}, #{visibility}, #{riskLevel},
                #{moderationReason}, #{moderatedAt}, #{firstAnswerAt}, #{answerCount}, #{usefulCount},
                #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QuestionDO question);

    @Select("select * from questions where id = #{id} and scope = 'public'")
    Optional<QuestionDO> findById(Long id);

    @Select("select * from questions where id = #{id} and scope = 'public' for update")
    Optional<QuestionDO> findByIdForUpdate(Long id);

    @Select("select count(*) from questions where scope = 'public' and moderation_status = #{status}")
    long countByModerationStatus(String status);

    @Select("select count(*) from questions where scope = 'public' and visibility = #{visibility}")
    long countByVisibility(String visibility);

    @Select("""
            select count(*) from questions
            where scope = 'public' and moderation_status = #{status} and visibility = #{visibility}
            """)
    long countByModerationStatusAndVisibility(@Param("status") String status,
                                              @Param("visibility") String visibility);

    @Select("select * from questions where scope = 'public' and moderation_status = 'L3' order by id")
    List<QuestionDO> findPending();

    @Select("select count(*) from questions where scope = 'public' and author_id = #{authorId} and created_at >= #{since}")
    long countByAuthorSince(@Param("authorId") Long authorId, @Param("since") LocalDateTime since);

    @Select("""
            <script>
            select q.* from questions q
            where q.scope = #{scope} and q.visibility = #{visibility}
            <if test="cursor != null">and q.id &lt; #{cursor}</if>
            <if test="unanswered">
                and q.answer_count = 0
                and not exists (
                    select 1 from answers a where a.question_id = q.id
                      and a.visibility &lt;&gt; 'REMOVED' and a.moderation_status &lt;&gt; 'REJECTED'
                )
            </if>
            <if test="tag != null">
                and exists (
                    select 1 from question_tags qt join tags t on t.id = qt.tag_id
                    where qt.question_id = q.id and t.slug = #{tag}
                )
            </if>
            order by q.id desc limit #{limit}
            </script>
            """)
    List<QuestionDO> list(@Param("scope") String scope, @Param("visibility") String visibility,
                          @Param("cursor") Long cursor, @Param("tag") String tag,
                          @Param("unanswered") boolean unanswered, @Param("limit") int limit);

    @Select("select coalesce(max(id), 0) from questions where scope = #{scope} and visibility = #{visibility}")
    long findMaxId(@Param("scope") String scope, @Param("visibility") String visibility);

    /**
     * 推荐/热门候选池：固定快照上界，支持关键词、多标签（AND/OR）与时间范围过滤，
     * 并排除当前用户已“不喜欢”的问题。候选按 id 倒序截断。
     */
    @Select("""
            <script>
            select q.* from questions q
            where q.scope = #{scope} and q.visibility = #{visibility}
              and q.id &lt;= #{maxId}
            <if test="since != null">and q.created_at &gt;= #{since}</if>
            <if test="until != null">and q.created_at &lt; #{until}</if>
            <if test="keyword != null">
                and (locate(#{keyword}, lower(q.title)) &gt; 0 or locate(#{keyword}, lower(q.body)) &gt; 0)
            </if>
            <if test="tags != null and tags.size() &gt; 0">
                <choose>
                    <when test="matchAll">
                        and (select count(distinct t.id) from question_tags qt join tags t on t.id = qt.tag_id
                             where qt.question_id = q.id and t.slug in
                             <foreach collection="tags" item="tag" open="(" separator="," close=")">#{tag}</foreach>
                        ) = #{tagCount}
                    </when>
                    <otherwise>
                        and exists (
                            select 1 from question_tags qt join tags t on t.id = qt.tag_id
                            where qt.question_id = q.id and t.slug in
                            <foreach collection="tags" item="tag" open="(" separator="," close=")">#{tag}</foreach>
                        )
                    </otherwise>
                </choose>
            </if>
            <if test="excludeUserId != null">
                and not exists (
                    select 1 from interactions i
                    where i.user_id = #{excludeUserId} and i.target_type = 'question'
                      and i.target_id = q.id and i.type = 'dislike'
                )
            </if>
            order by q.id desc limit #{limit}
            </script>
            """)
    List<QuestionDO> listFilteredCandidates(@Param("scope") String scope, @Param("visibility") String visibility,
                                            @Param("maxId") long maxId, @Param("since") LocalDateTime since,
                                            @Param("until") LocalDateTime until, @Param("keyword") String keyword,
                                            @Param("tags") List<String> tags, @Param("tagCount") int tagCount,
                                            @Param("matchAll") boolean matchAll,
                                            @Param("excludeUserId") Long excludeUserId, @Param("limit") int limit);

    /** 最新排序：按 (createdAt, id) 做 seek 分页，支持同一套关键词/标签/时间过滤。 */
    @Select("""
            <script>
            select q.* from questions q
            where q.scope = #{scope} and q.visibility = #{visibility}
            <if test="lastCreatedAt != null">
              and (q.created_at &lt; #{lastCreatedAt}
                   or (q.created_at = #{lastCreatedAt} and q.id &lt; #{lastId}))
            </if>
            <if test="from != null">and q.created_at &gt;= #{from}</if>
            <if test="to != null">and q.created_at &lt; #{to}</if>
            <if test="keyword != null">
                and (locate(#{keyword}, lower(q.title)) &gt; 0 or locate(#{keyword}, lower(q.body)) &gt; 0)
            </if>
            <if test="tags != null and tags.size() &gt; 0">
                <choose>
                    <when test="matchAll">
                        and (select count(distinct t.id) from question_tags qt join tags t on t.id = qt.tag_id
                             where qt.question_id = q.id and t.slug in
                             <foreach collection="tags" item="tag" open="(" separator="," close=")">#{tag}</foreach>
                        ) = #{tagCount}
                    </when>
                    <otherwise>
                        and exists (
                            select 1 from question_tags qt join tags t on t.id = qt.tag_id
                            where qt.question_id = q.id and t.slug in
                            <foreach collection="tags" item="tag" open="(" separator="," close=")">#{tag}</foreach>
                        )
                    </otherwise>
                </choose>
            </if>
            <if test="excludeUserId != null">
                and not exists (
                    select 1 from interactions i
                    where i.user_id = #{excludeUserId} and i.target_type = 'question'
                      and i.target_id = q.id and i.type = 'dislike'
                )
            </if>
            order by q.created_at desc, q.id desc
            limit #{limit}
            </script>
            """)
    List<QuestionDO> listLatest(@Param("scope") String scope, @Param("visibility") String visibility,
                                @Param("keyword") String keyword, @Param("tags") List<String> tags,
                                @Param("tagCount") int tagCount, @Param("matchAll") boolean matchAll,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                @Param("lastCreatedAt") LocalDateTime lastCreatedAt, @Param("lastId") long lastId,
                                @Param("excludeUserId") Long excludeUserId, @Param("limit") int limit);

    @Select("""
            <script>
            select * from questions where scope = 'public' and author_id = #{authorId}
            <if test="scope != null">and scope = #{scope}</if>
            <if test="cursor != null">and id &lt; #{cursor}</if>
            order by id desc limit #{limit}
            </script>
            """)
    List<QuestionDO> listByAuthor(@Param("authorId") Long authorId, @Param("scope") String scope,
                                  @Param("cursor") Long cursor, @Param("limit") int limit);

    @Select("select count(*) from questions where scope = 'public' and author_id = #{authorId}")
    long countByAuthor(Long authorId);

    @Select("select coalesce(sum(answer_count), 0) from questions where scope = 'public' and author_id = #{authorId}")
    long countReceivedAnswers(Long authorId);

    @Select("""
            <script>
            select q.* from questions q
            where q.scope = 'public' and q.visibility = 'VISIBLE'
              and exists (
                  select 1 from answers a
                  where a.question_id = q.id and a.author_id = #{hrId}
                    and (a.followup_id is null or exists (select 1 from followups f
                         where f.id = a.followup_id and f.question_id = q.id
                           and f.scope = 'public' and f.root_answer_id is not null))
              )
            <if test="cursor != null">and q.id &lt; #{cursor}</if>
            order by q.id desc limit #{limit}
            </script>
            """)
    List<QuestionDO> listPublicAnsweredByHr(@Param("hrId") Long hrId,
                                             @Param("cursor") Long cursor,
                                             @Param("limit") int limit);

    @Select("""
            <script>
            select q.*,
                exists (
                    select 1 from answers pa where pa.question_id = q.id
                      and pa.moderation_status in ('L1', 'L2', 'L3')
                ) as has_pending_answer
            from questions q
            where q.scope = 'public' and q.visibility = 'VISIBLE'
              and exists (
                    select 1 from answers a where a.question_id = q.id
                      and (a.visibility = 'VISIBLE' or a.moderation_status in ('L1', 'L2', 'L3'))
              )
            <if test="cursor != null">and q.id &lt; #{cursor}</if>
            order by q.id desc limit #{limit}
            </script>
            """)
    List<QuestionDO> listPublicWithAnyAnswer(@Param("cursor") Long cursor, @Param("limit") int limit);

    @Update("""
            update questions set moderation_status = #{moderationStatus}, visibility = #{visibility},
                risk_level = #{riskLevel}, moderation_reason = #{reason}, moderated_at = current_timestamp,
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateModeration(@Param("id") Long id,
                         @Param("moderationStatus") String moderationStatus,
                         @Param("visibility") String visibility,
                         @Param("riskLevel") String riskLevel,
                         @Param("reason") String reason);

    @Update("""
            <script>
            update questions set moderation_status = #{toStatus}, visibility = #{visibility},
                risk_level = #{riskLevel}, moderation_reason = #{reason}, moderated_at = current_timestamp,
                review_attempts = review_attempts + 1, updated_at = current_timestamp
            where id = #{id} and moderation_status in
            <foreach collection="expected" item="item" open="(" separator="," close=")">#{item}</foreach>
            </script>
            """)
    int updateModerationIfStatusIn(@Param("id") Long id,
                                   @Param("expected") List<String> expected,
                                   @Param("toStatus") String toStatus,
                                   @Param("visibility") String visibility,
                                   @Param("riskLevel") String riskLevel,
                                   @Param("reason") String reason);

    @Update("""
            <script>
            update questions set visibility = #{visibility}, moderation_reason = #{reason},
                moderated_at = current_timestamp, updated_at = current_timestamp
            where id = #{id} and moderation_status in
            <foreach collection="expectedStatus" item="item" open="(" separator="," close=")">#{item}</foreach>
              and visibility in
            <foreach collection="expectedVisibility" item="item" open="(" separator="," close=")">#{item}</foreach>
            </script>
            """)
    int updateVisibilityIf(@Param("id") Long id,
                           @Param("expectedStatus") List<String> expectedStatus,
                           @Param("expectedVisibility") List<String> expectedVisibility,
                           @Param("visibility") String visibility,
                           @Param("reason") String reason);

    @Update("""
            update questions set moderation_status = 'L3', visibility = #{visibility},
                moderation_reason = #{reason}, moderated_at = current_timestamp, updated_at = current_timestamp
            where id = #{id} and moderation_status in ('APPROVED', 'REJECTED')
            """)
    int undoModerationIf(@Param("id") Long id, @Param("visibility") String visibility,
                         @Param("reason") String reason);

    @Select("""
            select count(*) from questions
            where scope = 'public' and moderation_status = 'L3' and review_attempts >= 2
            """)
    long countArbitration();

    @Update("""
            update questions set moderation_status = 'WITHDRAWN', visibility = 'REMOVED',
                moderated_at = current_timestamp, updated_at = current_timestamp
            where id = #{id} and visibility in ('VISIBLE', 'HIDDEN')
            """)
    int withdraw(Long id);

    @Update("""
            update questions set answer_count = answer_count + 1,
                first_answer_at = coalesce(first_answer_at, current_timestamp), updated_at = current_timestamp
            where id = #{id}
            """)
    int recordAnswer(Long id);

    @Update("""
            update questions set followup_count = followup_count + 1, updated_at = current_timestamp where id = #{id}
            """)
    int incrementFollowup(Long id);

    @Update("""
            update questions set useful_count = useful_count + 1, updated_at = current_timestamp where id = #{id}
            """)
    int incrementUseful(Long id);

    @Update("""
            update questions set useful_count = case when useful_count > 0 then useful_count - 1 else 0 end,
                updated_at = current_timestamp
            where id = #{id}
            """)
    int decrementUseful(Long id);

    @Update("update questions set answer_count = greatest(answer_count - 1, 0), updated_at = current_timestamp where id = #{id}")
    int decrementAnswer(Long id);

    @Update("update questions set followup_count = greatest(followup_count - 1, 0), updated_at = current_timestamp where id = #{id}")
    int decrementFollowup(Long id);

    @Update("""
            update questions set like_count = like_count + 1, updated_at = current_timestamp where id = #{id}
            """)
    int incrementLike(Long id);

    @Update("""
            update questions set like_count = case when like_count > 0 then like_count - 1 else 0 end,
                updated_at = current_timestamp
            where id = #{id}
            """)
    int decrementLike(Long id);

    @Update("""
            update questions set favorite_count = favorite_count + 1, updated_at = current_timestamp where id = #{id}
            """)
    int incrementFavorite(Long id);

    @Update("""
            update questions set favorite_count = case when favorite_count > 0 then favorite_count - 1 else 0 end,
                updated_at = current_timestamp
            where id = #{id}
            """)
    int decrementFavorite(Long id);

}