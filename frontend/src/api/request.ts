/**
 * 统一请求层：原生 fetch + Cookie 会话 + CSRF 双提交校验。
 * 只负责传输与错误归一化，业务接口见 src/api/index.ts。
 */

const BASE = import.meta.env.VITE_API_BASE ?? "/api/v1";
import { SESSION_REFRESH_EVENT } from "@/utils/sessionSync";

let studentIdentity: number | null = null;
export function setStudentRequestIdentity(id: number | null) { studentIdentity = id; }

/** 后端统一响应结构：{ code, message, data } */
export interface ApiEnvelope<T> {
  code: number;
  message?: string;
  data: T;
}

/** 业务/HTTP 错误的统一类型，code 为后端业务码，status 为 HTTP 状态码 */
export class ApiError extends Error {
  readonly code: number;
  readonly status: number;

  constructor(message: string, code: number, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

let csrfToken: string | null = null;

/** 登录/注册/登出后服务端会轮换会话，必须丢弃旧 token */
export function resetCsrfToken(): void {
  csrfToken = null;
}

async function fetchCsrfToken(): Promise<string> {
  const res = await fetch(`${BASE}/auth/csrf`, { credentials: "include" });
  const body = (await res.json().catch(() => null)) as ApiEnvelope<{ token: string }> | null;

  if (!res.ok || !body || body.code !== 0) {
    throw new ApiError(body?.message ?? `获取 CSRF Token 失败（HTTP ${res.status}）`, body?.code ?? -1, res.status);
  }
  csrfToken = body.data.token;
  return csrfToken;
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  /** JSON 请求体，自动序列化；需要传 FormData 时用 rawBody */
  body?: unknown;
  /** 原始请求体（FormData / Blob 等），设置后不再自动序列化 */
  rawBody?: BodyInit;
  /** 跳过 CSRF 头，仅用于确实不需要校验的场景 */
  skipCsrf?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method: rawMethod = "GET", body, rawBody, skipCsrf = false, headers: rawHeaders, ...rest } = options;
  const method = rawMethod.toUpperCase();
  const needsCsrf = !skipCsrf && method !== "GET" && method !== "HEAD";
  // 固定发起操作时看到的学生身份，重试 CSRF 时也不能悄悄改成另一个账号。
  const expectedStudent = needsCsrf && !path.startsWith("/auth/") ? studentIdentity : null;
  const payload = rawBody ?? (body === undefined ? undefined : JSON.stringify(body));

  const send = async (): Promise<Response> => {
    const headers = new Headers(rawHeaders);
    headers.set("Accept", "application/json");
    if (rawBody === undefined) {
      headers.set("Content-Type", "application/json; charset=utf-8");
    }
    // 写请求携带 CSRF Token；首次调用时懒加载并缓存
    if (needsCsrf) {
      headers.set("X-XSRF-TOKEN", csrfToken ?? (await fetchCsrfToken()));
    }
    if (expectedStudent != null) headers.set("X-CareerQ-Student-Id", String(expectedStudent));
    return fetch(`${BASE}${path}`, {
      ...rest,
      method,
      headers,
      credentials: "include",
      body: payload,
    });
  };

  let res = await send();

  // token 只缓存在内存，多标签页登录、会话轮换或后端重启后可能与 Cookie 失配；
  // 403 时重新拉一次 token 再试。CSRF 校验在业务执行前完成，重试不会重复写入
  if (res.status === 403 && needsCsrf) {
    resetCsrfToken();
    res = await send();
  }

  // 会话过期：丢掉缓存，下一次写请求会重新获取
  if (res.status === 401) {
    resetCsrfToken();
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const envelope = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (envelope?.code === 40902) window.dispatchEvent(new Event(SESSION_REFRESH_EVENT));

  if (!res.ok || !envelope || envelope.code !== 0) {
    throw new ApiError(envelope?.message ?? `HTTP ${res.status}`, envelope?.code ?? -1, res.status);
  }

  return envelope.data;
}
