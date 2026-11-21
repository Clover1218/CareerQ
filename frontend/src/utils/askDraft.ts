export interface AskDraft { title: string; body: string; tags: string[]; updatedAt: string }
const draftKey = (id: number) => `careerq:ask-draft:user:${id}`;
export const draftFingerprint = (draft: Pick<AskDraft, "title" | "body" | "tags">) => JSON.stringify([draft.title, draft.body, draft.tags]);
export interface RecoveredAskDraft { key: string; draft: AskDraft }

export function readAskDraft(id: number): AskDraft | null {
  try {
    const value = JSON.parse(localStorage.getItem(draftKey(id)) ?? "null");
    if (!value || typeof value.title !== "string" || typeof value.body !== "string" || !Array.isArray(value.tags)) return null;
    return { title: value.title.slice(0, 200), body: value.body.slice(0, 5000),
      tags: value.tags.filter((tag: unknown): tag is string => typeof tag === "string").slice(0, 5),
      updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : "" };
  } catch { return null; }
}
export function recoveredAskDrafts(id: number): RecoveredAskDraft[] {
  try {
    const prefix = `${draftKey(id)}:recovery:`;
    return Object.keys(localStorage).filter(key => key.startsWith(prefix)).flatMap(key => {
      try {
        const draft: AskDraft = JSON.parse(localStorage.getItem(key) ?? "null");
        return draft && typeof draft.title === "string" && typeof draft.body === "string" && Array.isArray(draft.tags) ? [{ key, draft }] : [];
      } catch { return []; }
    }).sort((a, b) => b.key.localeCompare(a.key));
  } catch { return []; }
}
export function preserveAskDraft(id: number, draft: Omit<AskDraft, "updatedAt">): boolean {
  if (!draft.title && !draft.body && !draft.tags.length) return true;
  try {
    if (!recoveredAskDrafts(id).some(item => draftFingerprint(item.draft) === draftFingerprint(draft)))
      localStorage.setItem(`${draftKey(id)}:recovery:${Date.now()}-${Math.random()}`, JSON.stringify({ ...draft, updatedAt: new Date().toISOString() }));
    return true;
  } catch { return false; }
}
export function saveAskDraft(id: number, draft: Omit<AskDraft, "updatedAt">, expectedFingerprint: string): boolean {
  try {
    const current = readAskDraft(id);
    // 另一标签页的新版本先另存，之后才能写入当前页面的编辑。
    if (current && draftFingerprint(current) !== expectedFingerprint && draftFingerprint(current) !== draftFingerprint(draft)
        && !preserveAskDraft(id, current)) return false;
    if (!draft.title && !draft.body && !draft.tags.length) localStorage.removeItem(draftKey(id));
    else localStorage.setItem(draftKey(id), JSON.stringify({ ...draft, updatedAt: new Date().toISOString() }));
    return true;
  } catch { return false; }
}
export function clearAskDraft(id: number, expectedFingerprint: string): boolean {
  try {
    const current = readAskDraft(id);
    // 当前页提交/清空不能顺带删除另一标签页较新的草稿。
    if (!current || draftFingerprint(current) === expectedFingerprint) localStorage.removeItem(draftKey(id));
    for (const item of recoveredAskDrafts(id)) if (draftFingerprint(item.draft) === expectedFingerprint) localStorage.removeItem(item.key);
    return true;
  } catch { return false; }
}
export function forgetRecoveredAskDraft(key: string, id: number): void {
  if (key.startsWith(`${draftKey(id)}:recovery:`)) localStorage.removeItem(key);
}
