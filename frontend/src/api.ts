const BASE = "/api/v1";

export interface User {
  id: number;
  role: "student" | "hr" | "operator" | "admin";
  nickname: string;
  avatarUrl?: string | null;
  school?: string | null;
  major?: string | null;
  grade?: string | null;
}

export interface UserBrief {
  id: number;
  nickname: string;
}

export interface Question {
  id: number;
  scope: "public" | "private";
  status: string;
  title: string;
  body: string;
  tags: string[];
  author: UserBrief;
  createdAt: string;
  answerCount: number;
  usefulCount: number;
  firstAnswerAt?: string | null;
}

export interface Answer {
  id: number;
  questionId: number;
  body: string;
  lengthType: string;
  status: string;
  author: UserBrief;
  likeCount: number;
  usefulCount: number;
  createdAt: string;
}

export interface Followup {
  id: number;
  questionId: number;
  senderRole: string;
  body: string;
  status: string;
  createdAt: string;
}

export interface PublicPost {
  id: number;
  title: string;
  questionBody: string;
  hrAnswer: string;
}

export interface QuestionDetail {
  question: Question;
  answers: Answer[];
  followups: Followup[];
}

export interface PageResult<T> {
  items: T[];
  nextCursor: number | null;
}

export interface Experience {
  id?: number;
  type: "internship" | "project";
  title: string;
  role?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  description?: string | null;
}

export interface Profile {
  id: number;
  role: string;
  nickname: string;
  avatarUrl?: string | null;
  school?: string | null;
  major?: string | null;
  grade?: string | null;
  degree?: string | null;
  graduationYear?: string | null;
  experiences: Experience[];
}

export interface Favorite {
  targetType: "question" | "answer" | "post";
  targetId: number;
  title: string;
  excerpt?: string | null;
  createdAt: string;
}
export class ApiError extends Error {
  code: number;
  status: number;

  constructor(message: string, code: number, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

let csrfToken: string | null = null;

async function ensureCsrf(): Promise<string> {
  const res = await fetch(`${BASE}/auth/csrf`, { credentials: "include" });
  const body = await res.json();
  csrfToken = body.data.token as string;
  return csrfToken;
}

async function request<T>(path: string, init: RequestInit = {}, csrfRetried = false): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json; charset=utf-8");

  if (method !== "GET" && method !== "HEAD") {
    if (!csrfToken) await ensureCsrf();
    headers.set("X-XSRF-TOKEN", csrfToken as string);
  }

  const res = await fetch(`${BASE}${path}`, {
    ...init,
    method,
    headers,
    credentials: "include",
  });
  const body = await res.json().catch(() => ({ code: -1, message: `HTTP ${res.status}` }));

  if (!res.ok || body.code !== 0) {
    if (res.status === 401) {
      csrfToken = null;
    }
    // CSRF token 过期 / 后端重启导致的 403：刷新 token 后自动重试一次
    if (res.status === 403 && body.code === 40300
        && method !== "GET" && method !== "HEAD" && !csrfRetried) {
      csrfToken = null;
      await ensureCsrf();
      return request<T>(path, init, true);
    }
    throw new ApiError(body.message ?? `HTTP ${res.status}`, body.code ?? -1, res.status);
  }
  return body.data as T;
}

export const api = {
  async sendCode(phone: string): Promise<void> {
    await request<void>("/auth/sms-code", {
      method: "POST",
      body: JSON.stringify({ phone }),
    });
  },

  async register(payload: {
    phone: string;
    code: string;
    nickname?: string;



    role?: "student" | "hr";
    company?: string;
    industry?: string;
    position?: string;
  }): Promise<void> {
    await request<{ user: User }>("/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    csrfToken = null;
  },

  async login(phone: string, code: string): Promise<void> {
    await request<{ user: User }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ phone, code }),
    });
    csrfToken = null;
  },

  async logout(): Promise<void> {
    await request<void>("/auth/logout", { method: "POST" });
    csrfToken = null;
  },

  me(): Promise<User> {
    return request<User>("/me");
  },

  listQuestions(tag?: string, cursor?: number | null): Promise<PageResult<Question>> {
    const params = new URLSearchParams();
    if (tag) params.set("tag", tag);
    if (cursor) params.set("cursor", String(cursor));
    params.set("limit", "20");
    return request<PageResult<Question>>(`/questions?${params.toString()}`);
  },

  getQuestion(id: number): Promise<QuestionDetail> {
    return request<QuestionDetail>(`/questions/${id}`);
  },

  createQuestion(payload: {
    title: string;
    body: string;
    tags: string[];
    scope: "public" | "private";
  }): Promise<Question> {
    return request<Question>("/questions", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  answer(questionId: number, body: string, lengthType = "detailed"): Promise<Answer> {
    return request<Answer>(`/questions/${questionId}/answers`, {
      method: "POST",
      body: JSON.stringify({ body, lengthType }),
    });
  },

  followup(questionId: number, body: string): Promise<Followup> {
    return request<Followup>(`/questions/${questionId}/followups`, {
      method: "POST",
      body: JSON.stringify({ body }),
    });
  },

  interact(targetType: string, targetId: number, type: string): Promise<void> {
    return request<void>("/interactions", {
      method: "POST",
      body: JSON.stringify({ targetType, targetId, type }),
    });
  },

  privatePool(): Promise<PageResult<Question>> {
    return request<PageResult<Question>>("/hr/private-pool?limit=20");
  },

  claim(questionId: number): Promise<Question> {
    return request<Question>(`/hr/questions/${questionId}/claim`, { method: "POST" });
  },

  closeQuestion(questionId: number): Promise<Question> {
    return request<Question>(`/questions/${questionId}/close`, { method: "POST" });
  },

  publishQuestion(questionId: number): Promise<Question> {
    return request<Question>(`/questions/${questionId}/publish`, { method: "POST" });
  },

  posts(): Promise<PublicPost[]> {
    return request<PublicPost[]>("/posts");
  },
  listMyQuestions(scope?: string): Promise<PageResult<Question>> {
    const params = new URLSearchParams();
    if (scope) params.set("scope", scope);
    params.set("limit", "20");
    return request<PageResult<Question>>(`/questions/mine?${params.toString()}`);
  },

  listFavorites(targetType?: string): Promise<PageResult<Favorite>> {
    const params = new URLSearchParams();
    if (targetType) params.set("targetType", targetType);
    params.set("limit", "20");
    return request<PageResult<Favorite>>(`/me/favorites?${params.toString()}`);
  },

  getProfile(): Promise<Profile> {
    return request<Profile>("/me/profile");
  },

  updateProfile(payload: {
    school?: string | null;
    major?: string | null;
    grade?: string | null;
    degree?: string | null;
    graduationYear?: string | null;
    experiences: Experience[];
  }): Promise<Profile> {
    return request<Profile>("/me/profile", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  updateAvatar(avatarUrl: string): Promise<Profile> {
    return request<Profile>("/me/avatar", {
      method: "PUT",
      body: JSON.stringify({ avatarUrl }),
    });
  },
};
