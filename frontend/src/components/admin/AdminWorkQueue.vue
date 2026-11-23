<script setup lang="ts">
import { computed } from "vue";
import type { AdminReviewItem } from "@/api";
import AdminIcon from "./AdminIcon.vue";

const props = defineProps<{
  items: AdminReviewItem[];
  selectedKey: string | null;
  loading: boolean;
  error: string;
  page: number;
  pageSize: number;
  total: number;
}>();

const emit = defineEmits<{
  select: [item: AdminReviewItem];
  "update:page": [value: number];
}>();

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

function keyOf(item: AdminReviewItem): string {
  return `${item.targetType}-${item.targetId}`;
}

function contentText(item: AdminReviewItem): string {
  if (item.targetType === "question") return item.title;
  return item.snippet || item.body || item.title;
}

function goPage(next: number) {
  const target = Math.min(Math.max(next, 1), pageCount.value);
  if (target !== props.page) emit("update:page", target);
}
</script>

<template>
  <aside class="flex w-[263px] flex-none flex-col bg-surface p-4">
    <div class="flex min-h-0 flex-1 flex-col border border-solid border-line">
      <div class="flex h-[43px] flex-none items-center border-0 border-b border-solid border-line bg-hover px-4">
        <h2 class="m-0 text-14 font-semibold leading-[22px] text-black">待办队列</h2>
      </div>

      <p v-if="error" class="m-0 px-4 py-2 text-14 leading-[22px] text-danger">{{ error }}</p>

      <div class="min-h-0 flex-1 overflow-y-auto">
        <button
          v-for="item in items"
          :key="keyOf(item)"
          class="flex h-[42px] w-full cursor-pointer items-center gap-2.5 border-0 px-4 py-2.5 text-left text-14 font-normal leading-[22px] text-black"
          :class="
            selectedKey === keyOf(item)
              ? 'bg-[#e6f0ff] hover:bg-[#e6f0ff]'
              : 'bg-transparent hover:bg-canvas'
          "
          type="button"
          :title="contentText(item)"
          @click="emit('select', item)"
        >
          <span class="min-w-0 flex-1 truncate">{{ contentText(item) }}</span>
          <AdminIcon name="chevron" :size="12" class="flex-none -rotate-90 text-[#4e5969]" />
        </button>

        <div v-if="loading" class="flex h-[100px] items-center justify-center text-14 leading-[22px] text-muted">
          加载中…
        </div>
        <div
          v-else-if="items.length === 0"
          class="flex h-[100px] items-center justify-center text-14 leading-[22px] text-muted"
        >
          暂无待审核内容
        </div>
      </div>
    </div>

    <div class="mt-2.5 flex h-8 flex-none items-center justify-between pl-1.5">
      <div class="flex items-center gap-2.5 whitespace-nowrap text-14 font-normal leading-[22px] text-black">
        <span>共 {{ total }} 条</span>
        <span>第 {{ page }} / {{ pageCount }} 页</span>
      </div>

      <div class="flex items-center gap-2">
        <button
          class="inline-flex h-8 w-8 items-center justify-center rounded-[4px] border-0 bg-transparent p-2.5 text-14 font-normal leading-[22px]"
          :class="page <= 1 ? 'cursor-not-allowed text-[#d1d5db]' : 'cursor-pointer text-regular hover:bg-hover'"
          type="button"
          :disabled="page <= 1"
          aria-label="上一页"
          @click="goPage(page - 1)"
        >
          &lt;
        </button>
        <button
          class="inline-flex h-8 w-8 items-center justify-center rounded-[4px] border-0 bg-transparent p-2.5 text-14 font-normal leading-[22px]"
          :class="page >= pageCount ? 'cursor-not-allowed text-[#d1d5db]' : 'cursor-pointer text-regular hover:bg-hover'"
          type="button"
          :disabled="page >= pageCount"
          aria-label="下一页"
          @click="goPage(page + 1)"
        >
          &gt;
        </button>
      </div>
    </div>
  </aside>
</template>