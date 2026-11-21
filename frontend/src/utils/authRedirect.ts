/** 只保留站内业务页面，避免无效返回值或登录/注册之间循环。 */
export function authRedirect(value: unknown): string {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) return "/";
  const path = value.split(/[?#]/, 1)[0];
  return path === "/login" || path === "/register" ? "/" : value;
}
