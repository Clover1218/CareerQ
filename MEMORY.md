# CareerQ 项目长期记忆

> 开工顺序：① `记忆书写规范.md` → ② 当天自己的 `YYYY-MM-DD_<署名>.md` 及同日期其他成员日志（没有就取最近一份）→ ③ 本文件。根目录早期 `YYYY-MM-DD.md` 仅作历史记录。

## 速查表

| 项 | 值 |
|---|---|
| 项目 | CareerQ / 职问桥（长期项目，王、季、李共同开发；2026-11-14 起**脱离比赛约束**） |
| 人类团队 | 王、季、李；具体分工暂未确定 |
| 对外名称 | **职问桥**　Slogan：让好问题遇见对的人，让经验公开沉淀 |
| 定位 | 面向大学生与 HR 的公开、异步、结构化职业问答信息库，不做私聊社交 |
| 策划书 | `docs/项目策划书-职问桥.md`（项目单一事实来源，改动须在提交正文说明） |
| 后端结构 | Maven 三个业务模块。 `product.api` 放 Spring MVC Controller，`product.service` 放接口和 DTO，`product.service.impl` 放服务实现、启动类及 MyBatis 持久层（`com.careerq.dao` 和 `com.careerq.dataobject`）。 |
| 构建命令 | `mvn -f backend/pom.xml test`；打包 `mvn -f backend/pom.xml package -DskipTests`，jar 在 `product.service.impl/target/` |
| 技术方案 | `docs/技术方案-职问桥.md`（**v0.5 实施版**，Spring Boot + 自建部署） |
| 接口契约 | `docs/接口契约-职问桥.md`（前后端联调唯一依据，v1.1） |
| 实施计划 | `docs/实施计划-职问桥.md`（M0—M6 里程碑，进行中） |
| 开发平台 | **自建部署**：前端静态（Nginx/CDN）+ Spring Boot + MySQL 8；Qoder Sites 不再强制 |
| Web 技术栈 | 前端：**Vue3 + TypeScript + Vite**（2026-11-18 起为 Pinia + NaiveUI + Tailwind，PR #7 重构版）；后端：**Spring Boot 3 + Java 21 + MySQL 8 + Flyway**（用户负责） |
| 鉴权 | **Cookie 会话 + CSRF Token**；登录/注册分离：未注册登录返回 40401，`POST /auth/register` 注册并建会话 |
| ⚠️ 分工 | **用户本人负责 Spring Boot 后端与前后端连接**；前端界面由队友负责；Qoder Sites 与 Spring Boot 的关系待确认 |
| ✅ 已裁决 | 2026-11-14：采用**方案 C（公开提问 + 私密工单混合）**；Qoder Sites 强制。详见 `docs/流程v2评估-私密问答与公开沉淀.md` |
| Web 技术栈 | 响应式 Web：Vue3 + TypeScript + Vite；FastAPI + PostgreSQL + Redis；Caddy/CDN |
| 工作区 | `E:\CareerQ`（2026-11-15 起；早期记录写 `D:\CareerQ`） |
| 本地服务 | 后端 `http://localhost:8080`（`dev,demo`，H2 内存库）；前端 `http://127.0.0.1:5173/careerq/`（`pnpm dev`）；演示账号 `13800000001/2/3`，验证码 `123456` |
| 远端仓库 | `https://github.com/Clover1218/CareerQ.git`（`main` 已建立，2026-11-11 首次推送成功） |
| 主干规则 | 所有改动经 PR 合并进入 `main`；禁止直接推送或强制推送 `main` |
| 记忆规范 | `记忆书写规范.md`（六条铁律 + 三层记忆 + 写作模板） |
| 日志 | 当天 `YYYY-MM-DD_<署名>.md`，每位成员或 AI 只追加自己的文件 |
| 共享交换区 | `shared/`，AI 只写自己前缀的文件 |

## 一、人类团队、AI 署名与共享区前缀

### 人类开发团队

| 成员 | 开发分工 |
|---|---|
| 王 | 用户端与运营后台前端；响应式页面、API 对接、前端测试，并指导李完成辅助组件 |
| 季 | 后端与平台基础；数据库、登录权限、问答互动、审核状态机、CI 与部署 |
| 李 | 产品质量与集成；验收用例、测试数据、联调与演示验收，并在王指导下承担辅助前端开发 |

### AI 协作者

| 模型 | 署名 | 共享区前缀 | 说明 |
|---|---|---|---|
| GPT (Codex) | Codex | `CODEX_` | 2026-11-11 已确认 |
| Qorder | Qorder | `QORDER_` | 2026-11-11 本模型已确认 |
| DeepSeek | DeepSeek | `DS_` | 2026-11-11 确认 |

- AI 是辅助开发方，不计入三名人类开发成员，也不默认担任人类 owner 或审查者。
- 三个 AI 各自署名，便于追溯记录；**不得写别人的前缀**，否则等于覆盖他人记忆。
- 署名可随用户要求调整，调整后同步更新本表和当天日志。

## 二·协作方式（2026-11-15 更新）

- **后端 `backend/` 由用户本人主写。Codex 负责审查用户完成的新改动。**
- 用户通知改动完成后，Codex 检查此次差异及受影响的调用链，按严重程度报告问题、代码位置、影响和验证结果。需要修复时，由用户指定范围，按一个文件或接口小步处理。
- `frontend/` 是 AI 生成的参考脚手架，**保留但不扩写**，不作为主线。

## 二、分支、提交与 PR 约定（协作必需）

- **所有改动进入 `main` 必须通过 Pull Request 合并**；禁止直接推送或强制推送 `main`。任务分支可以推送，用于保存进度和创建 PR。
- 每项任务使用独立分支，建议命名为 `feature/<任务>`、`fix/<问题>` 或 `docs/<主题>`；开发前同步 `main`，提交 PR 前将任务分支更新到最新 `main` 并解决冲突。
- PR 至少由另一名人类成员审查通过；AI 审查意见可以辅助，但不计入人类审查人数。已配置的自动检查必须全部通过后才可合并。
- 文件 owner 负责任务协调和 PR 审查，不禁止其他成员通过独立分支和 PR 参与同一文件。
- 提交标题概括改动，正文写明：**改了什么 / 为什么改 / 验证结果**。
- 未验证的事项必须如实标注“未验证”，不得写成已验证。
- Git 作者与 PR 创建者使用实际操作的人类成员账号；AI 协助时在提交正文署名 `Codex` / `DeepSeek` / `Qorder`，不冒充人类审查者。AI 协作者同样不得直接推送 `main`。

## 三、⚠️ 铁律与坑（摘自记忆规范，违反会出事）

1. 先读后写；每位成员或 AI 的日志只追加不覆盖；AI 共享区只写自己前缀；写完回读验证。
2. 不写密钥、临时路径、搜索结果原文。
3. Windows 文件名不区分大小写：`memory.md` 会覆盖 `MEMORY.md`。
4. 编辑工具可能静默不落盘：关键文件改完必须回读验证。
5. 同机 AI 防撞车：改前建 `.<name>_editing` 锁文件（内容写正在改什么），改完删；见到锁就别动。锁文件被 `.gitignore` 忽略，只对当前工作区有效；跨成员协调必须使用 GitHub Issue、PR 和 owner 分配。
6. `MEMORY.md` 过长会被注入截断，细节下沉到 `MEMORY_详细.md`。

## 四、当前进度

- 2026-11-11：记忆规范确认；三方署名与前缀确立并由各自模型确认（Codex、DeepSeek、Qorder）；本地仓库 `main` 已建并接入远端。
- **远端读已验证**：`git ls-remote` 退出码 0（仓库公开，匿名也可读）。
- **远端写已验证**：2026-11-11 由 DeepSeek 完成**首次真实推送**（`git push -u origin main` 退出码 0，`* [new branch] main -> main`），远端 `refs/heads/main` = `d940d17`，与本地 `HEAD` 一致；Qorder 此前记录的 dry-run 结论已由真实推送补证。
- 用户最新协作要求（2026-11-12）：所有改动进入 `main` 必须通过 PR 合并，禁止直接推送或强制推送 `main`；任务分支可以推送，用于保存进度和创建 PR。此前「完成改动即可直接推送」的约定失效。
- 待办：由仓库管理员确认并配置 GitHub `main` 分支保护，要求 PR 合并、至少 1 名其他人类成员审查通过，并禁止强制推送。
- 2026-11-12：新增 `.gitattributes`，将 Markdown 统一为 LF 行尾，避免多人编辑产生整文件行尾差异。
- 2026-11-12：比赛选题落档为**《CareerQ》**，策划书见 `docs/项目策划书-CareerQ.md`，记忆骨架见「五、项目概要」。
- 2026-11-12：用户选定比赛交付物为**技术方案 / 架构设计**；DeepSeek 协助起草 `docs/技术方案-CareerQ.md` v0.1，并在 `shared/DS_技术方案分工建议.md` 声明文件归属，待评审与认领。
- 2026-11-13：落实李提出的**响应式 Web 站点**要求，不做微信小程序；技术方案升级为 v0.2 Web 修订草案，登录改为手机短信验证码 + Web 会话，用户端与运营后台统一使用 Vue3 + TypeScript。
- 2026-11-14：用户提供流程 v2（**私密问答 → 可选公开沉淀**），与 v1「不做私聊、全部公开」直接冲突。DeepSeek 落档评估文档，给出 A/B/C 三方案。
- 2026-11-14 用户裁决：采用**方案 C（公开提问 + 私密工单混合）**；**Qoder Sites 为比赛强制平台**。技术方案升级 **v0.3**（Qoder Sites 托管，FastAPI / PostgreSQL / Redis / Caddy 退出主线），策划书同步为混合形态。
- 2026-11-14 后端栈修订：用户负责 **Spring Boot 后端与前后端连接**，前端界面由队友负责；技术方案升 **v0.4**（Sites 可能仅托管前端，后端 Spring Boot 独立部署），新增 `docs/接口契约-职问桥.md`。Qoder Sites 与 Spring Boot 的部署关系待确认。
- 2026-11-14 前后端约定锁定：前端 **Vue3 + TypeScript**；鉴权 **Cookie 会话 + CSRF Token**；Qoder Sites 暂按「只托管前端静态资源」推进，后端 Spring Boot 独立部署。
- ⚠️ 路径不一致待确认：`MEMORY.md` 速查表「工作区」写 `D:\CareerQ`，本会话实际为 `E:\CareerQ`。
- 2026-11-14 脱离比赛：Qoder Sites 强制取消，改自建部署；技术方案升 **v0.5 实施版**，新建 `docs/实施计划-职问桥.md`（M0—M6）。环境实测：**Java 11 不满足 Spring Boot 3（需 JDK 21）**，Maven/Node 可用，Docker 与 PostgreSQL 未装。下一步 M0。
- 2026-11-14 ⚠️ 同机并发事故：另一会话中途切换分支并把未提交 M2 改动收进 `wip/m2-auth`；已 ff 合并回 `ds/private-qna-flow`。教训：动手前确认分支 + 建锁，提交前再确认分支。
- 2026-11-14 按用户模块结构重构 backend：拆为 `product.service.api` / `product.service.impl` / `product.api` 三模块（Maven 聚合），Java 21；`mvn test` 16/16、冒烟 14 项通过。模块名按用户更正为 `product.service.impl`。
- 2026-11-14 联调资产：新增 `docs/前端联调指南-职问桥.md` 与 `docs/openapi.json`（25 个 path）、`scripts/export-openapi.ps1`；接口契约与实现对齐（幂等键、错误体）。Vue3 前端代码仍未入库，联调未开始。
- 2026-11-14 M6 完成：CORS、demo 种子数据、部署说明（`docs/部署说明-职问桥.md`）、全链路冒烟脚本（14 项断言通过）。M0–M6 全部完成，待前端实际联调。
- 2026-11-14 M5 完成：审核与举报（词库/机审/人审队列/审核留痕/举报处理/公开帖审核展示/指标计数），`mvn test` 16/16 通过。下一步 M6 联调与部署。
- 2026-11-14 M4 完成：私密工单闭环（私密池/认领/追问/关闭/脱敏转公开），`mvn test` 10/10 通过。下一步 M5 审核与举报。
- 2026-11-14 M3 完成：公开问答核心（提问/列表/详情/回答/标签/互动/HR 问题池），`mvn test` 8/8 通过；手机号/HR 角色、CSRF、403 处理均已验证。下一步 M4 私密工单。
- 2026-11-14 M2 完成：短信验证码（dev 固定 123456）+ 会话登录/登出 + `/api/v1/me` + 401/403 JSON；`mvn test` 5/5 通过；真实 HTTP 冒烟通过。下一步 M3 公开问答。
- 2026-11-14 M0/M1 完成：JDK 21（Temurin 21.0.13）已装且 JAVA_HOME 已设；数据库定 **MySQL 8**（dev 用 H2 MySQL 模式）；`backend/` 骨架建成，`mvn test` 通过，Flyway V1 全量表迁移成功。下一步 M2 鉴权。
- 2026-11-15 公开问答重构：feed 摘要 → `/content` → `/thread` 三态懒加载；前端 FeedCard1/2/3 与 Figma 对齐并做响应式页头；后端 37 项测试、`pnpm build` 通过；`docs/openapi.json` 重新生成（30 个 path）。注意：当前 shell 的 `JAVA_HOME` 可能仍是 JDK 17，Maven 前需指向 JDK 21。
- 2026-11-16 公开问答交互修订：去头像；feed 的 `excerpt` 改为完整正文（删 `truncated` 与 120 字截断），每张卡片固定「阅读全文」；全文与回答区互相独立；发送按钮随输入框焦点显隐（按钮需 `@mousedown.prevent`）。37 项测试与构建通过。
- 2026-11-19 公开问答筛选：后端 `/api/v1/feed/questions` 支持 `q`、`tags`（多标签 AND）、`period/from/to`、`sort=recommend|latest|hot` 与不透明游标（快照+种子，筛选指纹校验）；前端新增 `FeedFilterPanel`（搜索、标签复用 TagPicker、时间预设+自定义起止、排序、换一换）并接入列表页。注意：naive-ui 日期范围面板首次点击即下发 `[start,start]`，需自维护「选择中」阶段，否则会在首击就关闭并只筛一天。详见 `2026-11-19_QORDER.md`。
- 2026-11-19 学生端前端完善：顶栏头像 + 角色浮层、消息栏（回复我的/收到的赞/系统消息）、我的问答默认 3 条可展开、历史浏览标签（localStorage）、学生页头像上传（`POST /me/avatar`，PNG/JPEG/WEBP ≤2MB）、退出登录全屏居中确认弹窗；修复顶栏吸顶（fixed）、滚动条占位（scrollbar-gutter）导致切换横向位移、Vite Windows HMR 偶发失效（`server.watch.usePolling`）。
- 2026-11-19 提问撤回：后端 `POST /api/v1/questions/{id}/withdraw`（仅作者、无回答、待审核/已发布可撤回，状态 `removed`，公开列表隐藏）；前端「我的问答」撤回按钮 + 二次确认 + 「已撤回」标签；新增 `WithdrawFlowTests`，全量 `mvn test` **77/77**、`pnpm build` 通过。
- 2026-11-19 ⚠️ PR 事故与暂停：PR #13 已合入 main；PR #14 为 stacked PR（base `ji/no-private-no-comments`），在该 base 先合入 main 后才合并，导致 6 个前端提交**未进入 main**；PR #16 因 main 被 PR #15 改动而冲突，**按用户要求已关闭**。`main`=`5e9b7e9`；本地/远端分支 `ji/student-frontend`=`afefe55` 保留全部功能。用户要求暂停提交/合并/回滚，待决定：rebase 补回全部前端功能 / 只摘「提问撤回」 / 保持本地。
- 2026-11-19 同日后续：个人信息弹出抽屉（ProfileDrawer）、悬停头像资料摘要、我的问答右侧资料栏 sticky、历史浏览单条删除图标 + `?tab=history` 自动切标签；回复/追问撤销（`POST /answers/{id}/withdraw`、`POST /questions/{qid}/followups/{fid}/withdraw`，仅作者、计数递减，新增 `ReplyWithdrawFlowTests`），全量 `mvn test` **87/87**、`pnpm build` 通过。Codex 的并发改动文件未纳入本次提交。

## 五、项目概要（CareerQ）

详见 `docs/项目策划书-CareerQ.md`，此处只留人类团队成员与 AI 协作者必须知道的骨架。

- **不做**：私信、好友、实时聊天、匿名树洞、招聘投递、简历交易、付费咨询。这是产品定义，不是妥协。
- **MVP**：学生提问（标题/描述/标签/公开）→ HR 从问题池选答（「一句话建议」或「详细经验」）→ 点赞/收藏/标记有用 → 公开追问；HR 可发观点帖；运营精选；举报与审核后台（AI 初筛 + 人工复核）；身份认证；标签搜索。
- **冷启动**：邀请 10—15 位 HR + 1—2 个学院学生，先由运营抛高质量问题、定向邀请作答；核心指标是「48 小时内至少 1 个有效回答」，不是用户数。
- **路线图**：MVP 第 1—4 周 → 冷启动 5—8 周 → 内容沉淀 9—12 周 → 扩展 13 周起。
- **合规红线**：公开异步 + 留痕可审核；HR 展示为「某互联网公司 HR」不露真名；不冒充学校官方平台；上线前找法律/合规咨询。
- **团队分工**：王负责用户端与运营后台前端，季负责后端、审核与平台基础，李负责产品质量、测试数据、联调验收并辅助前端。主责不独占文件；其他成员可通过独立分支和 PR 参与，owner 负责协调与审查，AI 只承担协助工作。

> 连接远端的三层判定（反复踩）：**地址可达 → 读权限 → 写权限**。公开空仓库让前两层自动成立，只有真实 push 能证明第三层。

## 数据库（2026-11-14 实测）

- 阿里云 RDS MySQL 8.0.36，独立库 `careerq`（utf8mb4）；`dewudb` 已被其他项目占用，不复用。
- 凭证只走环境变量 `SPRING_DATASOURCE_*` / `DB_*`，不入仓库；`scripts/verify-mysql.ps1` 可在真库复验。
