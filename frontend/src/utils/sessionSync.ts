export const SESSION_SIGNAL_KEY = "careerq:session-change";
export const SESSION_REFRESH_EVENT = "careerq:refresh-session";

/** 仅广播会话变化信号，不存储账号资料或令牌。 */
export function announceSessionChange() {
  try { localStorage.setItem(SESSION_SIGNAL_KEY, `${Date.now()}-${Math.random()}`); }
  catch { /* 本地存储不可用时，焦点与提交校验仍可同步身份。 */ }
  window.dispatchEvent(new Event(SESSION_REFRESH_EVENT));
}
