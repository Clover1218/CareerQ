import { ref } from "vue";
import { defineStore } from "pinia";
import { api, type HrUnreadCounts } from "@/api";
import { useUserStore } from "@/stores/user";

export const useHrNotificationStore = defineStore("hr-notifications", () => {
  const user = useUserStore();
  const counts = ref<HrUnreadCounts | null>(null);
  const error = ref("");
  let sequence = 0;
  let inFlight: Promise<void> | null = null;

  function reset() {
    ++sequence;
    counts.value = null;
    error.value = "";
    inFlight = null;
  }

  async function refresh(force = false): Promise<void> {
    if (user.user?.role !== "hr") { reset(); return; }
    if (inFlight && !force) return inFlight;
    const id = user.user.id;
    const current = ++sequence;
    inFlight = (async () => {
      try {
        const result = await api.hrUnreadCounts();
        if (current !== sequence || user.user?.id !== id || user.user.role !== "hr") return;
        counts.value = result;
        error.value = "";
      } catch (cause) {
        if (current !== sequence) return;
        counts.value = null;
        error.value = cause instanceof Error ? cause.message : "未读消息暂时无法读取";
      } finally {
        if (current === sequence) inFlight = null;
      }
    })();
    return inFlight;
  }

  async function read(id: number) {
    const account = user.user?.id;
    const result = await api.readHrNotification(id);
    if (account === user.user?.id) await refresh(true);
    return result;
  }

  async function readAll(throughId: number) {
    const account = user.user?.id;
    await api.readAllHrNotifications(throughId);
    if (account === user.user?.id) await refresh(true);
  }

  return { counts, error, reset, refresh, read, readAll };
});
