<script setup lang="ts">
import type { AdminDecision, AdminRecordView, AdminReviewDetail } from "@/api";
import { riskText, targetTypeText } from "@/utils/status";

defineProps<{
  detail: AdminReviewDetail | null;
  loading: boolean;
  deciding: boolean;
  decision: AdminDecision | "";
}>();

const emit = defineEmits<{
  "update:decision": [value: AdminDecision | ""];
  submit: [];
}>();

function formatTime(value?: string | null): string {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

function formatFullTime(value?: string | null): string {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 19);
}

function stageText(stage?: string | null): string {
  const key = (stage ?? "").toUpperCase();
  if (key === "L1" || key === "L2" || key === "L3") return `${key}过滤`;
  if (key === "HUMAN") return "人工审核";
  if (key === "ARBITRATE") return "仲裁";
  return stage ?? "审核";
}

function decisionText(decision?: string | null): string {
  switch ((decision ?? "").toLowerCase()) {
    case "approve":
      return "通过";
    case "reject":
      return "拒绝";
    case "remove":
      return "下架";
    case "restore":
      return "恢复";
    default:
      return decision ?? "";
  }
}

function recordContent(record: AdminRecordView): string {
  if (record.riskLabels?.trim()) return record.riskLabels.split(",").join("、");
  if (record.note?.trim()) return record.note;
  const stage = (record.stage ?? "").toUpperCase();
  if (stage === "L1" || stage === "L2" || stage === "L3") return "无意见";
  return decisionText(record.decision) || "无意见";
}
</script>

<template>
  <section class="flex min-w-0 flex-1 flex-col gap-2.5 bg-surface p-2.5">
    <div class="min-h-0 flex-1 overflow-y-auto p-2.5">
      <div v-if="loading" class="flex h-full items-center justify-center text-14 leading-[22px] text-muted">
        加载中…
      </div>

      <div v-else-if="!detail" class="flex h-full items-center justify-center text-14 leading-[22px] text-muted">
        请从左侧待办队列选择一条内容
      </div>

      <div v-else class="flex flex-col gap-4">
        <div class="flex h-[38px] flex-none items-center gap-4 overflow-hidden whitespace-nowrap bg-[#e6f0ff] px-2">
          <div class="flex items-center gap-2">
            <span class="text-14 leading-[22px] text-black">发布用户:&nbsp;</span>
            <span class="text-14 leading-[22px] text-regular">{{ detail.item.authorNickname || "-" }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-14 leading-[22px] text-black">发布时间:&nbsp;</span>
            <span class="text-14 leading-[22px] text-regular">{{ formatFullTime(detail.item.createdAt) }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-14 leading-[22px] text-black">问答类别：</span>
            <span class="text-14 leading-[22px] text-regular">{{ targetTypeText(detail.item.targetType) }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-14 leading-[22px] text-black">危险程度：</span>
            <span class="text-14 leading-[22px] text-regular">{{ riskText(detail.item.riskLevel) }}</span>
          </div>
        </div>

        <section class="flex flex-col gap-1 px-2">
          <h3 class="m-0 text-14 font-semibold leading-[22px] text-black">问答内容</h3>
          <p class="m-0 whitespace-pre-wrap break-words text-14 font-normal leading-[22px] text-ink">
            {{ detail.item.body }}
          </p>
        </section>

        <section v-if="detail.records.length > 0" class="flex flex-col gap-2.5 px-2.5">
          <h3 class="m-0 text-14 font-semibold leading-[22px] text-black">审核记录</h3>
          <ol class="m-0 flex w-[291px] max-w-full list-none flex-col gap-2 p-0">
            <li
              v-for="(record, index) in detail.records"
              :key="record.id"
              class="flex min-h-[44px] w-full"
            >
              <div class="flex w-[22px] flex-none flex-col items-center">
                <span class="flex h-[22px] w-[22px] items-center justify-center">
                  <span class="h-2.5 w-2.5 rounded-full bg-[#d9d9d9]" />
                </span>
                <span
                  v-if="index < detail.records.length - 1"
                  class="h-[22px] w-px border-0 border-l border-solid border-line"
                />
              </div>
              <div class="flex min-w-0 flex-1 flex-col">
                <div class="flex items-center gap-2">
                  <span class="text-14 font-semibold leading-[22px] text-regular">{{ stageText(record.stage) }}</span>
                  <span class="text-12 leading-[18px] text-muted">{{ formatTime(record.createdAt) }}</span>
                </div>
                <p class="m-0 whitespace-pre-wrap break-words text-14 font-normal leading-[22px] text-black">
                  {{ recordContent(record) }}
                </p>
              </div>
            </li>
          </ol>
        </section>

        <section v-if="detail.context.length > 0" class="flex flex-col gap-2 px-2.5">
          <h3 class="m-0 text-14 font-semibold leading-[22px] text-black">上下文</h3>
          <div class="flex w-[291px] max-w-full flex-col gap-2">
            <article
              v-for="entry in detail.context"
              :key="`${entry.kind}-${entry.id}`"
              class="flex w-full flex-col"
            >
              <div class="flex items-center gap-2">
                <span class="whitespace-nowrap text-14 leading-[22px] text-regular">{{ entry.authorNickname }}</span>
                <span class="whitespace-nowrap text-14 leading-[22px] text-muted">{{ formatTime(entry.createdAt) }}</span>
              </div>
              <p class="m-0 max-w-[168px] whitespace-pre-wrap break-words text-14 font-normal leading-[22px] text-black">
                {{ entry.body }}
              </p>
            </article>
          </div>
        </section>
      </div>
    </div>

    <div class="flex h-8 flex-none items-center justify-end gap-8 px-2.5">
      <div class="flex items-center gap-2.5">
        <span class="whitespace-nowrap text-14 font-medium leading-[22px] text-black">判断结果：</span>
        <button
          class="inline-flex h-8 cursor-pointer items-center justify-center whitespace-nowrap border border-solid px-4 py-2 text-14 font-normal leading-[22px]"
          :class="
            decision === 'approve'
              ? 'border-[#4a83e3] bg-[#e6f0ff] text-[#3874d0] hover:bg-[#e6f0ff]'
              : 'border-line bg-transparent text-black hover:bg-hover'
          "
          type="button"
          :aria-pressed="decision === 'approve'"
          @click="emit('update:decision', decision === 'approve' ? '' : 'approve')"
        >
          通过
        </button>
        <button
          class="inline-flex h-8 cursor-pointer items-center justify-center whitespace-nowrap border border-solid px-4 py-2 text-14 font-normal leading-[22px]"
          :class="
            decision === 'reject'
              ? 'border-[#4a83e3] bg-[#e6f0ff] text-[#3874d0] hover:bg-[#e6f0ff]'
              : 'border-line bg-transparent text-black hover:bg-hover'
          "
          type="button"
          :aria-pressed="decision === 'reject'"
          @click="emit('update:decision', decision === 'reject' ? '' : 'reject')"
        >
          拒绝
        </button>
      </div>
      <button
        class="inline-flex h-8 min-w-[60px] cursor-pointer items-center justify-center whitespace-nowrap rounded-[2px] border-0 bg-[#4a83e3] px-4 py-2 text-14 font-normal leading-[22px] text-white enabled:hover:bg-[#3874d0] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-[#4a83e3]"
        type="button"
        :disabled="!detail || !decision || deciding"
        @click="emit('submit')"
      >
        提交
      </button>
    </div>
  </section>
</template>