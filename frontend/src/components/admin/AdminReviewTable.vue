<script setup lang="ts">
import { computed } from "vue";
import type { GlobalThemeOverrides } from "naive-ui";
import type { AdminDecision, AdminReviewItem } from "@/api";
import { adminStatusText, riskText, targetTypeText } from "@/utils/status";
import type { AdminTab } from "./AdminTabs.vue";

const props = defineProps<{
  tab: AdminTab;
  items: AdminReviewItem[];
  loading: boolean;
  selected: string[];
  allSelected: boolean;
}>();

const emit = defineEmits<{
  toggleAll: [];
  toggleRow: [item: AdminReviewItem];
  decide: [item: AdminReviewItem, decision: AdminDecision];
  undo: [item: AdminReviewItem];
  detail: [item: AdminReviewItem];
}>();

const checkboxTheme: GlobalThemeOverrides = {
  Checkbox: {
    sizeSmall: "16px",
    borderRadius: "2px",
    color: "#f8f9fa",
    colorChecked: "#3874d0",
    border: "1px solid #e5e7eb",
    borderChecked: "1px solid #3874d0",
    borderFocus: "1px solid #3874d0",
    boxShadowFocus: "0 0 0 2px #e6f0ff",
    checkMarkColor: "#fff",
  },
};

// 列宽按 Figma 表格：勾选 16，内容自适应，其余固定宽
const columns = computed(() =>
  props.tab === "pending"
    ? "grid-cols-[16px_minmax(0,1fr)_80px_80px_120px_120px_96px_100px]"
    : "grid-cols-[16px_minmax(0,1fr)_80px_80px_120px_120px_120px_100px]",
);

function keyOf(item: AdminReviewItem): string {
  return `${item.targetType}-${item.targetId}`;
}

function isSelected(item: AdminReviewItem): boolean {
  return props.selected.includes(keyOf(item));
}

function contentText(item: AdminReviewItem): string {
  if (item.targetType === "question") return item.title;
  return item.snippet || item.body || item.title;
}

function formatTime(value?: string | null): string {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}
</script>

<template>
  <n-config-provider abstract :theme-overrides="checkboxTheme">
    <div class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-card-lg bg-surface">
      <div
        class="sticky top-0 z-[1] grid h-10 items-center gap-4 whitespace-nowrap border-0 border-b border-solid border-line bg-hover px-6 text-14 font-medium leading-[22px] text-black"
        :class="columns"
        role="row"
      >
        <n-checkbox
          size="small"
          :checked="allSelected"
          aria-label="全选"
          @update:checked="emit('toggleAll')"
        />
        <span class="text-left">问答内容</span>
        <span>危险程度</span>
        <span>种类</span>
        <span>发布用户</span>
        <template v-if="tab === 'pending'">
          <span>发布时间</span>
          <span>审核次数</span>
        </template>
        <template v-else>
          <span>当前状态</span>
          <span>审核时间</span>
        </template>
        <span class="text-left">操作</span>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto">
        <div
          v-for="item in items"
          :key="keyOf(item)"
          class="grid h-10 items-center gap-4 whitespace-nowrap border-0 border-b border-solid border-line px-6 text-14 font-normal leading-[22px] text-black last:border-b-0"
          :class="columns"
          role="row"
        >
          <n-checkbox
            size="small"
            :checked="isSelected(item)"
            :aria-label="`选择 ${item.title}`"
            @update:checked="emit('toggleRow', item)"
          />

          <div class="min-w-0 overflow-hidden">
            <button
              class="block w-full cursor-pointer truncate border-0 bg-transparent p-0 text-left text-14 font-normal leading-[22px] text-black hover:bg-transparent hover:text-[#3874d0]"
              type="button"
              :title="contentText(item)"
              @click="emit('detail', item)"
            >
              {{ contentText(item) }}
            </button>
          </div>

          <span>{{ riskText(item.riskLevel) }}</span>
          <span>{{ targetTypeText(item.targetType) }}</span>
          <span class="truncate" :title="item.authorNickname">{{ item.authorNickname || "-" }}</span>
          <template v-if="tab === 'pending'">
            <span>{{ formatTime(item.createdAt) }}</span>
            <span class="flex items-center gap-1">
              <span class="text-regular">{{ item.reviewAttempts }}/2</span>
              <span
                v-if="item.arbitrationRequired"
                class="rounded-[2px] bg-[#fff1e6] px-1 text-12 leading-[18px] text-[#b25e00]"
              >
                待仲裁
              </span>
            </span>
          </template>
          <template v-else>
            <span>{{ adminStatusText(item.displayStatus) }}</span>
            <span>{{ formatTime(item.reviewedAt) }}</span>
          </template>

          <div class="flex items-center gap-2.5">
            <template v-if="tab === 'pending'">
              <button
                class="cursor-pointer whitespace-nowrap border-0 bg-transparent p-0 text-14 font-normal leading-[22px] text-muted hover:bg-transparent hover:text-[#3874d0]"
                type="button"
                @click="emit('decide', item, 'approve')"
              >
                通过
              </button>
              <button
                class="cursor-pointer whitespace-nowrap border-0 bg-transparent p-0 text-14 font-normal leading-[22px] text-muted hover:bg-transparent hover:text-danger"
                type="button"
                @click="emit('decide', item, 'reject')"
              >
                拒绝
              </button>
            </template>
            <button
              v-else-if="item.canUndo"
              class="cursor-pointer whitespace-nowrap border-0 bg-transparent p-0 text-14 font-normal leading-[22px] text-muted hover:bg-transparent hover:text-danger"
              type="button"
              @click="emit('undo', item)"
            >
              撤销审核
            </button>
            <button
              class="cursor-pointer whitespace-nowrap border-0 bg-transparent p-0 text-14 font-normal leading-[22px] text-muted hover:bg-transparent hover:text-ink"
              type="button"
              @click="emit('detail', item)"
            >
              详情
            </button>
          </div>
        </div>

        <div v-if="loading" class="flex h-[120px] items-center justify-center text-14 leading-[22px] text-muted">
          加载中…
        </div>
        <div
          v-else-if="items.length === 0"
          class="flex h-[120px] items-center justify-center text-14 leading-[22px] text-muted"
        >
          {{ tab === "pending" ? "暂无待审核内容" : "暂无审核记录" }}
        </div>
      </div>
    </div>
  </n-config-provider>
</template>
