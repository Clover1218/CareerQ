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

职问桥项目的后端服务。接口契约见 [`../docs/接口契约-职问桥.md`](../docs/接口契约-职问桥.md)，方案见 [`../docs/技术方案-职问桥.md`](../docs/技术方案-职问桥.md)。

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

Windows 本地演示也可在打包成功后执行 `powershell -NoProfile -File scripts/run-local-backend.ps1`。
该脚本把完整程序包复制到独立运行目录，避免后续构建覆盖运行中的 JAR；使用 `dev,demo` 和本地 H2 文件库，重启后保留开发数据。
运行包和数据库保存在被 Git 忽略的 `data/local-runtime/`。此方式的 H2 控制台 URL 为 `jdbc:h2:file:./data/local-runtime/database/careerq;MODE=MySQL;DATABASE_TO_LOWER=TRUE`，用户 `sa`，密码留空。

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
- Flyway 迁移包含 V1–V9：V8 为公开 Feed 筛选索引，V9 为 HR 回复追问增加关联字段。V1 中的私密问答表结构暂留作历史数据兼容，不再开放私密问答流程。

## 已实现接口（M2–M3）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/v1/auth/sms-code` | 发送验证码（dev 固定 123456） | 公开 |
| POST | `/api/v1/auth/login` | 登录，返回会话 Cookie | 公开 |
| POST | `/api/v1/auth/register` | 注册学生账号并建立会话 | 公开 |
| POST | `/api/v1/auth/logout` | 登出 | 登录 |
| GET | `/api/v1/me` | 当前用户 | 登录 |
| POST | `/api/v1/questions` | 公开提问（`scope` 仅接受 public） | STUDENT |
| GET | `/api/v1/questions?tag=&cursor=&limit=` | 公开问题列表 | 公开 |
| GET | `/api/v1/questions/{id}` | 公开问题详情 + 回答列表 | 公开 |
| POST | `/api/v1/questions/{id}/answers` | 回答公开问题主问题（自动审核） | HR |
| POST | `/api/v1/questions/{id}/followups/{followupId}/answers` | 回复指定的已发布学生追问（沿用回答审核） | HR |
| GET | `/api/v1/hr/question-pool` | HR 待回答池（没有已发布或审核中的回答） | HR |
| GET | `/api/v1/hr/answered-public-questions?cursor=&limit=` | 已回答或回答审核中的公开问题（不区分 HR） | HR |
| GET | `/api/v1/hr/answered-questions?cursor=&limit=` | 当前 HR 提交过回答的公开问题 | HR |
| POST | `/api/v1/interactions` | 点赞/收藏/有用 | 登录 |
| POST | `/api/v1/questions/{id}/followups` | 对公开问题追问 | STUDENT |
| GET | `/api/v1/questions/{id}/followups` | 获取问题追问 | 公开/问题可见者 |

| POST | `/api/v1/reports` | 举报（question/answer/post/public_post） | 登录 |
| GET | `/api/v1/posts` | 已审核公开沉淀列表 | 公开 |
| GET | `/api/v1/feed/highlights` | 精选（公开帖 Top10） | 公开 |
| GET | `/api/v1/admin/moderation/queue` | 待人工审核队列 | OPERATOR/ADMIN |
| POST | `/api/v1/admin/moderation/{targetType}/{id}/decide` | 通过/驳回/下架 | OPERATOR/ADMIN |
| GET/POST | `/api/v1/admin/moderation/words` | 词库查询/维护 | ADMIN |
| GET | `/api/v1/admin/reports` | 举报队列 | OPERATOR/ADMIN |
| POST | `/api/v1/admin/reports/{id}/handle` | 处理举报（dismiss/remove） | OPERATOR/ADMIN |
| GET | `/api/v1/admin/metrics` | 审核/举报计数 | OPERATOR/ADMIN |

## 演示数据（demo profile）

```bash
# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE = "dev,demo"
mvn -f backend/pom.xml install -DskipTests
mvn -f backend/pom.xml -pl shared.all/product.shared/product.service.impl spring-boot:run
```

演示账号（dev 验证码固定 `123456`）：学生 `13800000001`、HR `13800000002`、运营 `13800000003`。
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

开发环境使用固定验证码 `123456`。先调用 `GET /api/v1/auth/csrf`，保存 Cookie，并在认证 POST 请求中发送 `X-XSRF-TOKEN`。已注册手机号调用 `/api/v1/auth/login`，新手机号调用 `/api/v1/auth/register`。保留会话 Cookie 后，可调用 `GET /api/v1/me` 或提交登出请求。

生产环境在接入真实短信前，发送验证码、登录和注册返回 `50301`。

## 包与依赖边界

```text
product.api ────────────> product.service
product.service.impl ──> product.api + product.service

product.service/src/main/java/com/careerq/
├── service/                     # Service 接口
├── common/、security/           # 公共响应与登录主体
└── auth/、question/、…          # DTO

product.api/src/main/java/com/careerq/
├── controller/                  # Spring MVC Controller
├── common/                      # API 异常处理
└── param/                       # 请求参数

product.service.impl/src/
├── main/java/com/careerq/
│   ├── CareerQApplication.java  # 应用入口、Mapper 扫描
│   ├── service/impl/            # 服务实现与内部读取辅助类
│   ├── dao/、dataobject/        # MyBatis Mapper 与 DO
│   └── config/、bootstrap/、feed/
├── main/resources/
│   ├── application.yml
│   └── db/migration/            # Flyway 迁移
└── test/java/com/careerq/        # 业务与查询次数回归测试
```

- Controller 通过 Service 接口访问业务；DTO 不依赖 DAO、DO 或实现模块。
- 列表展示所需的作者、标签、互动状态批量读取。作者查询仅选择 `id`、`nickname`；不读取手机号等完整账号字段。
- `ContentReadSupport` 仅在单次调用中汇总展示数据，不保存跨请求缓存；作者和互动查询每批最多 500 个去重 ID，空集合不发起批量 SQL。
- `ContentReadPerformanceTests` 对实际执行的数据库 SELECT 计数，覆盖分页、内容可见性、互动隔离和超过 500 条的批次边界。

## 当前进度

- ✅ M0 环境准备（JDK 21 + 数据库选型 MySQL）
- ✅ M1 后端骨架（工程、统一响应、Flyway V1、健康检查、OpenAPI）
- ✅ M2 开发环境登录、注册与会话鉴权，生产短信待接入
