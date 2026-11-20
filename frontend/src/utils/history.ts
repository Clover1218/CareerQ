export interface ViewHistoryEntry {
  id: number;
  title: string;
  visitedAt: string;
}

const STORAGE_KEY = "careerq:view-history";
const MAX_ENTRIES = 50;

function isValid(entry: unknown): entry is ViewHistoryEntry {
  return (
    typeof entry === "object" &&
    entry !== null &&
    typeof (entry as ViewHistoryEntry).id === "number" &&
    typeof (entry as ViewHistoryEntry).title === "string"
  );
}

/** 读取本地浏览记录（后端接口暂未实现，先用 localStorage） */
export function getViewHistory(): ViewHistoryEntry[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(isValid);
  } catch {
    return [];
  }
}

/** 记录一次问题浏览：同一问题只保留最新一条，最多 50 条 */
export function addViewHistory(entry: { id: number; title: string }): void {
  if (!entry.id) return;
  try {
    const next: ViewHistoryEntry = {
      id: entry.id,
      title: entry.title,
      visitedAt: new Date().toISOString(),
    };
    const rest = getViewHistory().filter((item) => item.id !== entry.id);
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify([next, ...rest].slice(0, MAX_ENTRIES)),
    );
  } catch {
    // localStorage 不可用（无痕模式等）时静默忽略
  }
}

/** 删除单条浏览记录 */
export function removeViewHistoryEntry(id: number): void {
  try {
    const rest = getViewHistory().filter((item) => item.id !== id);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(rest));
  } catch {
    // ignore
  }
}

export function clearViewHistory(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}
