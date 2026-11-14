package com.careerq.bootstrap;

import com.careerq.answer.Answer;
import com.careerq.answer.AnswerRepository;
import com.careerq.followup.Followup;
import com.careerq.followup.FollowupRepository;
import com.careerq.publicpost.PublicPost;
import com.careerq.publicpost.PublicPostRepository;
import com.careerq.question.Question;
import com.careerq.question.QuestionRepository;
import com.careerq.tag.Tag;
import com.careerq.tag.TagRepository;
import com.careerq.user.User;
import com.careerq.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 演示数据（仅 demo profile，且库为空时写入）。
 * 启动方式：SPRING_PROFILES_ACTIVE=dev,demo
 * 演示账号（dev 验证码固定 123456）：
 *   学生 13800000001 / HR 13800000002 / 运营 13800000003
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final TagRepository tagRepository;
    private final FollowupRepository followupRepository;
    private final PublicPostRepository publicPostRepository;

    public DemoDataSeeder(UserRepository userRepository,
                          QuestionRepository questionRepository,
                          AnswerRepository answerRepository,
                          TagRepository tagRepository,
                          FollowupRepository followupRepository,
                          PublicPostRepository publicPostRepository) {
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.tagRepository = tagRepository;
        this.followupRepository = followupRepository;
        this.publicPostRepository = publicPostRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("[demo] 数据库已有数据，跳过演示数据写入");
            return;
        }
        User student = createUser("13800000001", "student", "演示学生");
        User hr = createUser("13800000002", "hr", "某互联网公司 HR");
        createUser("13800000003", "operator", "运营小助手");

        Tag intern = tagRepository.save(Tag.of("实习", "实习"));
        Tag resume = tagRepository.save(Tag.of("简历", "简历"));
        Tag interview = tagRepository.save(Tag.of("面试准备", "面试准备"));

        Question q1 = new Question();
        q1.setScope("public");
        q1.setAuthorId(student.getId());
        q1.setTitle("实习面试一般会问什么？");
        q1.setBody("第一次找实习，想提前准备一下常见的面试问题。");
        q1.setStatus("published");
        q1.setTags(new LinkedHashSet<>(Set.of(intern, interview)));
        questionRepository.save(q1);

        Answer a1 = new Answer();
        a1.setQuestionId(q1.getId());
        a1.setAuthorId(hr.getId());
        a1.setBody("常见有自我介绍、项目经历、岗位理解，建议用 STAR 结构准备 2—3 个案例。");
        a1.setLengthType("detailed");
        a1.setStatus("published");
        a1.setUsefulCount(3);
        answerRepository.save(a1);

        q1.setAnswerCount(1);
        q1.setFirstAnswerAt(LocalDateTime.now());
        questionRepository.save(q1);

        Question q2 = new Question();
        q2.setScope("public");
        q2.setAuthorId(student.getId());
        q2.setTitle("简历里没有实习经历怎么写？");
        q2.setBody("大二还没有实习，简历应该怎么突出自己？");
        q2.setStatus("published");
        q2.setTags(new LinkedHashSet<>(Set.of(resume)));
        questionRepository.save(q2);

        Question q3 = new Question();
        q3.setScope("public");
        q3.setAuthorId(student.getId());
        q3.setTitle("如何判断一个行业适不适合自己？");
        q3.setBody("对行业选择比较迷茫，想知道可以从哪些维度评估。");
        q3.setStatus("published");
        q3.setTags(new LinkedHashSet<>(Set.of(interview)));
        questionRepository.save(q3);

        // 一条已审核公开的私密沉淀
        PublicPost post = new PublicPost();
        post.setOriginalQuestionId(q2.getId());
        post.setTitle("没有实习经历，简历怎么写才不空？");
        post.setQuestionBody("大二还没有实习，简历应该怎么突出自己？");
        post.setHrAnswer("突出课程项目、竞赛、社团与技能证书，用结果量化产出。");
        post.setAnonymized(true);
        post.setStatus("published");
        post.setPublishedAt(LocalDateTime.now());
        publicPostRepository.save(post);

        log.info("[demo] 演示数据已写入：学生 13800000001 / HR 13800000002 / 运营 13800000003（验证码 123456）");
    }

    private User createUser(String phone, String role, String nickname) {
        User user = new User();
        user.setRole(role);
        user.setPhoneHash(sha256(phone));
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setNickname(nickname);
        user.setStatus("active");
        return userRepository.save(user);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
