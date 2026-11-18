package com.careerq.bootstrap;

import com.careerq.dataobject.AnswerDO;
import com.careerq.dao.AnswerDao;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.TagDO;
import com.careerq.dao.TagDao;
import com.careerq.dataobject.UserDO;
import com.careerq.dao.UserDao;
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

    private final UserDao userDao;
    private final QuestionDao questionDao;
    private final AnswerDao answerDao;
    private final TagDao tagDao;

    public DemoDataSeeder(UserDao userDao,
                          QuestionDao questionDao,
                          AnswerDao answerDao,
                          TagDao tagDao) {
        this.userDao = userDao;
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.tagDao = tagDao;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userDao.count() > 0) {
            log.info("[demo] 数据库已有数据，跳过演示数据写入");
            return;
        }
        UserDO student = createUser("13800000001", "student", "演示学生");
        UserDO hr = createUser("13800000002", "hr", "某互联网公司 HR");
        createUser("13800000003", "operator", "运营小助手");

        TagDO intern = createTag("实习");
        TagDO resume = createTag("简历");
        TagDO interview = createTag("面试准备");

        QuestionDO q1 = new QuestionDO();
        q1.setScope("public");
        q1.setAuthorId(student.getId());
        q1.setTitle("实习面试一般会问什么？");
        q1.setBody("第一次找实习，想提前准备一下常见的面试问题。");
        q1.setStatus("published");
        questionDao.insert(q1);
        tagDao.linkToQuestion(q1.getId(), intern.getId());
        tagDao.linkToQuestion(q1.getId(), interview.getId());

        AnswerDO a1 = new AnswerDO();
        a1.setQuestionId(q1.getId());
        a1.setAuthorId(hr.getId());
        a1.setBody("常见有自我介绍、项目经历、岗位理解，建议用 STAR 结构准备 2—3 个案例。");
        a1.setLengthType("detailed");
        a1.setStatus("published");
        a1.setUsefulCount(3);
        answerDao.insert(a1);
        questionDao.recordAnswer(q1.getId());

        QuestionDO q2 = new QuestionDO();
        q2.setScope("public");
        q2.setAuthorId(student.getId());
        q2.setTitle("简历里没有实习经历怎么写？");
        q2.setBody("大二还没有实习，简历应该怎么突出自己？");
        q2.setStatus("published");
        questionDao.insert(q2);
        tagDao.linkToQuestion(q2.getId(), resume.getId());

        QuestionDO q3 = new QuestionDO();
        q3.setScope("public");
        q3.setAuthorId(student.getId());
        q3.setTitle("如何判断一个行业适不适合自己？");
        q3.setBody("对行业选择比较迷茫，想知道可以从哪些维度评估。");
        q3.setStatus("published");
        questionDao.insert(q3);
        tagDao.linkToQuestion(q3.getId(), interview.getId());

        log.info("[demo] 演示数据已写入：学生 13800000001 / HR 13800000002 / 运营 13800000003（验证码 123456）");
    }

    private TagDO createTag(String name) {
        TagDO tag = TagDO.of(name, name);
        tagDao.insert(tag);
        return tag;
    }

    private UserDO createUser(String phone, String role, String nickname) {
        UserDO user = new UserDO();
        user.setRole(role);
        user.setPhoneHash(sha256(phone));
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setNickname(nickname);
        user.setStatus("active");
        userDao.insert(user);
        return user;
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
