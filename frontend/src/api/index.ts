import { request, resetCsrfToken } from "./request";

export { ApiError, resetCsrfToken, request, type ApiEnvelope, type RequestOptions } from "./request";

export interface User {
  id: number;
  role: "student" | "hr" | "operator" | "admin";
  nickname: string;
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

export const api = {
  async sendCode(phone: string): Promise<void> {
    await request<void>("/auth/sms-code", { method: "POST", body: { phone } });
  },

  async register(payload: {
    phone: string;
    code: string;
    nickname?: string;
    school?: string;
    major?: string;
    grade?: string;
  }): Promise<void> {
    await request<{ user: User }>("/auth/register", { method: "POST", body: payload });
    // 注册成功即建立会话，旧 CSRF token 失效
    resetCsrfToken();
  },

  async login(phone: string, code: string): Promise<void> {
    await request<{ user: User }>("/auth/login", { method: "POST", body: { phone, code } });
    resetCsrfToken();
  },

  async logout(): Promise<void> {
    await request<void>("/auth/logout", { method: "POST" });
    resetCsrfToken();
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
    return request<Question>("/questions", { method: "POST", body: payload });
  },

  answer(questionId: number, body: string, lengthType = "detailed"): Promise<Answer> {
    return request<Answer>(`/questions/${questionId}/answers`, {
      method: "POST",
      body: { body, lengthType },
    });
  },

  followup(questionId: number, body: string): Promise<Followup> {
    return request<Followup>(`/questions/${questionId}/followups`, { method: "POST", body: { body } });
  },

  interact(targetType: string, targetId: number, type: string): Promise<void> {
    return request<void>("/interactions", {
      method: "POST",
      body: { targetType, targetId, type },
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
};
