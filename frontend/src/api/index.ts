import { request, resetCsrfToken } from "./request";
import { announceSessionChange } from "@/utils/sessionSync";

export { ApiError, resetCsrfToken, request, type ApiEnvelope, type RequestOptions } from "./request";

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
  company?: string | null;
  position?: string | null;
}

export interface Question {
  id: number;
  scope: "public";
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

export interface HrAnswerSummary {
  id: number;
  followupId?: number | null;
  body: string;
  lengthType: string;
  status: string;
  removalSource?: AnswerRemovalSource | null;
  createdAt: string;
}

export interface HrAnsweredQuestion {
  question: Question;
  myAnswer: HrAnswerSummary;
  followups: {
    pendingCount: number;
    reviewingCount: number;
    firstPendingFollowupId?: number | null;
    latestPendingAt?: string | null;
  };
}

export interface HrAnsweredQuestionPage extends PageResult<HrAnsweredQuestion> {
  nextCursorAt?: string | null;
  pendingQuestionCount: number;
  pendingFollowupCount: number;
}

export interface HrAnsweredPublicQuestion {
  question: Question;
  hasPendingAnswer: boolean;
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
  /** 推荐理由：命中的正向偏好标签；时间序列表为空数组 */
  matchedTags: string[];
  /** 当前会话的互动状态；旧摘要或本人列表可能不提供，由卡片读取内容接口。 */
  viewer?: ViewerState;
}

/** 推荐流分页：游标是不透明字符串，同一快照内翻页 */
export interface FeedPage {
  items: QuestionFeedItem[];
  nextCursor: string | null;
}

export type FeedSort = "recommend" | "latest" | "hot";
export type FeedPeriod = "all" | "day" | "week" | "month";

/** 公开列表筛选条件；tags 传标签 slug，from/to 传 YYYY-MM-DD */
export interface FeedQuery {
  q?: string;
  tags?: string[];
  period?: FeedPeriod;
  from?: string | null;
  to?: string | null;
  sort?: FeedSort;
}

/** 筛选面板提交的完整条件，tags 为标签名称 */
export interface FeedFilterValue {
  q: string;
  tags: string[];
  period: FeedPeriod;
  from: string | null;
  to: string | null;
  sort: FeedSort;
}

export interface ViewerState {
  liked: boolean;
  usefuled: boolean;
  favorited: boolean;
  disliked: boolean;
}

export interface QuestionContent {
  question: Question;
  viewer: ViewerState;
}

export type AnswerRemovalSource = "author" | "operator" | "unknown";

export interface ThreadItem {
  key: string;
  kind: "hr_answer" | "followup_answer" | "followup";
  id: number;
  followupId?: number | null;
  /** 追问的目标 HR；回答类消息为空 */
  hrId?: number | null;
  rootAnswerId?: number | null;
  body: string;
  author: UserBrief;
  hr: boolean;
  status: string;
  /** 仅回答和追问回复使用，学生追问为空。 */
  removalSource?: AnswerRemovalSource | null;
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
  followupId?: number | null;
  body: string;
  lengthType: string;
  status: string;
  removalSource?: AnswerRemovalSource | null;
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
  avatarUrl?: string | null;
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

export interface HrProfile {
  id: number;
  nickname: string;
  avatarUrl?: string | null;
  companyDisplay: string;
  industry?: string | null;
  position?: string | null;
  bio?: string | null;
  specialties: string[];
  verifyStatus: string;
}

export type HrProfilePatch = Partial<Pick<HrProfile, "nickname" | "companyDisplay" | "industry" | "position" | "bio" | "specialties">>;
export interface HrContributionStats {
  rootAnswerCount: number;
  followupReplyCount: number;
  likeCount: number;
  usefulCount: number;
}
export type HrAnswerKind = "all" | "root" | "reply";
export type HrAnswerStatus = "all" | "published" | "reviewing" | "rejected" | "withdrawn" | "downlisted" | "removed";
export interface HrAnswerRecord {
  id: number;
  questionId: number;
  questionTitle: string;
  canViewQuestion: boolean;
  rootAnswerId: number;
  followupId?: number | null;
  body: string;
  status: string;
  removalSource?: AnswerRemovalSource | null;
  likeCount: number;
  usefulCount: number;
  createdAt: string;
}

export type HrMessageCategory = "all" | "followup" | "system";
export interface HrNotification {
  id: number;
  type: "followup_published" | "answer_approved" | "answer_rejected" | "answer_removed";
  category: "followup" | "system";
  title: string;
  message: string;
  questionTitle?: string | null;
  questionId: number;
  answerId?: number | null;
  followupId?: number | null;
  destination: "question" | "answer_record" | "unavailable";
  createdAt: string;
  readAt?: string | null;
}
export interface HrUnreadCounts { total: number; followup: number; system: number; snapshotId: number }
export interface HrNotificationPage extends PageResult<HrNotification> { snapshotId: number }

export type HrPostStatus = "draft" | "pending_human" | "rejected" | "withdrawn" | "published" | "removed";
export interface HrPost {
  id: number; title: string; body: string; tags: string[]; status: HrPostStatus; version: number;
  createdAt: string; updatedAt: string; submittedAt?: string | null;
}
export interface HrPostSummary extends Omit<HrPost, "body"> { excerpt: string }
export interface HrPostContent { title: string; body: string; tags: string[] }

export type StudentMessageCategory = "all" | "reply" | "like" | "system";
export interface StudentNotification {
  id: number; type: string; category: "reply" | "like" | "system";
  title: string; message: string; questionTitle?: string | null; questionId: number;
  answerId?: number | null; followupId?: number | null;
  destination: "question" | "unavailable"; createdAt: string; readAt?: string | null;
}
export interface StudentUnreadCounts { total: number; reply: number; like: number; system: number; snapshotId: number }
export interface StudentNotificationPage extends PageResult<StudentNotification> { snapshotId: number }
export interface Favorite {
  targetType: "question" | "answer"; targetId: number; questionId: number; followupId?: number | null;
  title: string; excerpt: string; createdAt: string;
}

export interface Followup {
  id: number;
  questionId: number;
  hrId: number;
  rootAnswerId: number;
  senderRole: string;
  body: string;
  status: string;
  createdAt: string;
  author: UserBrief;
  hr: boolean;
}

export interface TagCategory {
  name: string;
  tagCount: number;
}

export interface TagItem {
  id: number;
  name: string;
  slug: string;
  category?: string | null;
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
  hrPosts(ownerId: number, status = "all", cursor?: number | null): Promise<PageResult<HrPostSummary>> {
    const params = new URLSearchParams({ status, limit: "5" });
    if (cursor != null) params.set("cursor", String(cursor));
    return request(`/hr/posts?${params}`, { headers: { "X-CareerQ-Hr-Id": String(ownerId) } });
  },
  hrPost(ownerId: number, id: number): Promise<HrPost> {
    return request(`/hr/posts/${id}`, { headers: { "X-CareerQ-Hr-Id": String(ownerId) } });
  },
  createHrPostDraft(ownerId: number, clientKey: string, content: HrPostContent): Promise<HrPost> {
    return request("/hr/posts/drafts", { method: "POST", headers: { "X-CareerQ-Hr-Id": String(ownerId) }, body: { clientKey, ...content } });
  },
  saveHrPostDraft(ownerId: number, id: number, version: number, content: HrPostContent): Promise<HrPost> {
    return request(`/hr/posts/${id}/draft`, { method: "PUT", headers: { "X-CareerQ-Hr-Id": String(ownerId) }, body: { version, ...content } });
  },
  submitHrPost(ownerId: number, id: number, version: number): Promise<HrPost> {
    return request(`/hr/posts/${id}/submit`, { method: "POST", headers: { "X-CareerQ-Hr-Id": String(ownerId) }, body: { version } });
  },
  withdrawHrPost(ownerId: number, id: number, version: number): Promise<HrPost> {
    return request(`/hr/posts/${id}/withdraw`, { method: "POST", headers: { "X-CareerQ-Hr-Id": String(ownerId) }, body: { version } });
  },
  deleteHrPostDraft(ownerId: number, id: number, version: number): Promise<void> {
    return request(`/hr/posts/${id}/draft`, { method: "DELETE", headers: { "X-CareerQ-Hr-Id": String(ownerId) }, body: { version } });
  },
  studentNotifications(category: StudentMessageCategory = "all", unreadOnly = false, cursor?: number | null): Promise<StudentNotificationPage> {
    const params = new URLSearchParams({ category, unreadOnly: String(unreadOnly), limit: "5" });
    if (cursor != null) params.set("cursor", String(cursor));
    return request<StudentNotificationPage>(`/student/notifications?${params}`);
  },
  studentUnreadCounts(): Promise<StudentUnreadCounts> { return request("/student/notifications/unread-count"); },
  readStudentNotification(id: number): Promise<StudentNotification> {
    return request(`/student/notifications/${id}/read`, { method: "POST" });
  },
  readAllStudentNotifications(throughId: number): Promise<void> {
    return request("/student/notifications/read-all", { method: "POST", body: { throughId } });
  },
  myFavorites(cursor?: number | null): Promise<PageResult<Favorite>> {
    const params = new URLSearchParams({ limit: "5" });
    if (cursor != null) params.set("cursor", String(cursor));
    return request(`/me/favorites?${params}`);
  },
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
    announceSessionChange();
  },

  async login(phone: string, code: string): Promise<void> {
    await request<{ user: User }>("/auth/login", { method: "POST", body: { phone, code } });
    resetCsrfToken();
    announceSessionChange();
  },

  async logout(): Promise<void> {
    await request<void>("/auth/logout", { method: "POST" });
    resetCsrfToken();
    announceSessionChange();
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

  hrAnsweredQuestions(
    cursor?: number | null,
    limit = 20,
    options: { pendingFollowupsOnly?: boolean; cursorAt?: string | null } = {},
  ): Promise<HrAnsweredQuestionPage> {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor != null) params.set("cursor", String(cursor));
    if (options.pendingFollowupsOnly) {
      params.set("pendingFollowupsOnly", "true");
      if (cursor != null && options.cursorAt) params.set("cursorAt", options.cursorAt);
    }
    return request<HrAnsweredQuestionPage>(`/hr/answered-questions?${params.toString()}`);
  },

  hrAnsweredPublicQuestions(cursor?: number | null, limit = 20): Promise<PageResult<HrAnsweredPublicQuestion>> {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor != null) params.set("cursor", String(cursor));
    return request<PageResult<HrAnsweredPublicQuestion>>(`/hr/answered-public-questions?${params.toString()}`);
  },

  hrQuestionPool(cursor?: number | null, limit = 20): Promise<PageResult<Question>> {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor != null) params.set("cursor", String(cursor));
    return request<PageResult<Question>>(`/hr/question-pool?${params.toString()}`);
  },

  getProfile(): Promise<Profile> {
    return request<Profile>("/me/profile");
  },

  hrProfile(): Promise<HrProfile> {
    return request<HrProfile>("/hr/profile");
  },

  patchHrProfile(payload: HrProfilePatch): Promise<HrProfile> {
    return request<HrProfile>("/hr/profile", { method: "PATCH", body: payload });
  },

  hrStats(): Promise<HrContributionStats> {
    return request<HrContributionStats>("/hr/stats");
  },

  hrAnswers(kind: HrAnswerKind = "all", status: HrAnswerStatus = "all", cursor?: number | null, limit = 5): Promise<PageResult<HrAnswerRecord>> {
    const params = new URLSearchParams({ kind, status, limit: String(limit) });
    if (cursor != null) params.set("cursor", String(cursor));
    return request<PageResult<HrAnswerRecord>>(`/hr/answers?${params}`);
  },

  hrAnswer(id: number): Promise<HrAnswerRecord> {
    return request<HrAnswerRecord>(`/hr/answers/${id}`);
  },

  hrNotifications(category: HrMessageCategory = "all", unreadOnly = false, cursor?: number | null, limit = 5): Promise<HrNotificationPage> {
    const params = new URLSearchParams({ category, unreadOnly: String(unreadOnly), limit: String(limit) });
    if (cursor != null) params.set("cursor", String(cursor));
    return request<HrNotificationPage>(`/hr/notifications?${params}`);
  },

  hrUnreadCounts(): Promise<HrUnreadCounts> {
    return request<HrUnreadCounts>("/hr/notifications/unread-count");
  },

  readHrNotification(id: number): Promise<HrNotification> {
    return request<HrNotification>(`/hr/notifications/${id}/read`, { method: "POST" });
  },

  readAllHrNotifications(throughId: number): Promise<void> {
    return request<void>("/hr/notifications/read-all", { method: "POST", body: { throughId } });
  },

  patchProfile(payload: ProfilePatch): Promise<Profile> {
    return request<Profile>("/me/profile", { method: "PATCH", body: payload });
  },

  uploadAvatar(file: File): Promise<Profile> {
    const form = new FormData();
    form.append("file", file);
    return request<Profile>("/me/avatar", { method: "POST", rawBody: form });
  },

  listQuestions(tag?: string, cursor?: number | null, limit = 20): Promise<PageResult<QuestionFeedItem>> {    const params = new URLSearchParams();
    if (tag) params.set("tag", tag);
    if (cursor != null) params.set("cursor", String(cursor));
    params.set("limit", String(limit));
    return request<PageResult<QuestionFeedItem>>(`/questions?${params.toString()}`);
  },

  feedQuestions(query: FeedQuery = {}, cursor?: string | null, limit = 20): Promise<FeedPage> {
    const params = new URLSearchParams();
    if (query.q) params.set("q", query.q);
    if (query.tags?.length) params.set("tags", query.tags.join(","));
    if (query.period && query.period !== "all") params.set("period", query.period);
    if (query.from) params.set("from", query.from);
    if (query.to) params.set("to", query.to);
    if (query.sort) params.set("sort", query.sort);
    if (cursor) params.set("cursor", cursor);
    params.set("limit", String(limit));
    return request<FeedPage>(`/feed/questions?${params.toString()}`);
  },

  tagCategories(): Promise<TagCategory[]> {
    return request<TagCategory[]>("/tags/categories");
  },

  listTags(params: { category?: string; keyword?: string; limit?: number } = {}): Promise<TagItem[]> {
    const query = new URLSearchParams();
    if (params.category) query.set("category", params.category);
    if (params.keyword) query.set("keyword", params.keyword);
    if (params.limit) query.set("limit", String(params.limit));
    const suffix = query.toString();
    return request<TagItem[]>(suffix ? `/tags?${suffix}` : "/tags");
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
    scope: "public";
  }): Promise<Question> {
    return request<Question>("/questions", { method: "POST", body: payload });
  },

  answer(questionId: number, body: string, lengthType = "detailed"): Promise<Answer> {
    return request<Answer>(`/questions/${questionId}/answers`, {
      method: "POST",
      body: { body, lengthType },
    });
  },

  answerFollowup(questionId: number, followupId: number, body: string, lengthType = "detailed"): Promise<Answer> {
    return request<Answer>(`/questions/${questionId}/followups/${followupId}/answers`, {
      method: "POST",
      body: { body, lengthType },
    });
  },

  followup(questionId: number, body: string, hrId: number, rootAnswerId: number): Promise<Followup> {
    return request<Followup>(`/questions/${questionId}/followups`, { method: "POST", body: { body, hrId, rootAnswerId } });
  },
  withdrawAnswer(answerId: number): Promise<Answer> {
    return request<Answer>(`/answers/${answerId}/withdraw`, { method: "POST" });
  },

  withdrawFollowup(questionId: number, followupId: number): Promise<Followup> {
    return request<Followup>(`/questions/${questionId}/followups/${followupId}/withdraw`, {
      method: "POST",
    });
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

  report(targetType: "question" | "answer", targetId: number, reason = "inappropriate"): Promise<void> {
    return request<void>("/reports", {
      method: "POST",
      body: { targetType, targetId, reason },
    });
  },

  withdrawQuestion(questionId: number): Promise<Question> {
    return request<Question>(`/questions/${questionId}/withdraw`, { method: "POST" });
  },
};
