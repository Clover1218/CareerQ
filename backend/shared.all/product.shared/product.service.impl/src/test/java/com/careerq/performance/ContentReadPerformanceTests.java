package com.careerq.performance;

import com.careerq.dao.AnswerDao;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.PublicPostDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.TagDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.InteractionDO;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dataobject.TagDO;
import com.careerq.dataobject.UserDO;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import com.careerq.service.PublicPostService;
import com.careerq.service.QuestionService;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Counts database SELECTs, including cache effects, rather than relying on timing thresholds. */
@SpringBootTest
@Import(ContentReadPerformanceTests.QueryCountingConfiguration.class)
@Transactional
class ContentReadPerformanceTests {

    @Autowired QuestionService questions;
    @Autowired FollowupService followups;
    @Autowired PublicPostService posts;
    @Autowired UserDao userDao;
    @Autowired QuestionDao questionDao;
    @Autowired TagDao tagDao;
    @Autowired AnswerDao answerDao;
    @Autowired FollowupDao followupDao;
    @Autowired PublicPostDao postDao;
    @Autowired InteractionDao interactionDao;
    @Autowired SqlSessionTemplate sqlSession;
    @Autowired SelectCounter counter;

    @Test
    void publicListBatchesTagsAndPreservesCursorAndTagOrder() {
        UserDO author = user("student");
        TagDO first = tag();
        TagDO second = tag();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            QuestionDO question = question(author.getId());
            ids.add(question.getId());
            tagDao.linkToQuestion(question.getId(), second.getId());
            tagDao.linkToQuestion(question.getId(), first.getId());
        }

        var page = measured("public-list-50", () -> questions.list(first.getSlug(), null, 50));
        assertThat(page.items()).hasSize(50);
        assertThat(page.items().getFirst().id()).isEqualTo(ids.getLast());
        assertThat(page.items().getFirst().tags()).containsExactly(first.getName(), second.getName());
        assertThat(page.nextCursor()).isEqualTo(ids.get(1));
        assertThat(counter.count()).isLessThanOrEqualTo(2);
        assertThat(questions.list(first.getSlug(), page.nextCursor(), 50).items())
                .extracting(item -> item.id()).containsExactly(ids.getFirst());
    }

    @Test
    void myQuestionsBatchAuthorsAndTagsWithoutLosingPendingItems() {
        UserDO author = user("student");
        for (int i = 0; i < 50; i++) {
            question(author.getId());
        }
        QuestionDO pending = question(author.getId());
        questionDao.updateStatus(pending.getId(), "pending_human");

        var page = measured("my-questions-50", () -> questions.mine(auth(author), null, null, 50));
        assertThat(page.items()).hasSize(50);
        assertThat(page.items().getFirst().status()).isEqualTo("pending_human");
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.author().nickname()).isEqualTo(author.getNickname());
            assertThat(item.tags()).isEmpty();
        });
        assertThat(page.nextCursor()).isNotNull();
        assertThat(counter.count()).isLessThanOrEqualTo(3);
    }

    @Test
    void detailAndThreadBatchAuthorsAndKeepViewerAndModerationStatesIsolated() {
        UserDO viewer = user("student");
        UserDO otherViewer = user("student");
        QuestionDO question = question(viewer.getId());
        List<Long> visibleIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UserDO hr = user("hr");
            AnswerDO answer = answer(question.getId(), hr.getId(), "published");
            visibleIds.add(answer.getId());
            if (i % 2 == 0) {
                interaction(viewer.getId(), "answer", answer.getId(), "like");
            } else {
                interaction(viewer.getId(), "answer", answer.getId(), "useful");
            }
            followup(question.getId(), hr.getId(), "published");
        }
        answer(question.getId(), otherViewer.getId(), "pending_human");
        followup(question.getId(), otherViewer.getId(), "pending_human");
        interaction(viewer.getId(), "question", question.getId(), "favorite");

        var detail = measured("detail-50-answers-50-followups",
                () -> questions.detail(auth(viewer), question.getId()));
        assertThat(detail.answers()).hasSize(50);
        assertThat(detail.followups()).hasSize(50);
        assertThat(detail.viewer().favorited()).isTrue();
        assertThat(detail.answers()).allSatisfy(item -> {
            int index = visibleIds.indexOf(item.id());
            assertThat(item.liked()).isEqualTo(index % 2 == 0);
            assertThat(item.usefuled()).isEqualTo(index % 2 != 0);
            assertThat(item.author().nickname()).startsWith("读性能-");
            assertThat(item.status()).isEqualTo("published");
        });
        assertThat(counter.count()).isLessThanOrEqualTo(10);

        var thread = measured("thread-50-answers-50-followups",
                () -> questions.thread(auth(viewer), question.getId()));
        assertThat(thread.items()).hasSize(100);
        assertThat(thread.items().stream().filter(item -> item.liked()).count()).isEqualTo(25);
        assertThat(counter.count()).isLessThanOrEqualTo(8);

        var otherDetail = questions.detail(auth(otherViewer), question.getId());
        assertThat(otherDetail.viewer().favorited()).isFalse();
        assertThat(otherDetail.answers()).noneMatch(item -> item.liked() || item.usefuled());
    }

    @Test
    void followupsBatchDistinctAuthorsAndRetainTheirIdentity() {
        UserDO viewer = user("student");
        QuestionDO question = question(viewer.getId());
        List<Long> senderIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UserDO sender = user("student");
            senderIds.add(sender.getId());
            followup(question.getId(), sender.getId(), "published");
        }

        var items = measured("followups-50", () -> followups.list(null, question.getId()));
        assertThat(items).hasSize(50);
        assertThat(items).extracting(item -> item.author().id()).containsExactlyElementsOf(senderIds);
        assertThat(counter.count()).isLessThanOrEqualTo(3);
    }

    @Test
    void publicPostsBatchInteractionsWithoutCrossViewerOrTargetLeaks() {
        UserDO viewer = user("student");
        UserDO other = user("student");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            PublicPostDO post = post("published");
            ids.add(post.getId());
            if (i % 2 == 0) {
                interaction(viewer.getId(), "post", post.getId(), "like");
            } else {
                interaction(viewer.getId(), "post", post.getId(), "favorite");
            }
            interaction(other.getId(), "post", post.getId(), "like");
            interaction(viewer.getId(), "answer", post.getId(), "like");
        }
        PublicPostDO pending = post("pending_human");

        var items = measured("public-posts-50", () -> posts.list(auth(viewer)));
        assertThat(items).extracting(item -> item.id()).doesNotContain(pending.getId());
        assertThat(items.stream().filter(item -> ids.contains(item.id())).toList()).hasSize(50)
                .allSatisfy(item -> {
                    int index = ids.indexOf(item.id());
                    assertThat(item.liked()).isEqualTo(index % 2 == 0);
                    assertThat(item.favorited()).isEqualTo(index % 2 != 0);
                });
        assertThat(counter.count()).isLessThanOrEqualTo(2);
        measured("guest-public-posts", () -> posts.list(null));
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void emptyPagesDoNotIssueInvalidInClauses() {
        UserDO viewer = user("student");
        assertThat(measured("empty-mine", () -> questions.mine(auth(viewer), null, null, 50)).items()).isEmpty();
        assertThat(counter.count()).isEqualTo(1);
        assertThat(measured("empty-list", () -> questions.list(UUID.randomUUID().toString(), null, 50)).items()).isEmpty();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void largeListsKeepAuthorsAndInteractionsAcrossBatchBoundaries() {
        UserDO viewer = user("student");
        QuestionDO question = question(viewer.getId());
        List<Long> senderIds = new ArrayList<>();
        List<Long> postIds = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            UserDO sender = user("student");
            senderIds.add(sender.getId());
            followup(question.getId(), sender.getId(), "published");
            PublicPostDO post = post("published");
            postIds.add(post.getId());
            interaction(viewer.getId(), "post", post.getId(), "favorite");
        }

        var replies = measured("followups-501", () -> followups.list(null, question.getId()));
        assertThat(replies).extracting(item -> item.author().id()).containsExactlyElementsOf(senderIds);
        assertThat(counter.count()).isEqualTo(4);

        var published = measured("public-posts-501", () -> posts.list(auth(viewer)));
        assertThat(published.stream().filter(item -> postIds.contains(item.id())).toList()).hasSize(501)
                .allSatisfy(item -> assertThat(item.favorited()).isTrue());
        assertThat(counter.count()).isEqualTo(3);
    }

    @Test
    void missingAuthorsKeepTheExistingDisplayFallback() {
        QuestionDO question = question(-1000L);
        followup(question.getId(), -1001L, "published");

        var detail = questions.detail(null, question.getId());
        assertThat(detail.question().author().id()).isEqualTo(-1000L);
        assertThat(detail.question().author().nickname()).isEqualTo("用户");
        assertThat(detail.followups().getFirst().author().id()).isEqualTo(-1001L);
        assertThat(detail.followups().getFirst().author().nickname()).isEqualTo("用户");
    }

    private <T> T measured(String label, Supplier<T> operation) {
        sqlSession.clearCache();
        counter.start();
        try {
            return operation.get();
        } finally {
            counter.stop();
            System.out.println("SELECT count [" + label + "]: " + counter.count());
        }
    }

    private UserDO user(String role) {
        UserDO user = new UserDO();
        user.setRole(role);
        user.setNickname("读性能-" + UUID.randomUUID());
        user.setStatus("active");
        userDao.insert(user);
        return user;
    }

    private AuthUser auth(UserDO user) {
        return AuthUser.of(user.getId(), user.getRole(), user.getNickname(), "test-only");
    }

    private TagDO tag() {
        String value = "perf-" + UUID.randomUUID().toString().substring(0, 20);
        TagDO tag = TagDO.of(value, value);
        tagDao.insert(tag);
        return tag;
    }

    private QuestionDO question(Long authorId) {
        QuestionDO question = new QuestionDO();
        question.setScope("public");
        question.setStatus("published");
        question.setAuthorId(authorId);
        question.setTitle("性能测试问题");
        question.setBody("完整正文");
        questionDao.insert(question);
        return question;
    }

    private AnswerDO answer(Long questionId, Long authorId, String status) {
        AnswerDO answer = new AnswerDO();
        answer.setQuestionId(questionId);
        answer.setAuthorId(authorId);
        answer.setBody("回答正文");
        answer.setStatus(status);
        answer.setLengthType("short");
        answerDao.insert(answer);
        return answer;
    }

    private void followup(Long questionId, Long senderId, String status) {
        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(questionId);
        followup.setSenderId(senderId);
        followup.setSenderRole("student");
        followup.setBody("追问正文");
        followup.setScope("public");
        followup.setStatus(status);
        followupDao.insert(followup);
    }

    private PublicPostDO post(String status) {
        PublicPostDO post = new PublicPostDO();
        post.setTitle("公开稿");
        post.setQuestionBody("问题正文");
        post.setHrAnswer("回答正文");
        post.setAnonymized(true);
        post.setStatus(status);
        post.setPublishedAt(LocalDateTime.now());
        postDao.insert(post);
        return post;
    }

    private void interaction(Long viewerId, String targetType, Long targetId, String type) {
        InteractionDO interaction = new InteractionDO();
        interaction.setUserId(viewerId);
        interaction.setTargetType(targetType);
        interaction.setTargetId(targetId);
        interaction.setType(type);
        interactionDao.insert(interaction);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryCountingConfiguration {
        @Bean
        SelectCounter selectCounter() {
            return new SelectCounter();
        }
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "query",
            args = {Statement.class, ResultHandler.class}))
    static class SelectCounter implements Interceptor {
        private final ThreadLocal<int[]> current = new ThreadLocal<>();
        private int lastCount;

        void start() {
            current.set(new int[1]);
        }

        void stop() {
            lastCount = current.get()[0];
            current.remove();
        }

        int count() {
            return lastCount;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            int[] count = current.get();
            if (count != null) {
                count[0]++;
            }
            return invocation.proceed();
        }
    }
}
