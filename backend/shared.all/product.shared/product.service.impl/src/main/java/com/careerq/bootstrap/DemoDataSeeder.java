package com.careerq.bootstrap;

import com.careerq.dataobject.AnswerDO;
import com.careerq.dao.AnswerDao;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dao.FollowupDao;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.dao.PublicPostDao;
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
    private final FollowupDao followupDao;
    private final TagDao tagDao;
    private final PublicPostDao publicPostDao;

    public DemoDataSeeder(UserDao userDao,
                          QuestionDao questionDao,
                          AnswerDao answerDao,
                          FollowupDao followupDao,
                          TagDao tagDao,
                          PublicPostDao publicPostDao) {
        this.userDao = userDao;
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.followupDao = followupDao;
        this.tagDao = tagDao;
        this.publicPostDao = publicPostDao;
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

        TagDO intern = requireTag("实习");
        TagDO resume = requireTag("简历");
        TagDO interview = requireTag("面试准备");
        TagDO autumn = requireTag("秋招");
        TagDO career = requireTag("职业规划");

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

        // 长内容演示：秋招复盘
        QuestionDO q4 = new QuestionDO();
        q4.setScope("public");
        q4.setAuthorId(student.getId());
        q4.setTitle("秋招三轮技术面全挂，复盘之后应该优先补哪一块？");
        q4.setBody("""
                背景：双非本科，Java 后端方向，投了 40 多家，拿到 6 次面试机会，全部挂在一面或二面。

                三次失败大概是这样的：
                1. 第一家：八股问得很深，JVM 内存模型和 GC 调优没答上来；
                2. 第二家：项目深挖，面试官一直问我为什么用 Redis、不用行不行，我答得很虚；
                3. 第三家：手撕算法卡在二叉树层序遍历的边界处理上，时间没写完。

                现在的问题是：我背了很多八股，但一到追问就露馅；项目也做了两个，可讲出来像在念功能清单。
                距离春招还有三个月，应该优先补哪一块？是继续刷题，还是把项目重新做一遍？
                希望有经验的前辈能给一个可执行的计划，最好能说清楚每周做什么。
                """);
        q4.setStatus("published");
        questionDao.insert(q4);
        tagDao.linkToQuestion(q4.getId(), autumn.getId());
        tagDao.linkToQuestion(q4.getId(), interview.getId());

        AnswerDO a4 = new AnswerDO();
        a4.setQuestionId(q4.getId());
        a4.setAuthorId(hr.getId());
        a4.setBody("""
                先说结论：你现在的瓶颈不是"知识点不够"，而是"讲不出判断过程"。
                三轮挂掉的原因各不相同，但本质是同一个问题——面试官问的是"你为什么这么想"，你回答的是"我背过这个"。

                一、关于八股
                不要按面经顺序背，按"问题链"整理。比如 JVM，不要只记 GC 分代，试着回答这条链：
                对象为什么进老年代 → 什么情况下会触发 Full GC → Full GC 频繁时你怎么排查 → 你实际遇到过一次吗，当时怎么定位的。
                每条链最后都要落到一个你亲手处理过的场景，哪怕只是课程项目里调过 JVM 参数。
                面试官追问，往往就是为了验证最后这一步。

                二、关于项目
                把"功能清单"改成"决策清单"。每个项目提前准备三个问题：
                1. 当时有哪些可选方案？你为什么选了这个？
                2. 这个方案带来了什么代价？你后来怎么弥补？
                3. 如果流量或数据量涨十倍，哪里会先出问题？
                以 Redis 为例，不要说"用了 Redis 做缓存"，要说"查询接口 QPS 到了多少、数据库顶不住，
                我先加了本地缓存，但多实例下不一致，所以换成 Redis，并接受了缓存穿透的风险，
                用布隆过滤器和空值缓存兜底"。这段话本身就包含了取舍、验证和兜底，追问空间就变成了你的主场。

                三、关于算法
                三个月的时间，建议按 6:3:1 分配。六成时间做项目复盘和八股成链，三成时间刷题，
                每天 3 道，重点在树的遍历、双指针和动态规划基础，写完必须手写一遍边界；
                一成时间做模拟面试。模拟面试一定要出声讲，讲给同学听，让对方专挑你答得最顺的地方追问。

                四、时间安排
                第 1—4 周：把两个项目按上面的"决策清单"重写一遍，每个项目写满两页纸，能对着空气讲 15 分钟不卡壳。
                第 5—8 周：八股成链，每天一条链，周末自测。
                第 9—12 周：投递加模拟面试，重点练"不会的问题怎么接"。
                遇到不会的题，坦白说不会，然后讲你的推理路径，比硬编一个答案的评分高得多。

                最后提醒一句：春招岗位少，但竞争者也少，很多秋招拿到 offer 的人不会再来。
                你的三次失败不是白挂的，把每次面试的问题回忆出来，它们就是你最真实的题库。
                """);
        a4.setLengthType("detailed");
        a4.setStatus("published");
        a4.setUsefulCount(12);
        answerDao.insert(a4);
        questionDao.recordAnswer(q4.getId());

        FollowupDO f4a = new FollowupDO();
        f4a.setQuestionId(q4.getId());
        f4a.setSenderId(student.getId());
        f4a.setSenderRole("student");
        f4a.setBody("""
                追问一下：我的两个项目都是课程作业量级，确实没有高并发场景，
                按"决策清单"讲取舍会不会显得很假？如果面试官追问数据，我该如实说没有压测过吗？
                """);
        f4a.setScope("public");
        f4a.setStatus("published");
        followupDao.insert(f4a);
        questionDao.incrementFollowup(q4.getId());

        FollowupDO f4b = new FollowupDO();
        f4b.setQuestionId(q4.getId());
        f4b.setSenderId(student.getId());
        f4b.setSenderRole("student");
        f4b.setBody("再补充一个：面试官问“你最大的缺点是什么”，我回答“太追求完美”，他说这是套路答案。这种题到底想听什么？");
        f4b.setScope("public");
        f4b.setStatus("published");
        followupDao.insert(f4b);
        questionDao.incrementFollowup(q4.getId());

        // 长内容演示：简历项目经历
        QuestionDO q5 = new QuestionDO();
        q5.setScope("public");
        q5.setAuthorId(student.getId());
        q5.setTitle("简历上的项目只有课程作业，怎么写出让人愿意面的内容？");
        q5.setBody("""
                大三在读，准备找暑期实习。简历改了很多版，但投出去几乎没回音。
                我手上能写的只有三个课程项目：一个图书管理系统、一个仿微博的 Java Web 小站、
                还有一个用 Spring Boot 做的校园二手交易平台，量级都很小，也没有真实用户。

                自己写的时候很纠结：写得太细像流水账，写得太概括又像套模板。
                也看过一些建议说要量化结果，可这几个项目根本没有可量化的业务指标。
                想请教一下，这种没有实习、没有真实数据的项目，简历应该怎么组织？
                技术栈要写多深？要不要附上 GitHub 链接？如果面试官问"这个项目上线了吗"，应该怎么回答才不减分？
                """);
        q5.setStatus("published");
        questionDao.insert(q5);
        tagDao.linkToQuestion(q5.getId(), resume.getId());

        AnswerDO a5 = new AnswerDO();
        a5.setQuestionId(q5.getId());
        a5.setAuthorId(hr.getId());
        a5.setBody("""
                课程项目不是问题，几乎所有应届生的第一份简历都是这么过来的。问题在于你写的是"做完了什么"，
                而筛选简历的人想看的是"你解决了什么"。

                一、每条项目描述用同一个结构
                背景一句话，问题一句话，做法两句话，结果一句话。举个可以直接套的例子：
                "校园二手交易平台（Spring Boot + MySQL + Redis）：课程项目，模拟 500 名用户并发浏览商品列表时，
                数据库连接经常被打满。引入 Redis 缓存热点列表并设置随机过期时间，接口平均响应从 800ms 降到 120ms。"
                这里的结果不是业务指标，而是你自己压测出来的技术指标，这完全可以写，只要如实标注测试方式。

                二、技术栈写到"能扛追问"的深度
                不要罗列一堆名词。写了 Redis，就要能说清楚缓存了什么、怎么处理穿透和一致性；
                写了消息队列，就要能说清楚为什么需要异步、失败怎么重试。宁可少写两个，也别写自己讲不明白的。

                三、不要虚构用户和上线状态
                面试官问"上线了吗"，如实说"没有对外上线，是课程项目，但我做过本地压测，也部署在服务器上跑过一段时间"，
                这句话不减分，反而说明你诚实且有工程意识。减分的是含糊其辞，等对方继续追问才承认。

                四、GitHub 链接
                建议放，但前提是 README 能让人五分钟看懂：项目解决什么问题、怎么启动、目录结构、
                遇到过的一个坑和解决思路。如果仓库里只有课程原始代码，先把 README 补好再放链接。

                五、排版和投递
                一页纸，项目放最上面，倒序排列，最近做的放第一个。每个项目三到五条，不要超过五条。
                投递前把岗位描述里的关键词，和你项目里真实做过的部分对上，比如对方写"熟悉缓存设计"，
                你的 Redis 那条就往缓存设计上靠。不要为了匹配去编没有的经历，面试三句话就能问出来。

                最后说个心态问题：没有回音不一定是简历差，也可能是投递时间、岗位匹配度的问题。
                把简历发给你信得过的学长学姐看一遍，比你自己改十版都有用。
                """);
        a5.setLengthType("detailed");
        a5.setStatus("published");
        a5.setUsefulCount(9);
        answerDao.insert(a5);
        questionDao.recordAnswer(q5.getId());

        FollowupDO f5a = new FollowupDO();
        f5a.setQuestionId(q5.getId());
        f5a.setSenderId(student.getId());
        f5a.setSenderRole("student");
        f5a.setBody("""
                感谢，结构很清晰。再问一个细节：课程项目是小组作业，有三个人一起做的，
                简历里要不要写清楚自己负责的部分？如果只写我负责的模块，会不会显得项目很单薄？
                """);
        f5a.setScope("public");
        f5a.setStatus("published");
        followupDao.insert(f5a);
        questionDao.incrementFollowup(q5.getId());

        FollowupDO f5b = new FollowupDO();
        f5b.setQuestionId(q5.getId());
        f5b.setSenderId(student.getId());
        f5b.setSenderRole("student");
        f5b.setBody("再补充一个：简历上要不要写期望薪资？我问了同学，有人说写了会被压价，有人说写了显得有底气。");
        f5b.setScope("public");
        f5b.setStatus("published");
        followupDao.insert(f5b);
        questionDao.incrementFollowup(q5.getId());

        // 长内容演示：行业与平台选择
        QuestionDO q6 = new QuestionDO();
        q6.setScope("public");
        q6.setAuthorId(student.getId());
        q6.setTitle("国企稳定和大厂平台，第一份实习到底该怎么选？");
        q6.setBody("""
                目前手上有两个暑期实习 offer，都是后端开发方向，时间冲突，只能选一个，比较纠结。

                A：老家省会的一家国企子公司，做内部管理系统，转正概率高，工作节奏比较慢，
                   据说每天六点能走，但技术栈偏老，用的是较旧的框架，业务也以流程审批为主。
                B：一线城市的大厂基础架构部门，做中间件相关的事情，面试时感觉团队技术氛围很好，
                   但实习转正率不确定，生活成本也高，而且离家远。

                我自己的情况是：普通家庭，没有太多试错成本，长期希望留在二线城市生活；
                但又不甘心第一份工作就去一个技术成长有限的地方。
                想听听从职业发展角度看，应该更看重平台还是稳定性？如果选了大厂，两年后回二线会不会反而难找工作？
                """);
        q6.setStatus("published");
        questionDao.insert(q6);
        tagDao.linkToQuestion(q6.getId(), career.getId());

        AnswerDO a6 = new AnswerDO();
        a6.setQuestionId(q6.getId());
        a6.setAuthorId(hr.getId());
        a6.setBody("""
                这个问题没有标准答案，但可以把决策拆成三个问题，逐个回答之后，答案通常就出来了。

                第一，这份实习对你最大的价值是什么？
                如果你还没有一段像样的实习经历，那 B 的边际价值明显更高。第一段实习解决的是"从 0 到 1"，
                它决定了你后面能拿到什么样的面试机会。反过来，如果你已经有大厂实习，A 的稳定性才有比较意义。

                第二，你所谓的技术成长，具体指什么？
                国企系统也有值得学的东西：复杂业务建模、存量系统改造、跨部门沟通，这些在大厂中间件团队未必能接触到。
                差别在于反馈速度。大厂的基础架构岗，你能看到高并发下的真实问题，成长曲线更陡；
                国企的节奏慢，成长更依赖主动性。如果你是一个需要环境推着走的人，B 更适合你；
                如果你能自己找事做，A 也未尝不可。

                第三，两年后回二线，真的会贬值吗？
                不会，但前提是你这两年在 B 积累的东西是可迁移的。可迁移的是：排查复杂问题的能力、
                对系统设计的理解、以及一套靠谱的工作方法。不可迁移的是：只会在内部平台上点按钮。
                所以选 B 之后，要有意识地做两件事：一是争取完整负责一个模块，从设计到上线；
                二是保持输出，把做过的事情整理成可以讲清楚的案例。

                再说一个现实因素：普通家庭、没有试错成本，这句话很重要。
                如果 B 的城市生活成本会让你焦虑到影响状态，那它就不一定是好选择。
                实习期只有两三个月，可以算一笔账：房租加生活开销，家里能支持多少，自己能不能接受。
                如果这笔账算下来压力很大，选 A 并不丢人，稳扎稳打也是一种策略。

                最后给个可执行的建议：分别找两个团队的在职员工聊 20 分钟，
                问三个问题——实习生具体做什么、转正看什么、团队最近半年在忙什么。
                聊完你大概率就不纠结了，因为你会发现真正让你不舒服的，可能不是"稳定还是平台"，
                而是某个具体的细节。
                """);
        a6.setLengthType("detailed");
        a6.setStatus("published");
        a6.setUsefulCount(15);
        answerDao.insert(a6);
        questionDao.recordAnswer(q6.getId());

        FollowupDO f6a = new FollowupDO();
        f6a.setQuestionId(q6.getId());
        f6a.setSenderId(student.getId());
        f6a.setSenderRole("student");
        f6a.setBody("""
                谢谢，最后那段"找在职员工聊 20 分钟"很实用。但我不认识这两个团队的人，
                在社交平台上直接私信会不会很冒犯？有没有更自然的方式？比如问 HR 能不能安排和未来 mentor 聊一次？
                """);
        f6a.setScope("public");
        f6a.setStatus("published");
        followupDao.insert(f6a);
        questionDao.incrementFollowup(q6.getId());

        // 一条已审核公开的私密沉淀
        PublicPostDO post = new PublicPostDO();
        post.setOriginalQuestionId(q2.getId());
        post.setTitle("没有实习经历，简历怎么写才不空？");
        post.setQuestionBody("大二还没有实习，简历应该怎么突出自己？");
        post.setHrAnswer("突出课程项目、竞赛、社团与技能证书，用结果量化产出。");
        post.setAnonymized(true);
        post.setStatus("published");
        post.setPublishedAt(LocalDateTime.now());
        publicPostDao.insert(post);

        log.info("[demo] 演示数据已写入：学生 13800000001 / HR 13800000002 / 运营 13800000003（验证码 123456）");
    }

    private TagDO requireTag(String slug) {
        return tagDao.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("固定标签缺失：" + slug + "，请检查 V5 迁移"));
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
