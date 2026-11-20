import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";

const require = createRequire(new URL("../frontend/package.json", import.meta.url));
const ts = require("typescript");
const source = await readFile(new URL("../frontend/src/api/request.ts", import.meta.url), "utf8");
const compiled = ts.transpileModule(
  source.replaceAll("import.meta.env", '({ VITE_API_BASE: "/api/v1" })'),
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } },
).outputText;
const { request, resetCsrfToken, ApiError } = await import(
  `data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`
);

function json(data, status = 200, code = 0) {
  return new Response(JSON.stringify({ code, message: "测试响应", data }), { status });
}

function waitForAbort(signal) {
  return new Promise((_, reject) => {
    const abort = () => reject(signal.reason);
    if (signal.aborted) abort();
    else signal.addEventListener("abort", abort, { once: true });
  });
}

test("CSRF 请求无响应时结束等待，且不会发出提问", async (t) => {
  resetCsrfToken();
  const paths = [];
  t.mock.method(globalThis, "fetch", (url, options) => {
    paths.push(url);
    return waitForAbort(options.signal);
  });
  await assert.rejects(request("/questions", { method: "POST", body: {}, timeoutMs: 20 }),
    error => error instanceof ApiError && error.status === 408 && /请求超时/.test(error.message));
  assert.deepEqual(paths, ["/api/v1/auth/csrf"]);
});

test("提问正文响应停滞时超时，不自动重复写入", async (t) => {
  resetCsrfToken();
  let submissions = 0;
  t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url.endsWith("/csrf")) return json({ token: "token" });
    submissions++;
    return { status: 200, ok: true, json: () => waitForAbort(options.signal) };
  });
  await assert.rejects(request("/questions", { method: "POST", body: {}, timeoutMs: 20 }),
    error => error instanceof ApiError && error.status === 408);
  assert.equal(submissions, 1);
});

test("正常提交携带令牌、会话与原始正文并返回问题", async (t) => {
  resetCsrfToken();
  const payload = { title: "公开问题", body: "正文", scope: "public", tags: [] };
  t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url.endsWith("/csrf")) return json({ token: "fresh-token" });
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.get("X-XSRF-TOKEN"), "fresh-token");
    assert.deepEqual(JSON.parse(options.body), payload);
    return json({ id: 12, ...payload });
  });
  assert.equal((await request("/questions", { method: "POST", body: payload })).id, 12);
});

test("令牌失配时重新获取，原有一次重试仍然有效", async (t) => {
  resetCsrfToken();
  let tokens = 0;
  let submissions = 0;
  t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url.endsWith("/csrf")) return json({ token: `token-${++tokens}` });
    submissions++;
    assert.equal(options.headers.get("X-XSRF-TOKEN"), `token-${tokens}`);
    return submissions === 1 ? json(null, 403, 40300) : json({ id: 15 });
  });
  assert.equal((await request("/questions", { method: "POST", body: {} })).id, 15);
  assert.equal(tokens, 2);
  assert.equal(submissions, 2);
});

test("调用者取消请求时保留取消原因，不误报超时", async (t) => {
  resetCsrfToken();
  const controller = new AbortController();
  t.mock.method(globalThis, "fetch", (_, options) => waitForAbort(options.signal));
  const pending = request("/questions", { signal: controller.signal });
  const reason = new Error("caller cancelled");
  controller.abort(reason);
  await assert.rejects(pending, error => error === reason);
});
