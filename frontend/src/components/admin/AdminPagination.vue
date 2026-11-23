<script setup lang="ts">
import { computed } from "vue";
import { adminSelectTheme } from "./adminSelectTheme";

const props = defineProps<{
  total: number;
  page: number;
  pageSize: number;
}>();

const emit = defineEmits<{
  "update:page": [value: number];
  "update:pageSize": [value: number];
}>();

const selectTheme = adminSelectTheme({
  padding: "0 26px 0 8px",
  radius: "4px",
  textColor: "#000",
});

const sizeOptions = [
  { label: "20条/页", value: 20 },
  { label: "50条/页", value: 50 },
];

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

const blocks = computed<Array<number | "ellipsis">>(() => {
  const count = pageCount.value;
  const current = props.page;
  if (count <= 7) {
    return Array.from({ length: count }, (_, index) => index + 1);
  }
  const items: Array<number | "ellipsis"> = [1];
  const start = Math.max(2, current - 1);
  const end = Math.min(count - 1, current + 1);
  if (start > 2) items.push("ellipsis");
  for (let number = start; number <= end; number += 1) items.push(number);
  if (end < count - 1) items.push("ellipsis");
  items.push(count);
  return items;
});

function go(next: number) {
  const target = Math.min(Math.max(next, 1), pageCount.value);
  if (target !== props.page) emit("update:page", target);
}

function onSize(value: string | number | null) {
  emit("update:pageSize", Number(value ?? 20));
}
</script>

<template>
  <n-config-provider abstract :theme-overrides="selectTheme">
    <div class="flex h-8 items-center justify-end gap-2.5 px-2">
      <span class="whitespace-nowrap text-14 font-normal leading-[22px] text-black">共 {{ total }} 条</span>

      <div class="flex items-center gap-2">
        <button
          class="inline-flex h-8 w-8 items-center justify-center rounded-[4px] border-0 bg-transparent p-2.5 text-14 font-normal leading-[22px]"
          :class="page <= 1 ? 'cursor-not-allowed text-[#d1d5db]' : 'cursor-pointer text-regular hover:bg-hover'"
          type="button"
          :disabled="page <= 1"
          aria-label="上一页"
          @click="go(page - 1)"
        >
          &lt;
        </button>
        <template v-for="(block, index) in blocks" :key="`${block}-${index}`">
          <span
            v-if="block === 'ellipsis'"
            class="inline-flex h-8 w-8 cursor-default items-center justify-center p-2.5 text-14 font-normal leading-[22px] text-regular"
          >
            ...
          </span>
          <button
            v-else
            class="inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-[4px] border-0 p-2.5 text-14 font-normal leading-[22px]"
            :class="block === page ? 'bg-[#e6f0ff] text-[#3874d0]' : 'bg-transparent text-regular hover:bg-hover'"
            type="button"
            @click="go(block)"
          >
            {{ block }}
          </button>
        </template>
        <button
          class="inline-flex h-8 w-8 items-center justify-center rounded-[4px] border-0 bg-transparent p-2.5 text-14 font-normal leading-[22px]"
          :class="page >= pageCount ? 'cursor-not-allowed text-[#d1d5db]' : 'cursor-pointer text-regular hover:bg-hover'"
          type="button"
          :disabled="page >= pageCount"
          aria-label="下一页"
          @click="go(page + 1)"
        >
          &gt;
        </button>
      </div>

      <n-select
        class="w-[89px]"
        size="small"
        :value="pageSize"
        :options="sizeOptions"
        aria-label="每页条数"
        @update:value="onSize"
      />
    </div>
  </n-config-provider>
</template>
