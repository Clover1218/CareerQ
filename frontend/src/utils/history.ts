export interface ViewHistoryEntry {
  id: number;
  title: string;
  visitedAt: string;
}

const LEGACY_STORAGE_KEY = "careerq:view-history";
const MAX_ENTRIES = 50;

function storageKey(userId: number | null): string {
  // 旧共用记录无法判断作者，不能迁入当前学生或游客的历史。
  window.localStorage.removeItem(LEGACY_STORAGE_KEY);
  return `${LEGACY_STORAGE_KEY}:${userId == null ? "guest" : `user:${userId}`}`;
}

function isValid(entry: unknown): entry is ViewHistoryEntry {
  return (
    typeof entry === "object" &&
    entry !== null &&
    Number.isSafeInteger((entry as ViewHistoryEntry).id) && (entry as ViewHistoryEntry).id > 0 &&
    typeof (entry as ViewHistoryEntry).title === "string" &&
    typeof (entry as ViewHistoryEntry).visitedAt === "string"
  );
}

/** 读取本地浏览记录（后端接口暂未实现，先用 localStorage） */
export function getViewHistory(userId: number | null): ViewHistoryEntry[] {
  try {
    const raw = window.localStorage.getItem(storageKey(userId));
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(isValid);
  } catch {
    return [];
  }
}

/** 记录一次问题浏览：同一问题只保留最新一条，最多 50 条 */
export function addViewHistory(entry: { id: number; title: string }, userId: number | null): void {
  if (!entry.id) return;
  try {
    const next: ViewHistoryEntry = {
      id: entry.id,
      title: entry.title,
      visitedAt: new Date().toISOString(),
    };
    const rest = getViewHistory(userId).filter((item) => item.id !== entry.id);
    window.localStorage.setItem(
      storageKey(userId),
      JSON.stringify([next, ...rest].slice(0, MAX_ENTRIES)),
    );
  } catch {
    // localStorage 不可用（无痕模式等）时静默忽略
  }
}

/** 删除单条浏览记录 */
export function removeViewHistoryEntry(id: number, userId: number | null): void {
  try {
    const rest = getViewHistory(userId).filter((item) => item.id !== id);
    window.localStorage.setItem(storageKey(userId), JSON.stringify(rest));
  } catch {
    // ignore
  }
}

export function clearViewHistory(userId: number | null): void {
  try {
    window.localStorage.removeItem(storageKey(userId));
  } catch {
    // ignore
  }
}
