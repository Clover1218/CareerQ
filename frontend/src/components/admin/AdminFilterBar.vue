<script setup lang="ts">
import type { AdminTab } from "./AdminTabs.vue";
import { adminSelectTheme } from "./adminSelectTheme";

defineProps<{
  tab: AdminTab;
  typeFilter: string;
  riskFilter: string;
  statusFilter: string;
  arbitrationFilter: string;
  canArbitrate: boolean;
}>();

const emit = defineEmits<{
  "update:typeFilter": [value: string];
  "update:riskFilter": [value: string];
  "update:statusFilter": [value: string];
  "update:arbitrationFilter": [value: string];
  change: [];
}>();

const selectTheme = adminSelectTheme({ padding: "0 16px", radius: "6px" });

const statusOptions = [
  { label: "已通过", value: "PUBLISHED" },
  { label: "已拒绝", value: "REJECTED" },
  { label: "已下架", value: "REMOVED" },
  { label: "已撤回", value: "WITHDRAWN" },
  { label: "审核中", value: "REVIEWING" },
];

const typeOptions = [
  { label: "学生问题", value: "question" },
  { label: "HR 回答", value: "answer" },
  { label: "学生追问", value: "followup" },
];

const riskOptions = [
  { label: "高危", value: "high" },
  { label: "关注", value: "watch" },
  { label: "普通", value: "normal" },
  { label: "未评级", value: "unrated" },
];

const arbitrationOptions = [
  { label: "待仲裁", value: "true" },
  { label: "普通待审", value: "false" },
];

function onStatus(value: string | number | null) {
  emit("update:statusFilter", value == null ? "" : String(value));
  emit("change");
}

function onType(value: string | number | null) {
  emit("update:typeFilter", value == null ? "" : String(value));
  emit("change");
}

function onRisk(value: string | number | null) {
  emit("update:riskFilter", value == null ? "" : String(value));
  emit("change");
}

function onArbitration(value: string | number | null) {
  emit("update:arbitrationFilter", value == null ? "" : String(value));
  emit("change");
}
</script>

<template>
  <div class="flex min-h-12 flex-wrap items-start justify-between gap-x-4 gap-y-2 px-2 pt-4">
    <n-config-provider abstract :theme-overrides="selectTheme">
      <div class="flex flex-wrap items-center gap-x-8 gap-y-2">
        <label v-if="tab === 'reviewed'" class="flex items-center gap-4">
          <span class="whitespace-nowrap text-14 font-normal leading-[22px] text-regular">当前状态</span>
          <n-select
            class="w-[200px]"
            size="small"
            :value="statusFilter || null"
            :options="statusOptions"
            :show-arrow="false"
            clearable
            placeholder="点击选择状态"
            aria-label="当前状态"
            @update:value="onStatus"
          />
        </label>

        <label v-if="tab === 'pending' && canArbitrate" class="flex items-center gap-4">
          <span class="whitespace-nowrap text-14 font-normal leading-[22px] text-regular">仲裁状态</span>
          <n-select
            class="w-[200px]"
            size="small"
            :value="arbitrationFilter || null"
            :options="arbitrationOptions"
            :show-arrow="false"
            clearable
            placeholder="点击选择"
            aria-label="仲裁状态"
            @update:value="onArbitration"
          />
        </label>

        <label class="flex items-center gap-4">
          <span class="whitespace-nowrap text-14 font-normal leading-[22px] text-regular">内容种类</span>
          <n-select
            class="w-[200px]"
            size="small"
            :value="typeFilter || null"
            :options="typeOptions"
            :show-arrow="false"
            clearable
            placeholder="点击选择种类"
            aria-label="内容种类"
            @update:value="onType"
          />
        </label>

        <label class="flex items-center gap-4">
          <span class="whitespace-nowrap text-14 font-normal leading-[22px] text-regular">危险程度</span>
          <n-select
            class="w-[200px]"
            size="small"
            :value="riskFilter || null"
            :options="riskOptions"
            :show-arrow="false"
            clearable
            placeholder="点击选择程度"
            aria-label="危险程度"
            @update:value="onRisk"
          />
        </label>
      </div>
    </n-config-provider>

    <div class="flex h-8 flex-none items-center gap-2.5">
      <slot />
    </div>
  </div>
</template>
