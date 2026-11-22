import type { HrPostContent, HrPostStatus } from "@/api";
export const hrPostLabels: Record<HrPostStatus, string> = {
  draft: "草稿", pending_human: "待审核", rejected: "审核未通过", withdrawn: "已撤回", published: "已发布", removed: "运营已下架",
};
export const editableHrPost = (status: HrPostStatus) => ["draft", "rejected", "withdrawn"].includes(status);
export const postFingerprint = (content: HrPostContent) => JSON.stringify([content.title, content.body, [...content.tags].sort()]);
export interface LocalHrPostDraft extends HrPostContent {
  clientKey: string; postId: number | null; baseVersion: number | null; savedFingerprint: string; updatedAt: string;
}
const prefix = (ownerId: number) => `careerq:hr-post-draft:user:${ownerId}:`;
const storageKey = (ownerId: number, key: string) => `${prefix(ownerId)}${key}`;
export function readLocalHrPosts(ownerId: number): { key: string; draft: LocalHrPostDraft }[] {
  try {
    return Object.keys(localStorage).filter(key => key.startsWith(prefix(ownerId))).flatMap(key => {
      try {
        const draft: LocalHrPostDraft = JSON.parse(localStorage.getItem(key) ?? "null");
        return draft && typeof draft.title === "string" && typeof draft.body === "string" && Array.isArray(draft.tags)
          && typeof draft.clientKey === "string" && typeof draft.savedFingerprint === "string" ? [{ key: key.slice(prefix(ownerId).length), draft }] : [];
      } catch { return []; }
    }).sort((a, b) => b.draft.updatedAt.localeCompare(a.draft.updatedAt));
  } catch { return []; }
}
export function localHrPost(ownerId: number, key: string) {
  return readLocalHrPosts(ownerId).find(item => item.key === key)?.draft ?? null;
}
export function backupLocalHrPost(ownerId: number, draft: LocalHrPostDraft): boolean {
  if (!draft.title && !draft.body && !draft.tags.length) return true;
  try {
    localStorage.setItem(storageKey(ownerId, `recovery-${crypto.randomUUID()}`), JSON.stringify({ ...draft, updatedAt: new Date().toISOString() }));
    return true;
  } catch { return false; }
}
export function saveLocalHrPost(ownerId: number, key: string, draft: LocalHrPostDraft, expected: string): boolean {
  try {
    const previous = localHrPost(ownerId, key);
    if (previous && postFingerprint(previous) !== expected && postFingerprint(previous) !== postFingerprint(draft)
        && !backupLocalHrPost(ownerId, previous)) return false;
    localStorage.setItem(storageKey(ownerId, key), JSON.stringify({ ...draft, updatedAt: new Date().toISOString() }));
    return true;
  } catch { return false; }
}
export function removeLocalHrPost(ownerId: number, key: string, expected: string): boolean {
  try {
    const current = localHrPost(ownerId, key);
    if (!current || postFingerprint(current) === expected) localStorage.removeItem(storageKey(ownerId, key));
    return true;
  } catch { return false; }
}
