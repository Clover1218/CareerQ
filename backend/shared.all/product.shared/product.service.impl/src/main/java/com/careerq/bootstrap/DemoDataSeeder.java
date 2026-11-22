package com.careerq.bootstrap;

import com.careerq.dataobject.AnswerDO;
import com.careerq.dao.AnswerDao;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.HrProfileDao;
import com.careerq.dataobject.HrProfileDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.TagDO;
import com.careerq.dao.TagDao;
import com.careerq.dataobject.UserDO;
import com.careerq.dao.UserDao;
import com.careerq.dao.UserPermissionDao;
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
 *   学生 13800000001
 *   HR   13800000002 陈静怡（字节跳动）
 *   HR   13800000004 王浩然（美团）
 *   HR   13800000005 李梦琪（阿里云）
 *   HR   13800000006 张帆（小红书）
 *   运营 13800000003（普通审核，无仲裁权）
 *   运营 13800000008（普通审核，无仲裁权）
 *   运营 13800000007（仲裁运营，拥有 arbitrate 权限）
 *
 * 数据覆盖：同一问题多个 HR 回答；每个 HR 会话都至少有一轮「题主追问 + HR 回复」。
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
    private final HrProfileDao hrProfileDao;
    private final UserPermissionDao userPermissionDao;

    public DemoDataSeeder(UserDao userDao,
                          QuestionDao questionDao,
                          AnswerDao answerDao,
                          FollowupDao followupDao,
                          TagDao tagDao,
                          HrProfileDao hrProfileDao,
                          UserPermissionDao userPermissionDao) {
        this.userDao = userDao;
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.followupDao = followupDao;
        this.tagDao = tagDao;
        this.hrProfileDao = hrProfileDao;
        this.userPermissionDao = userPermissionDao;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userDao.count() > 0) {
            log.info("[demo] 数据库已有数据，跳过演示数据写入");
            return;
        }
        UserDO student = createUser("13800000001", "student", "演示学生");
        UserDO chen = createHr("13800000002", "陈静怡", "字节跳动", "电商招聘 HRBP", "互联网");
        UserDO wang = createHr("13800000004", "王浩然", "美团", "到店事业群校招负责人", "本地生活");
        UserDO li = createHr("13800000005", "李梦琪", "阿里云", "技术招聘专家", "云计算");
        UserDO zhang = createHr("13800000006", "张帆", "小红书", "社区技术 HRBP", "内容社区");
        createUser("13800000003", "operator", "运营小助手");
        createUser("13800000008", "operator", "审核运营");
        UserDO arbiter = createUser("13800000007", "operator", "仲裁运营");
        userPermissionDao.insert(arbiter.getId(), "arbitrate");

        TagDO intern = requireTag("实习");
        TagDO resume = requireTag("简历");
        TagDO interview = requireTag("面试准备");
        TagDO autumn = requireTag("秋招");
        TagDO career = requireTag("职业规划");

        // ---------- Q1：多 HR 同题 ----------
        QuestionDO q1 = newQuestion(student,
                "实习面试一般会问什么？",
                "第一次找实习，想提前准备一下常见的面试问题。",
                intern, interview);

        AnswerDO q1a1 = rootAnswer(q1, chen, """
                实习面试通常会问四类问题：一是自我介绍，控制在 1 分钟，说清楚学校、方向、做过什么；
                二是项目经历，会追问你负责哪部分、遇到什么困难、怎么解决；
                三是岗位理解，比如你知道这个岗位每天做什么吗；四是反问环节。

                准备方法上，用 STAR 结构整理 2—3 个项目案例，每个案例写清楚背景、任务、动作、结果。
                面试官不一定问原题，但会反复验证你是不是真的做过。
                """, "detailed", 6);
        FollowupDO q1f1 = askFollowup(q1, chen, student,
                "我的项目都是课程作业，没有线上数据。面试官问“讲一个你最有成就感的项目”，我该选功能最多的，还是技术上最难的？");
        reply(q1, chen, q1f1, """
                选“你参与最深、能讲清楚取舍”的那个，而不是功能最多或技术最难的。
                判断标准很简单：能否回答三层追问——为什么这么做、当时有什么替代方案、如果重来一次你会改哪里。
                课程项目完全够用，关键是你要能还原当时的判断过程。功能多但每块都浅的项目，反而容易被问穿。
                """, 3);

        AnswerDO q1a2 = rootAnswer(q1, wang, """
                美团这边实习面试一般分两轮：一面重基础和项目，二面会给一个业务场景让你现场拆解。
                基础部分高频的是数据结构、数据库索引、HTTP 与缓存；项目部分更看重你是否真的做过，而不是用了多少技术。

                建议按岗位 JD 准备 3 个案例：一个技术难点、一个协作问题、一个失败复盘。
                每个控制在 2 分钟以内，先给结论再展开。
                """, "detailed", 4);
        FollowupDO q1f2 = askFollowup(q1, wang, student,
                "面试最后问“你还有什么想问我的”，问什么比较加分？问薪资和转正会不会不太合适？");
        reply(q1, wang, q1f2, """
                可以在二面或 HR 面再问薪资与转正，技术面优先问业务和团队。
                加分的问题通常具体，比如“这个岗位的实习生第一个月主要交付什么”“团队现在最头疼的技术问题是什么”
                “您觉得做得好的实习生通常有什么共同点”。这类问题说明你在认真评估岗位，而不是只想要一个 offer。
                """, 2);

        AnswerDO q1a3 = rootAnswer(q1, li, """
                阿里云技术岗实习面试，除了基础和项目，还会看学习能力和沟通。
                遇到不会的问题，面试官往往不是想听标准答案，而是看你如何拆解。
                可以先把问题复述一遍，说出你知道的部分，再讲你会怎么查、怎么验证。

                另外，简历上写的每个技术点都要能被追问三层，比如写了 Redis，
                就要能说清缓存了什么、怎么保证一致性、失效了会怎样。
                """, "detailed", 5);
        FollowupDO q1f3 = askFollowup(q1, li, student,
                "如果八股真的不会，直接说“不会”是不是就凉了？");
        reply(q1, li, q1f3, """
                不会本身不减分，硬编才会。比较好的接法是：先承认这块了解不深，再说你的推理路径，
                最后补一句你回去会怎么补齐。面试官每天面很多人，能区分“没背到”和“没思考”。
                当然，基础中的基础还是要提前准备，比如你简历上明确写了的语言和框架。
                """, 1);

        // ---------- Q2：单一 HR 回答 ----------
        QuestionDO q2 = newQuestion(student,
                "简历里没有实习经历怎么写？",
                "大二还没有实习，简历应该怎么突出自己？",
                resume);

        AnswerDO q2a1 = rootAnswer(q2, wang, """
                没有实习经历，简历要解决的是“可信度”问题。课程项目、竞赛、社团、开源都算经历，
                关键是写法：背景一句话，问题一句话，你做了什么两句话，结果一句话。

                比如“校园二手交易平台”：课程项目，模拟 500 用户并发浏览时数据库连接被打满；
                引入 Redis 缓存热点列表并加随机过期；接口平均响应从 800ms 降到 120ms。
                没有业务指标没关系，技术指标同样能证明你做过。
                """, "detailed", 7);
        FollowupDO q2f1 = askFollowup(q2, wang, student,
                "项目确实没做过压测，也没有量化结果，这种情况还要硬写数字吗？");
        reply(q2, wang, q2f1, """
                不要编数字，但可以补做一次简单压测。用 JMeter 或 wrk 跑一遍核心接口，记录 QPS 和响应时间，
                这本身就是加分项，也让你在面试里有话可讲。如果时间实在不够，就写过程性的事实，
                比如“实现了分页与索引优化，将列表查询从全表扫描改为覆盖索引”，不要写没有依据的百分比。
                """, 2);

        // ---------- Q3：两个 HR 不同角度 ----------
        QuestionDO q3 = newQuestion(student,
                "如何判断一个行业适不适合自己？",
                "对行业选择比较迷茫，想知道可以从哪些维度评估。",
                career);

        AnswerDO q3a1 = rootAnswer(q3, zhang, """
                判断一个行业适不适合，建议从三个维度看：一是需求是否长期存在，
                二是你能否接受它的日常状态，三是你的能力能否积累。
                很多人口中的“适不适合”，其实是对日常工作的想象和现实不匹配。

                低成本的方法是做三个小实验：找 3 位从业者各聊 20 分钟；
                用一周时间读这个行业的 5 份年报或深度报道；投 5 个相关岗位，看面试官关心什么。
                实验做完，你的感受会比空想可靠得多。
                """, "detailed", 8);
        FollowupDO q3f1 = askFollowup(q3, zhang, student,
                "怎么找到愿意聊的从业者？直接私信会不会很冒犯？");
        reply(q3, zhang, q3f1, """
                优先找校友和二级人脉，说明来意和时间成本，比如“想请教 15 分钟，主要问三个问题”，回复率会高很多。
                社交平台私信也可以，但要具体：不要问“这个行业怎么样”，
                而是问“你一天的工作大概怎么安排”“新人最容易踩的坑是什么”。对方回答完记得反馈，后续关系才留得住。
                """, 2);

        AnswerDO q3a2 = rootAnswer(q3, li, """
                除了兴趣，我更建议看“能力可迁移性”和“生活成本”。
                第一份工作所在的行业不一定做一辈子，但你积累的方法、领域知识和人脉会影响下一次选择。

                可以列一张表：左边写这个行业 3 年后可能需要的核心能力，右边写你现在具备什么、还缺什么。
                如果缺口能在 1—2 年内补上，就值得试；如果缺口依赖平台或资源，而你短期拿不到，就要谨慎。
                """, "detailed", 3);
        FollowupDO q3f2 = askFollowup(q3, li, student,
                "技术岗跨行业，之前积累的经验会浪费吗？");
        reply(q3, li, q3f2, """
                底层能力不会浪费，比如系统设计、排查问题、协作方式；浪费的是只绑定某个业务名词的经验。
                跨行业时，把简历里的业务描述翻译成通用能力，比如“熟悉电商交易链路”
                可以落到“高并发下的状态一致性与幂等设计”。面试时主动做这个翻译，对方才看得到可迁移的部分。
                """, 1);

        // ---------- Q4：长内容，一个 HR 两轮追问 ----------
        QuestionDO q4 = newQuestion(student,
                "秋招三轮技术面全挂，复盘之后应该优先补哪一块？",
                """
                        背景：双非本科，Java 后端方向，投了 40 多家，拿到 6 次面试机会，全部挂在一面或二面。

                        三次失败大概是这样的：
                        1. 第一家：八股问得很深，JVM 内存模型和 GC 调优没答上来；
                        2. 第二家：项目深挖，面试官一直问我为什么用 Redis、不用行不行，我答得很虚；
                        3. 第三家：手撕算法卡在二叉树层序遍历的边界处理上，时间没写完。

                        现在的问题是：我背了很多八股，但一到追问就露馅；项目也做了两个，可讲出来像在念功能清单。
                        距离春招还有三个月，应该优先补哪一块？是继续刷题，还是把项目重新做一遍？
                        希望有经验的前辈能给一个可执行的计划，最好能说清楚每周做什么。
                        """,
                autumn, interview);

        AnswerDO q4a1 = rootAnswer(q4, chen, """
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
                """, "detailed", 12);

        FollowupDO q4f1 = askFollowup(q4, chen, student,
                """
                        追问一下：我的两个项目都是课程作业量级，确实没有高并发场景，
                        按"决策清单"讲取舍会不会显得很假？如果面试官追问数据，我该如实说没有压测过吗？
                        """);
        reply(q4, chen, q4f1, """
                如实说没有高并发场景，不丢人，编数据才丢人。你可以这样讲：项目是课程作业量级，没有真实流量，
                所以我做了本地压测，模拟 500 并发，发现连接池先打满，于是调整了池大小并加了缓存。
                重点不是数据多大，而是你有没有观测、定位、验证的闭环。面试官追问数据，往往是在验证你是不是真的做过。
                """, 4);

        FollowupDO q4f2 = askFollowup(q4, chen, student,
                "再补充一个：面试官问“你最大的缺点是什么”，我回答“太追求完美”，他说这是套路答案。这种题到底想听什么？");
        reply(q4, chen, q4f2, """
                这道题想听的是一个真实、具体、可改进的缺点，而不是完美人设。
                可以选一个和岗位不冲突、但确实影响过你的点，比如“以前做项目喜欢先写代码后补设计，导致返工”，
                然后说你后来怎么改：先画时序图、先写接口契约。
                回答结构是：缺点是什么、带来过什么具体后果、你现在用什么方法约束自己。
                """, 3);

        // ---------- Q5：两个 HR 同题，长内容 ----------
        QuestionDO q5 = newQuestion(student,
                "简历上的项目只有课程作业，怎么写出让人愿意面的内容？",
                """
                        大三在读，准备找暑期实习。简历改了很多版，但投出去几乎没回音。
                        我手上能写的只有三个课程项目：一个图书管理系统、一个仿微博的 Java Web 小站、
                        还有一个用 Spring Boot 做的校园二手交易平台，量级都很小，也没有真实用户。

                        自己写的时候很纠结：写得太细像流水账，写得太概括又像套模板。
                        也看过一些建议说要量化结果，可这几个项目根本没有可量化的业务指标。
                        想请教一下，这种没有实习、没有真实数据的项目，简历应该怎么组织？
                        技术栈要写多深？要不要附上 GitHub 链接？如果面试官问"这个项目上线了吗"，应该怎么回答才不减分？
                        """,
                resume);

        AnswerDO q5a1 = rootAnswer(q5, li, """
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
                """, "detailed", 9);

        FollowupDO q5f1 = askFollowup(q5, li, student,
                """
                        感谢，结构很清晰。再问一个细节：课程项目是小组作业，有三个人一起做的，
                        简历里要不要写清楚自己负责的部分？如果只写我负责的模块，会不会显得项目很单薄？
                        """);
        reply(q5, li, q5f1, """
                要写清楚负责的部分，但不要只写自己那一块。推荐的结构是：先一句话说项目整体目标和团队规模，
                再写你负责的模块、你做的关键决策、以及你和其他人的协作接口。
                比如“三人小组，我负责订单模块与缓存设计，定义了与支付模块的接口契约”。
                这样既不贪功，也不会显得单薄。面试官更在意你是否清楚边界和协作。
                """, 5);

        FollowupDO q5f2 = askFollowup(q5, li, student,
                "再补充一个：简历上要不要写期望薪资？我问了同学，有人说写了会被压价，有人说写了显得有底气。");
        reply(q5, li, q5f2, """
                实习简历一般不建议写期望薪资，校招简历更不需要。薪资是面试通过后谈的，提前写容易给自己设限，
                也可能让筛选者把注意力放到成本上。如果网申表单强制填写，就填一个区间，并注明可面议。
                真正影响你是否被压价的，是手里的选择权和岗位匹配度，而不是简历上那一行字。
                """, 2);

        AnswerDO q5a2 = rootAnswer(q5, chen, """
                从面试官视角补一个建议：课程项目最容易犯的错，是把“做了什么功能”写成了产品说明书。
                筛选时我更想看三件事：你为什么做这个项目、最难的一个技术点是什么、你如何验证它是对的。

                如果项目确实普通，就把一个点写深。比如二手交易平台，不要罗列登录、发布、搜索，
                而是挑“商品列表的分页与缓存”讲清楚：数据量多大、慢查询怎么发现、索引怎么加、缓存怎么失效。
                一个讲透的点，比五个功能名更有说服力。
                """, "detailed", 6);
        FollowupDO q5f3 = askFollowup(q5, chen, student,
                "小组项目里我只负责一个模块，写深了会不会让面试官觉得项目很小？");
        reply(q5, chen, q5f3, """
                不会。项目大小由业务目标决定，不由你负责的模块数量决定。
                你只需要在开头交代整体背景和团队分工，然后聚焦自己的模块讲深。
                反过来，什么都写一点、每块都讲不透，才会让人觉得项目单薄。
                面试官追问的是你的思考深度，不是你敲了多少行代码。
                """, 2);

        // ---------- Q6：两个 HR 同题，长内容 ----------
        QuestionDO q6 = newQuestion(student,
                "国企稳定和大厂平台，第一份实习到底该怎么选？",
                """
                        目前手上有两个暑期实习 offer，都是后端开发方向，时间冲突，只能选一个，比较纠结。

                        A：老家省会的一家国企子公司，做内部管理系统，转正概率高，工作节奏比较慢，
                           据说每天六点能走，但技术栈偏老，用的是较旧的框架，业务也以流程审批为主。
                        B：一线城市的大厂基础架构部门，做中间件相关的事情，面试时感觉团队技术氛围很好，
                           但实习转正率不确定，生活成本也高，而且离家远。

                        我自己的情况是：普通家庭，没有太多试错成本，长期希望留在二线城市生活；
                        但又不甘心第一份工作就去一个技术成长有限的地方。
                        想听听从职业发展角度看，应该更看重平台还是稳定性？如果选了大厂，两年后回二线会不会反而难找工作？
                        """,
                career);

        AnswerDO q6a1 = rootAnswer(q6, wang, """
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
                """, "detailed", 15);

        FollowupDO q6f1 = askFollowup(q6, wang, student,
                """
                        谢谢，最后那段"找在职员工聊 20 分钟"很实用。但我不认识这两个团队的人，
                        在社交平台上直接私信会不会很冒犯？有没有更自然的方式？比如问 HR 能不能安排和未来 mentor 聊一次？
                        """);
        reply(q6, wang, q6f1, """
                不用直接私信陌生人。更自然的方式有三种：一是通过学校就业中心或校友会找在该公司工作的学长学姐；
                二是面试反问环节直接问 HR“能否安排一次和未来 mentor 的交流”；
                三是参加公司开放日或线上宣讲会，会后加招聘同学微信。
                问的时候给对方一个明确的问题清单，别泛泛地说“想了解一下”，多数人愿意花 15 分钟回答具体问题。
                """, 6);

        AnswerDO q6a2 = rootAnswer(q6, zhang, """
                我会用一个更简单的判断：两年后你希望简历上多出哪三行字？
                如果答案是“独立负责过一个高并发模块”“完整经历一次大型项目上线”，那大厂基础架构更匹配；
                如果答案是“熟悉一套复杂业务系统”“能独立对接多个部门”，国企也完全能给你。

                稳定和平台不是对立面，真正的差别是反馈速度和不确定性。
                你可以接受多快的学习节奏、多大的不确定性，这两个问题只有你自己能回答。
                """, "detailed", 7);
        FollowupDO q6f2 = askFollowup(q6, zhang, student,
                "大厂实习转正率不确定，怎么在入职前降低这个风险？");
        reply(q6, zhang, q6f2, """
                入职前把三件事问清楚：这个部门过去两届实习生的留用比例、转正评估看哪些指标、
                是否有明确的转正答辩时间。不要只问 HR，也可以在面试时问未来的 mentor。
                入职后尽早和主管对齐一次目标，把“转正需要做到什么”写成可检查的清单，每月复盘一次。
                风险不能完全消除，但可以提前量化。
                """, 2);


        log.info("""
                [demo] 演示数据已写入：
                  学生 13800000001
                  HR   13800000002 陈静怡 / 字节跳动
                  HR   13800000004 王浩然 / 美团
                  HR   13800000005 李梦琪 / 阿里云
                  HR   13800000006 张帆 / 小红书
                  运营 13800000003（普通审核）
                  运营 13800000008（普通审核）
                  运营 13800000007（仲裁运营）
                  验证码统一 123456""");
    }

    private QuestionDO newQuestion(UserDO author, String title, String body, TagDO... tags) {
        QuestionDO question = new QuestionDO();
        question.setScope("public");
        question.setAuthorId(author.getId());
        question.setTitle(title);
        question.setBody(body);
        question.setModerationStatus("APPROVED");
        question.setVisibility("VISIBLE");
        question.setRiskLevel("NORMAL");
        question.setModeratedAt(LocalDateTime.now());
        questionDao.insert(question);
        for (TagDO tag : tags) {
            tagDao.linkToQuestion(question.getId(), tag.getId());
        }
        return question;
    }

    private AnswerDO rootAnswer(QuestionDO question, UserDO hr, String body, String lengthType, int usefulCount) {
        AnswerDO answer = new AnswerDO();
        answer.setQuestionId(question.getId());
        answer.setAuthorId(hr.getId());
        answer.setBody(body);
        answer.setLengthType(lengthType);
        approve(answer);
        answer.setUsefulCount(usefulCount);
        answerDao.insert(answer);
        questionDao.recordAnswer(question.getId());
        return answer;
    }

    private FollowupDO askFollowup(QuestionDO question, UserDO hr, UserDO student, String body) {
        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(question.getId());
        followup.setHrId(hr.getId());
        followup.setSenderId(student.getId());
        followup.setSenderRole("student");
        followup.setBody(body);
        followup.setScope("public");
        followup.setModerationStatus("APPROVED");
        followup.setVisibility("VISIBLE");
        followup.setRiskLevel("NORMAL");
        followup.setModeratedAt(LocalDateTime.now());
        followupDao.insert(followup);
        questionDao.incrementFollowup(question.getId());
        return followup;
    }

    private void reply(QuestionDO question, UserDO hr, FollowupDO target, String body, int usefulCount) {
        AnswerDO answer = new AnswerDO();
        answer.setQuestionId(question.getId());
        answer.setFollowupId(target.getId());
        answer.setAuthorId(hr.getId());
        answer.setBody(body);
        answer.setLengthType("detailed");
        approve(answer);
        answer.setUsefulCount(usefulCount);
        answerDao.insert(answer);
    }

    private void approve(AnswerDO answer) {
        answer.setModerationStatus("APPROVED");
        answer.setVisibility("VISIBLE");
        answer.setRiskLevel("NORMAL");
        answer.setModeratedAt(LocalDateTime.now());
    }

    private TagDO requireTag(String slug) {
        return tagDao.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("固定标签缺失：" + slug + "，请检查 V5 迁移"));
    }

    private UserDO createHr(String phone, String nickname, String company, String position, String industry) {
        UserDO hr = createUser(phone, "hr", nickname);
        HrProfileDO profile = new HrProfileDO();
        profile.setUserId(hr.getId());
        profile.setCompanyDisplay(company);
        profile.setPosition(position);
        profile.setIndustry(industry);
        profile.setVerifyStatus("approved");
        profile.setVerifiedAt(LocalDateTime.now());
        hrProfileDao.insert(profile);
        return hr;
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