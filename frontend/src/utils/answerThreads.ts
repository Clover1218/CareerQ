import type { QuestionDetail, ThreadItem } from "@/api";

export interface AnswerThread {
  key: string;
  rootAnswerId: number;
  hrId: number;
  nickname: string;
  company?: string | null;
  position?: string | null;
  rootAnswer: ThreadItem;
  messages: ThreadItem[];
}

export function detailThreadItems(detail: QuestionDetail): ThreadItem[] {
  return [
    ...detail.answers.map((answer): ThreadItem => ({
      ...answer,
      key: `answer-${answer.id}`,
      kind: answer.followupId == null ? "hr_answer" : "followup_answer",
      hrId: answer.author.id,
      rootAnswerId: answer.followupId == null ? answer.id
        : detail.followups.find((followup) => followup.id === answer.followupId)?.rootAnswerId,
      hr: true,
    })),
    ...detail.followups.map((followup): ThreadItem => ({
      ...followup,
      key: `followup-${followup.id}`,
      kind: "followup",
      hrId: followup.hrId,
      rootAnswerId: followup.rootAnswerId,
      followupId: null,
      likeCount: 0,
      liked: false,
    })),
  ];
}

/** 每条主回答一层，追问必须明确关联该回答。 */
export function buildAnswerThreads(items: ThreadItem[]): AnswerThread[] {
  const threads = new Map<string, AnswerThread>();
  const followups = new Map(items.filter((item) => item.kind === "followup").map((item) => [item.id, item]));
  for (const root of items.filter((item) => item.kind === "hr_answer")) {
    const key = `answer-${root.id}`;
    threads.set(key, {
      key, rootAnswerId: root.id, hrId: root.author.id,
      nickname: root.author.nickname, company: root.author.company, position: root.author.position,
      rootAnswer: root, messages: [],
    });
  }
  for (const item of items) {
    if (item.kind === "hr_answer") continue;
    const followup = item.kind === "followup" ? item : followups.get(item.followupId ?? -1);
    if (followup?.rootAnswerId == null) continue;
    const target = threads.get(`answer-${followup.rootAnswerId}`);
    if (!target || followup.hrId !== target.hrId) continue;
    target.messages.push(item);
  }
  const compare = (a: ThreadItem, b: ThreadItem) => a.createdAt.localeCompare(b.createdAt)
    || (a.kind === b.kind ? a.id - b.id : a.kind === "followup" ? -1 : 1);
  for (const thread of threads.values()) thread.messages.sort(compare);
  return [...threads.values()].sort((a, b) => compare(a.rootAnswer, b.rootAnswer));
}
