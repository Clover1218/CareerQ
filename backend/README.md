# CareerQ 后端（Spring Boot）

## 模块结构（多模块 Maven）

```text
backend/（聚合 pom）
└── shared.all/
    └── product.shared/
        ├── product.service/       服务层契约：Service 接口、DTO、通用响应、登录主体
        ├── product.service.impl/  服务实现、DO、MyBatis DAO，以及可执行应用的启动类、配置和测试
        └── product.api/           API 层：Controller、请求参数、API 异常处理
```

构建命令统一走聚合 pom：`mvn -f backend/pom.xml test`。

## 持久层：SSM（MyBatis）

- `product.service.impl` 内的 `com.careerq.dataobject` 保存数据对象，`com.careerq.dao` 保存 MyBatis Mapper。启动类用 `@MapperScan("com.careerq.dao")` 注册 Mapper。
- Service 使用 Spring 的 `@Transactional` 管理业务事务。MyBatis 使用 Spring Boot 提供的数据源，本地为 H2 MySQL 兼容模式，生产为 MySQL 8。
- Flyway 管理数据库表结构。问题与标签的关系通过原有的 `question_tags` 表读写。

## 构建命令

职问桥项目的后端服务。接口契约见 [`../docs/接口契约-职问桥.md`](../docs/接口契约-职问桥.md)，方案见 [`../docs/技术方案-CareerQ.md`](../docs/技术方案-CareerQ.md)。

## 环境要求

- JDK **21**（本机已装 Temurin 21，`JAVA_HOME` 已配置）
- Maven 3.9+
- 本地开发不需要安装数据库：使用 H2（MySQL 兼容模式）
- 生产使用 MySQL 8

## 快速开始

```bash
# 在仓库根目录
mvn -f backend/pom.xml install -DskipTests
mvn -f backend/pom.xml -pl shared.all/product.shared/product.service.impl spring-boot:run
```

启动后：

| 地址 | 说明 |
|---|---|
| http://localhost:8080/api/v1/system/health | 健康检查 |
| http://localhost:8080/actuator/health | Actuator 健康检查 |
| http://localhost:8080/swagger-ui.html | Swagger UI（OpenAPI） |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/h2-console/ | H2 控制台（dev，JDBC URL `jdbc:h2:mem:careerq;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`，用户 `sa`，密码留空） |

## 测试

```bash
mvn -f backend/pom.xml test
```

## 配置

- `application.yml` 默认 `dev` profile：H2 内存库 + MySQL 兼容模式。
- 生产用 `prod` profile，通过环境变量注入：

| 变量 | 说明 |
|---|---|
| `DB_URL` | JDBC URL，默认 `jdbc:mysql://localhost:3306/careerq` |
| `DB_USERNAME` | 数据库用户 |
| `DB_PASSWORD` | 数据库密码 |

```bash
mvn -f backend/pom.xml install -DskipTests
mvn -f backend/pom.xml -pl shared.all/product.shared/product.service.impl spring-boot:run -Dspring-boot.run.profiles=prod
```

## 数据库迁移

- Flyway 管理，脚本在 `src/main/resources/db/migration/`。
- 约定：迁移脚本保持 MySQL 方言中立，确保 H2（MySQL 模式）与 MySQL 8 都能执行。
- 已有 V1–V9 迁移按原校验内容保留：V8 为公开 Feed 筛选索引，V9 为 HR 回复追问增加关联字段。历史表结构不等于现行功能；不改写已执行迁移，也不自动清除已有数据库数据。
- V10 为追问绑定目标 HR；V11 为 HR 待回复追问统计添加索引。已执行的迁移均保持原样。
- V12 建立追问的目标主回答关联；V13 停用仍缺少该关联的记录及其回复，并校正受影响问题的发布数量。当前接口必须传具体回答，不再接受只指定 HR 的追问。
- V17 新增学生通知表及事件去重索引，并依据现有有效公开回答与非本人点赞补入真实消息；旧迁移保持原样，更新前备份数据库。
- V18 新建独立 HR 原创帖子与标签关联表，第一阶段只提供本人草稿、列表、提交待审和撤回；不开放历史 posts 表，也不改已有迁移。

## 当前主要接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/v1/auth/sms-code` | 发送验证码（dev 固定 123456） | 公开 |
| POST | `/api/v1/auth/login` | 登录，返回会话 Cookie | 公开 |
| POST | `/api/v1/auth/register` | 注册账号并建立会话；HR 需按契约填写角色和公司 | 公开 |
| POST | `/api/v1/auth/logout` | 登出 | 登录 |
| GET | `/api/v1/me` | 当前用户 | 登录 |
| POST | `/api/v1/questions` | 公开提问（`scope` 仅接受 public） | STUDENT |
| GET | `/api/v1/questions?tag=&cursor=&limit=` | 公开问题列表 | 公开 |
| GET | `/api/v1/questions/{id}` | 公开问题详情 + 回答列表 | 公开 |
| POST | `/api/v1/questions/{id}/answers` | 回答公开问题主问题（自动审核） | HR |
| POST | `/api/v1/questions/{id}/followups/{followupId}/answers` | 回复指定的已发布学生追问（沿用回答审核） | HR |
| GET | `/api/v1/hr/question-pool` | HR 待回答池（没有已发布或审核中的回答） | HR |
| GET | `/api/v1/hr/answered-public-questions?cursor=&limit=` | 已回答或回答审核中的公开问题（不区分 HR） | HR |
| GET | `/api/v1/hr/answered-questions?cursor=&limit=&pendingFollowupsOnly=&cursorAt=` | 本人回答记录、待回复/审核中追问统计；支持待回复筛选与时间游标 | HR |
| GET/PATCH | `/api/v1/hr/profile` | HR 本人职业资料与编辑，认证状态只读 | HR |
| GET | `/api/v1/hr/stats` | 已发布主回答、追问回复、获赞、有用统计 | HR |
| GET | `/api/v1/hr/answers?kind=&status=&cursor=&limit=5` | 本人逐条回答记录、类型与状态筛选，末页游标为空 | HR |
| GET | `/api/v1/hr/answers/{id}` | 本人单条回答/回复安全记录 | HR |
| GET | `/api/v1/hr/notifications?category=&unreadOnly=&cursor=&limit=5` | HR 站内通知分类、未读和分页 | HR |
| GET | `/api/v1/hr/notifications/unread-count` | 当前 HR 真实未读数量 | HR |
| POST | `/api/v1/hr/notifications/{id}/read` | 幂等标记本人消息已读 | HR |
| POST | `/api/v1/hr/notifications/read-all` | 按 throughId 快照批量标为已读 | HR |
| POST | `/api/v1/interactions` | 点赞/收藏/有用 | 登录 |
| POST | `/api/v1/questions/{id}/followups` | 对公开问题追问 | STUDENT |
| GET | `/api/v1/questions/{id}/followups` | 获取问题追问 | 公开/问题可见者 |
| POST | `/api/v1/questions/{id}/withdraw` | 撤回本人尚无回答的问题 | 学生作者 |
| POST | `/api/v1/answers/{id}/withdraw` | 撤销本人回答或追问回复 | HR 作者 |
| POST | `/api/v1/questions/{id}/followups/{followupId}/withdraw` | 撤销本人追问 | 学生作者 |
| POST | `/api/v1/reports` | 举报问题或回答（question/answer） | 登录 |
| GET | `/api/v1/admin/moderation/queue` | 待人工审核队列 | OPERATOR/ADMIN |
| POST | `/api/v1/admin/moderation/{targetType}/{id}/decide` | 通过/驳回/下架 | OPERATOR/ADMIN |
| GET/POST | `/api/v1/admin/moderation/words` | 词库查询/维护 | ADMIN |
| GET | `/api/v1/admin/reports` | 举报队列 | OPERATOR/ADMIN |
| POST | `/api/v1/admin/reports/{id}/handle` | 处理举报（dismiss/remove） | OPERATOR/ADMIN |
| GET | `/api/v1/admin/metrics` | 审核/举报计数 | OPERATOR/ADMIN |

回答与追问回复的移除通过 `removalSource` 区分作者撤销（`author`）与运营下架（`operator`）；旧记录来源不明时为 `unknown`。V14 添加内部来源、操作者、时间字段并校正已有回答计数。直接下架与举报下架统一处理，已发布内容仅扣一次，待审/驳回内容不扣，重复处理保留首次来源；公开接口只返回来源，不暴露操作者身份。详见接口契约。

学生追问复用规则审核，返回 `published` / `pending_human` / `rejected`；待审和驳回仅作者可见，待审项进入运营审核队列。只有发布或人工通过后计入公开追问数并通知目标 HR，下架/撤回已发布追问时扣数；待审撤回不扣数、不再允许审核发布。本次不改表结构和已有迁移。

学生注册选填的学校、专业、年级会保存到既有用户资料列，重新登录后仍可读取。学生写请求可携带 `X-CareerQ-Student-Id` 校验页面身份与真实会话是否一致，错号返回 HTTP 409 / code 40902 且不执行写入；该头不替代 Cookie、CSRF、角色与对象归属检查。详见接口契约。

`/feed/questions` 三种排序均附带每条问题的 `viewer` 互动状态，当前页通过一次批量查询读取当前会话的标记，游客全为 false。点赞、收藏及取消继续使用现有互动接口保存，前端刷新后直接恢复已选状态，无需新增数据库迁移。

学生 `/student/notifications` 提供回复、收到的赞、审核与下架消息，以及未读计数、单条和快照批量已读。业务与通知同事务，学生和 HR 的接口及收件箱独立。`/me/favorites` 新增上下文定位字段并在分页前过滤不可见内容。提问草稿在前端按账号保存在本机；详见[学生端功能收尾](../docs/学生端功能收尾-开发说明.md)。

## 演示数据（demo profile）

```bash
# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE = "dev,demo"
mvn -f backend/pom.xml install -DskipTests
mvn -f backend/pom.xml -pl shared.all/product.shared/product.service.impl spring-boot:run
```

演示账号（dev 验证码固定 `123456`）：学生 `13800000001`、HR `13800000002`、运营 `13800000003`、运营 `13800000008`、仲裁运营 `13800000007`。
数据仅在库为空时写入，重复启动不会重复插入。

## 冒烟测试

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
```

自动构建并启动后端（dev,demo，端口 18080），跑通公开问答、审核、回答、互动与举报处理。

## 前后端联调

前端（Vue3）接入请见 [`../docs/前端联调指南-职问桥.md`](../docs/前端联调指南-职问桥.md)：
联调流程包括先获取 CSRF 令牌，再请求验证码，最后注册或登录。

OpenAPI 契约：[`../docs/openapi.json`](../docs/openapi.json)（可用 `scripts/export-openapi.ps1` 重新导出）。

## 部署

见 [`../docs/部署说明-职问桥.md`](../docs/部署说明-职问桥.md)（MySQL、环境变量、systemd、Caddy/Nginx 反向代理）。

## 认证（dev）

开发环境使用固定验证码 `123456`。统一请求层先调用 `GET /api/v1/auth/csrf` 并保存 Cookie，内容写请求携带 `X-XSRF-TOKEN`；认证路径的 CSRF 豁免以安全配置为准。已注册手机号调用 `/api/v1/auth/login`，新手机号调用 `/api/v1/auth/register`。保留会话 Cookie 后，可调用 `GET /api/v1/me` 或提交登出请求。

生产环境在接入真实短信前，发送验证码、登录和注册返回 `50301`。

## 后续开发

HR 原创帖子第一阶段已提供 `/api/v1/hr/posts` 本人列表/详情、`POST /drafts`、`PUT /{id}/draft`、提交审核、撤回及删除草稿，带 clientKey 幂等与版本冲突检查。非拦截内容统一待审核，运营审核和公开展示/学生互动在后续阶段接入，见[分阶段说明](../docs/HR原创帖子-分阶段开发.md)。学生和 HR 的站内消息已接入；站外推送、真实认证和独立运营页面继续后置。历史内容工作流不重新开放，模块分工见[三人全栈模块开发计划](../docs/三人全栈模块开发计划.md)。

## 当前进度

- ✅ M0 环境准备（JDK 21 + 数据库选型 MySQL）
- ✅ M1 后端骨架（工程、统一响应、Flyway V1、健康检查、OpenAPI）
- ✅ M2 开发环境登录、注册与会话鉴权，生产短信待接入
