<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();

// 说明：消息后端接口尚未实现，这里先按设计稿放静态示例数据；
// 后续接 /api/v1/messages 时替换成接口返回即可。
interface MessageCategory {
  key: string;
  label: string;
  count?: number;
}

const categories = ref<MessageCategory[]>([
  { key: "reply", label: "回复我的", count: 2 },
  { key: "like", label: "收到的赞" },
  { key: "system", label: "系统消息" },
]);

const unreadTotal = ref(
  categories.value.reduce((sum, item) => sum + (item.count ?? 0), 0),
);

function open(category: MessageCategory) {
  // 消息页尚未开发：先跳转到「我的问答」，后续换成 /messages?tab=xxx
  router.push({ path: "/me", query: { tab: category.key } });
}
</script>

<template>
  <div class="msg-menu">
    <button class="msg-trigger" type="button" aria-label="消息">
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
      <span v-if="unreadTotal > 0" class="msg-badge">{{ unreadTotal }}</span>
    </button>

    <div class="msg-panel" role="menu">
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
  transition:
    opacity 0.15s ease,
    transform 0.15s ease,
    visibility 0.15s ease;
  pointer-events: none;
}

.msg-menu:hover .msg-panel,
.msg-menu:focus-within .msg-panel {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
  pointer-events: auto;
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
