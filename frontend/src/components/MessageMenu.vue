<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import { useHrNotificationStore } from "@/stores/hrNotifications";
import { useStudentNotificationStore } from "@/stores/studentNotifications";

const router = useRouter();
const route = useRoute();
const user = useUserStore();
const notifications = useHrNotificationStore();
const studentNotifications = useStudentNotificationStore();
const isHr = computed(() => user.user?.role === "hr");
const isStudent = computed(() => user.user?.role === "student");

// 两类消息从各自的收件人接口读取，不共用未读状态。
interface MessageCategory {
  key: string;
  label: string;
  count?: number;
}

const studentCategories = computed<MessageCategory[]>(() => [
  { key: "reply", label: "回复我的", count: studentNotifications.counts?.reply },
  { key: "like", label: "收到的赞", count: studentNotifications.counts?.like },
  { key: "system", label: "审核与系统通知", count: studentNotifications.counts?.system },
  { key: "all", label: "查看全部消息" },
]);

const categories = computed<MessageCategory[]>(() => isHr.value ? [
  { key: "followup", label: "学生追问", count: notifications.counts?.followup },
  { key: "system", label: "审核与系统通知", count: notifications.counts?.system },
  { key: "all", label: "查看全部消息" },
] : isStudent.value ? studentCategories.value : []);
const unreadTotal = computed(() => isHr.value ? notifications.counts?.total ?? 0
  : isStudent.value ? studentNotifications.counts?.total ?? 0 : 0);
const notificationError = computed(() => isHr.value ? notifications.error : isStudent.value ? studentNotifications.error : "");

function refreshUnread() {
  if (document.visibilityState !== "visible") return;
  if (isHr.value) void notifications.refresh();
  else if (isStudent.value) void studentNotifications.refresh();
}
function retryUnread() { if (isHr.value) void notifications.refresh(true); else if (isStudent.value) void studentNotifications.refresh(true); }
watch([() => user.user?.id, () => user.user?.role], () => { notifications.reset(); studentNotifications.reset(); refreshUnread(); }, { immediate: true });
watch(() => route.fullPath, refreshUnread);
let refreshTimer: ReturnType<typeof setInterval> | null = null;
onMounted(() => {
  refreshTimer = setInterval(refreshUnread, 30000);
  window.addEventListener("focus", refreshUnread);
  document.addEventListener("visibilitychange", refreshUnread);
});
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer);
  window.removeEventListener("focus", refreshUnread);
  document.removeEventListener("visibilitychange", refreshUnread);
});

function open(category: MessageCategory) {
  if (isHr.value) {
    void router.push({ path: "/hr/messages", query: category.key === "all" ? {} : { category: category.key } });
    return;
  }
  if (isStudent.value) void router.push({ path: "/messages", query: category.key === "all" ? {} : { category: category.key } });
}
</script>

<template>
  <div v-if="isHr || isStudent" class="msg-menu" @mouseenter="refreshUnread" @focusin="refreshUnread">
    <button class="msg-trigger" type="button" aria-label="消息" @click="open({ key: 'all', label: '查看全部消息' })">
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.7 21a2 2 0 0 1-3.4 0" />
      </svg>
      <span>消息</span>
      <span v-if="unreadTotal > 0" class="msg-badge">{{ unreadTotal > 99 ? '99+' : unreadTotal }}</span>
    </button>

    <div class="msg-panel" role="menu">
      <button v-if="notificationError" class="msg-item" type="button" @click="retryUnread">消息加载失败，点击重试</button>
      <button
        v-for="item in categories"
        :key="item.key"
        class="msg-item"
        type="button"
        role="menuitem"
        @click="open(item)"
      >
        <span>{{ item.label }}</span>
        <span v-if="item.count" class="msg-item-badge">{{ item.count }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.msg-menu {
  position: relative;
  display: flex;
  align-items: center;
}

/* 透明桥接区：填满「消息」与面板之间的 8px 间隙，鼠标移入面板时不会误触离开 */
.msg-menu::after {
  content: "";
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  height: 10px;
}

.msg-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: none;
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--color-text-regular);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.msg-trigger:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.msg-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ff383c;
  color: #fff;
  font-size: 11px;
  line-height: 1;
  font-weight: 600;
}

/* 悬停/聚焦时展开的白色面板（参考设计稿） */
.msg-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 40;
  width: 176px;
  padding: 6px 0;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-default);
  border-radius: 8px;
  box-shadow: 0 4px 16px -8px rgba(0, 0, 0, 0.25);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-4px);
  /* 收起时延迟 200ms，给鼠标留出移动到面板的时间 */
  transition:
    opacity 0.15s ease 0.2s,
    transform 0.15s ease 0.2s,
    visibility 0.15s ease 0.2s;
  pointer-events: none;
}

.msg-menu:hover .msg-panel,
.msg-menu:focus-within .msg-panel {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
  pointer-events: auto;
  /* 展开时立即响应，不加延迟 */
  transition-delay: 0s;
}

.msg-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  background: none;
  padding: 10px 16px;
  color: var(--color-text-regular);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.msg-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.msg-item-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ff383c;
  color: #fff;
  font-size: 11px;
  line-height: 1;
  font-weight: 600;
}
</style>
