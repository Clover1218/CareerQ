# CareerQ 后端（Spring Boot）

职问桥项目的后端服务。接口契约见 [`../docs/接口契约-职问桥.md`](../docs/接口契约-职问桥.md)，方案见 [`../docs/技术方案-职问桥.md`](../docs/技术方案-职问桥.md)。

## 环境要求

- JDK **21**（本机已装 Temurin 21，`JAVA_HOME` 已配置）
- Maven 3.9+
- 本地开发不需要安装数据库：使用 H2（MySQL 兼容模式）
- 生产使用 MySQL 8

## 快速开始

```bash
# 在仓库根目录
mvn -f backend/pom.xml spring-boot:run
```

启动后：

| 地址 | 说明 |
|---|---|
| http://localhost:8080/api/v1/system/health | 健康检查 |
| http://localhost:8080/actuator/health | Actuator 健康检查 |
| http://localhost:8080/swagger-ui.html | Swagger UI（OpenAPI） |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/h2-console | H2 控制台（dev，JDBC URL `jdbc:h2:mem:careerq`，用户 `sa`，密码空） |

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
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=prod
```

## 数据库迁移

- Flyway 管理，脚本在 `src/main/resources/db/migration/`。
- 约定：迁移脚本保持 MySQL 方言中立，确保 H2（MySQL 模式）与 MySQL 8 都能执行。
- 已包含 `V1__init.sql`：用户、HR 资料、问题（公开/私密）、回答、追问、观点帖、标签、互动、举报、审核记录、词库、审计日志、公开沉淀表。

## 已实现接口（M2–M3）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/v1/auth/sms-code` | 发送验证码（dev 固定 123456） | 公开 |
| POST | `/api/v1/auth/login` | 登录，返回会话 Cookie | 公开 |
| POST | `/api/v1/auth/logout` | 登出 | 登录 |
| GET | `/api/v1/me` | 当前用户 | 登录 |
| POST | `/api/v1/questions` | 提问（`scope`=public/private） | 登录 |
| GET | `/api/v1/questions?tag=&cursor=&limit=` | 公开问题列表 | 公开 |
| GET | `/api/v1/questions/{id}` | 问题详情 + 回答列表 | 公开（私密仅作者/运营） |
| POST | `/api/v1/questions/{id}/answers` | 回答（自动发布/转人工） | HR |
| GET | `/api/v1/hr/question-pool` | HR 未答问题池 | HR |
| POST | `/api/v1/interactions` | 点赞/收藏/有用 | 登录 |

| GET | `/api/v1/hr/private-pool` | HR 私密待认领池（作者匿名） | HR |
| POST | `/api/v1/hr/questions/{id}/claim` | 认领私密问题 | HR |
| POST | `/api/v1/questions/{id}/followups` | 追问（公开/私密） | 登录（私密需可见） |
| GET | `/api/v1/questions/{id}/followups` | 追问列表 | 公开/可见者 |
| POST | `/api/v1/questions/{id}/close` | 关闭私密问题 | 作者 |
| POST | `/api/v1/questions/{id}/publish` | 申请脱敏公开 | 作者 |

| POST | `/api/v1/reports` | 举报（question/answer/post/public_post） | 登录 |
| GET | `/api/v1/posts` | 已审核公开沉淀列表 | 公开 |
| GET | `/api/v1/feed/highlights` | 精选（公开帖 Top10） | 公开 |
| GET | `/api/v1/admin/moderation/queue` | 待人工审核队列 | OPERATOR/ADMIN |
| POST | `/api/v1/admin/moderation/{targetType}/{id}/decide` | 通过/驳回/下架 | OPERATOR/ADMIN |
| GET/POST | `/api/v1/admin/moderation/words` | 词库查询/维护 | OPERATOR/ADMIN |
| GET | `/api/v1/admin/reports` | 举报队列 | OPERATOR/ADMIN |
| POST | `/api/v1/admin/reports/{id}/handle` | 处理举报（dismiss/remove） | OPERATOR/ADMIN |
| GET | `/api/v1/admin/metrics` | 审核/举报计数 | OPERATOR/ADMIN |

## 演示数据（demo profile）

```bash
# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE = "dev,demo"
mvn -f backend/pom.xml spring-boot:run
```

演示账号（dev 验证码固定 `123456`）：学生 `13800000001`、HR `13800000002`、运营 `13800000003`。
数据仅在库为空时写入，重复启动不会重复插入。

## 冒烟测试

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
```

自动构建并启动后端（dev,demo，端口 18080），跑通「公开问答 → 审核 → 回答 → 互动 → 私密工单 → 脱敏公开 → 举报处理」并断言结果。

## 前后端联调

前端（Vue3）接入请见 [`../docs/前端联调指南-职问桥.md`](../docs/前端联调指南-职问桥.md)：
登录三步（sms-code → login → csrf）、Cookie 会话、CSRF 头、Vite 代理、错误码与联调顺序。

OpenAPI 契约：[`../docs/openapi.json`](../docs/openapi.json)（可用 `scripts/export-openapi.ps1` 重新导出）。

## 部署

见 [`../docs/部署说明-职问桥.md`](../docs/部署说明-职问桥.md)（MySQL、环境变量、systemd、Caddy/Nginx 反向代理）。

## 认证（dev）

dev 环境短信验证码固定为 `123456`，不发送真实短信（`careerq.sms.dev-mode=true`）。

```bash
# 1. 发送验证码（dev 下仅打日志）
curl -X POST http://localhost:8080/api/v1/auth/sms-code \
  -H "Content-Type: application/json" -d "{\"phone\":\"13800000000\"}"

# 2. 登录（-c 保存会话 Cookie）
curl -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" -d "{\"phone\":\"13800000000\",\"code\":\"123456\"}"

# 3. 携带会话访问
curl -b cookies.txt http://localhost:8080/api/v1/me

# 4. 登出
curl -b cookies.txt -X POST http://localhost:8080/api/v1/auth/logout
```

## 目录结构

```text
backend/
├── pom.xml
└── src/
    ├── main/java/com/careerq/
    │   ├── CareerQApplication.java
    │   ├── common/      # 统一响应、错误码、异常处理
    │   ├── config/      # 安全（会话 Cookie + CSRF）、OpenAPI
    │   └── system/      # 健康检查
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/ # Flyway 脚本
    └── test/java/com/careerq/
```

## 当前进度

- ✅ M0 环境准备（JDK 21 + 数据库选型 MySQL）
- ✅ M1 后端骨架（工程、统一响应、Flyway V1、健康检查、OpenAPI）
- ⏳ M2 鉴权与用户（下一步）

> 注意：M1 阶段的 Spring Security 只做了基础配置，启动日志中的 “Using generated security password” 属正常现象，M2 接入真实用户体系后会消失。
