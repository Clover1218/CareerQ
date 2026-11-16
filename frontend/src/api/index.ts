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
  followupCount: number;
  usefulCount: number;
  likeCount: number;
  favoriteCount: number;
  firstAnswerAt?: string | null;
}

/** 公开 feed 卡片初始态：只有标题和摘要，不含作者与正文 */
export interface QuestionFeedItem {
  id: number;
  title: string;
  excerpt: string;
  tags: string[];
  answerCount: number;
  followupCount: number;
  replyCount: number;
  likeCount: number;
  favoriteCount: number;
  createdAt: string;
}

export interface ViewerState {
  liked: boolean;
  usefuled: boolean;
  favorited: boolean;
}

export interface QuestionContent {
  question: Question;
  viewer: ViewerState;
}

export interface ThreadItem {
  key: string;
  kind: "hr_answer" | "followup";
  id: number;
  body: string;
  author: UserBrief;
  hr: boolean;
  status: string;
  likeCount: number;
  liked: boolean;
  createdAt: string;
}

export interface Thread {
  items: ThreadItem[];
  viewer: ViewerState;
  answerCount: number;
  followupCount: number;
  replyCount: number;
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
  liked: boolean;
  usefuled: boolean;
  createdAt: string;
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
  school?: string | null;
  major?: string | null;
  grade?: string | null;
  degree?: string | null;
  graduationYear?: string | null;
  experiences: Experience[];
}

export interface ProfilePatch {
  nickname?: string;
  school?: string;
  major?: string;
  grade?: string;
}

export interface MeStats {
  questionCount: number;
  receivedAnswerCount: number;
  authoredAnswerCount: number;
  likeCount: number;
}

export interface Followup {
  id: number;
  questionId: number;
  senderRole: string;
  body: string;
  status: string;
  createdAt: string;
  author: UserBrief;
  hr: boolean;
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
  viewer: ViewerState;
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

  myStats(): Promise<MeStats> {
    return request<MeStats>("/me/stats");
  },

  myQuestions(cursor?: number | null): Promise<PageResult<Question>> {
    const params = new URLSearchParams();
    if (cursor) params.set("cursor", String(cursor));
    params.set("limit", "20");
    return request<PageResult<Question>>(`/me/questions?${params.toString()}`);
  },

  getProfile(): Promise<Profile> {
    return request<Profile>("/me/profile");
  },

  patchProfile(payload: ProfilePatch): Promise<Profile> {
    return request<Profile>("/me/profile", { method: "PATCH", body: payload });
  },

  listQuestions(tag?: string, cursor?: number | null): Promise<PageResult<QuestionFeedItem>> {
    const params = new URLSearchParams();
    if (tag) params.set("tag", tag);
    if (cursor) params.set("cursor", String(cursor));
    params.set("limit", "20");
    return request<PageResult<QuestionFeedItem>>(`/questions?${params.toString()}`);
  },

  getQuestion(id: number): Promise<QuestionDetail> {
    return request<QuestionDetail>(`/questions/${id}`);
  },

  getQuestionContent(id: number): Promise<QuestionContent> {
    return request<QuestionContent>(`/questions/${id}/content`);
  },

  getQuestionThread(id: number): Promise<Thread> {
    return request<Thread>(`/questions/${id}/thread`);
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

  uninteract(targetType: string, targetId: number, type: string): Promise<void> {
    return request<void>("/interactions", {
      method: "DELETE",
      body: { targetType, targetId, type },
    });
  },

  report(targetType: "question" | "answer" | "post" | "public_post", targetId: number, reason = "inappropriate"): Promise<void> {
    return request<void>("/reports", {
      method: "POST",
      body: { targetType, targetId, reason },
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
