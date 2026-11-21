import type { Answer } from "@/api";

type AnswerState = Pick<Answer, "status" | "removalSource">;

export function answerStatusLabel(answer: AnswerState): string {
  if (answer.status === "removed") {
    if (answer.removalSource === "author") return "作者已撤销";
    if (answer.removalSource === "operator") return "运营已下架";
    return "已移除";
  }
  const labels: Record<string, string> = {
    published: "已发布", pending_machine: "审核中", pending_human: "审核中", rejected: "未通过",
  };
  return labels[answer.status] ?? answer.status;
}

export function removedAnswerText(answer: AnswerState, subject = "原回答"): string {
  if (answer.removalSource === "author") return `${subject}已由作者撤销。`;
  if (answer.removalSource === "operator") return `${subject}已被运营下架。`;
  return `${subject}已移除。`;
}
