export const DISPLAY_STATUS_TEXT: Record<string, string> = {
  REVIEWING: "审核中",
  PUBLISHED: "已发布",
  REJECTED: "未通过",
  REMOVED: "已下架",
  WITHDRAWN: "已撤回",
};

export function displayStatusText(status?: string | null): string {
  if (!status) return "";
  const key = status.toUpperCase();
  return DISPLAY_STATUS_TEXT[key] ?? status;
}

const ADMIN_STATUS_TEXT: Record<string, string> = {
  REVIEWING: "待审核",
  ARBITRATING: "待仲裁",
  PUBLISHED: "已通过",
  REJECTED: "已拒绝",
  REMOVED: "已下架",
  WITHDRAWN: "已撤回",
};

export function adminStatusText(status?: string | null): string {
  if (!status) return "";
  const key = status.toUpperCase();
  return ADMIN_STATUS_TEXT[key] ?? status;
}

export const RISK_TEXT: Record<string, string> = {
  NORMAL: "普通",
  WATCH: "关注",
  HIGH: "高危",
  UNRATED: "未评级",
};

export function riskText(risk?: string | null): string {
  if (!risk) return "";
  const key = risk.toUpperCase();
  return RISK_TEXT[key] ?? risk;
}

export const TARGET_TYPE_TEXT: Record<string, string> = {
  question: "学生问题",
  answer: "HR 回答",
  followup: "学生追问",
};

export function targetTypeText(type?: string | null): string {
  if (!type) return "";
  return TARGET_TYPE_TEXT[type] ?? type;
}