/** 分页轨迹随 URL 保留，详情返回后恢复原页；只接受有限个正整数游标。 */
export function cursorPages(value: unknown): number[] {
  if (typeof value !== "string" || !value || value.length > 2000) return [];
  const pages = value.split(",").map(Number);
  return pages.length <= 100 && pages.every((id, index) => Number.isSafeInteger(id) && id > 0 && (!index || id < pages[index - 1])) ? pages : [];
}
