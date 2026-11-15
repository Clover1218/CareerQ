# 导出 OpenAPI 契约

从运行中的后端导出 OpenAPI JSON，供前端生成类型或做接口联调。

## 用法

```bash
# 需要先构建：mvn -f backend/pom.xml package -DskipTests
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/export-openapi.ps1
```

默认输出到 `docs/openapi.json`，端口使用 18081（避免与本地 8080 冲突）。

## 参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `-Port` | 18081 | 临时端口 |
| `-Out` | docs/openapi.json | 输出路径 |

## 前端用法

- Swagger UI：启动后端后访问 `http://localhost:8080/swagger-ui.html`
- 类型生成：基于 `docs/openapi.json` 使用 `openapi-typescript` 等工具生成 `types.d.ts`
