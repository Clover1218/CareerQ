import { ref } from "vue";
import { defineStore } from "pinia";
import { api, type StudentUnreadCounts } from "@/api";
import { useUserStore } from "@/stores/user";

export const useStudentNotificationStore = defineStore("student-notifications", () => {
  const user = useUserStore();
  const counts = ref<StudentUnreadCounts | null>(null);
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
    if (user.user?.role !== "student") { reset(); return; }
    if (inFlight && !force) return inFlight;
    const id = user.user.id;
    const current = ++sequence;
    inFlight = (async () => {
      try {
        const result = await api.studentUnreadCounts();
        if (current !== sequence || user.user?.id !== id || user.user.role !== "student") return;
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
    const result = await api.readStudentNotification(id);
    if (account === user.user?.id) await refresh(true);
    return result;
  }

  async function readAll(throughId: number) {
    const account = user.user?.id;
    await api.readAllStudentNotifications(throughId);
    if (account === user.user?.id) await refresh(true);
  }

  return { counts, error, reset, refresh, read, readAll };
});
