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
