<script setup lang="ts">
import type { AdminDecision, AdminRecordView, AdminReviewDetail } from "@/api";
import { adminStatusText, riskText, targetTypeText } from "@/utils/status";
import AdminReviewContext from "@/components/admin/AdminReviewContext.vue";

defineProps<{
  open: boolean;
  loading: boolean;
  detail: AdminReviewDetail | null;
  pending: boolean;
}>();

const emit = defineEmits<{
  close: [];
  decide: [decision: AdminDecision];
  undo: [];
}>();

function formatTime(value?: string | null): string {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
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

function recordTitle(record: AdminRecordView): string {
  if (record.decision === "undo") return "审核人已撤销，内容退回待审池";
  const stage = (record.stage ?? "").toUpperCase();
  if (stage === "ARBITRATE") return `仲裁裁定：${decisionText(record.decision)}`;
  if (stage === "HUMAN") return `人工审核：${decisionText(record.decision)}`;
  return `${stage || "系统"}：${decisionText(record.decision)}`;
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="open" class="fixed inset-0 z-[1000] flex justify-end bg-[#1118273d]" @click.self="emit('close')">
        <aside
          class="drawer flex h-screen w-[600px] max-w-full flex-col gap-4 bg-surface p-4 shadow-[-8px_0_24px_rgba(17,24,39,0.08)]"
          role="dialog"
          aria-modal="true"
          aria-label="审核详情"
        >
          <div class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto">
            <h2 class="m-0 text-24 font-bold leading-8 text-ink">详情</h2>

            <div v-if="loading" class="py-10 text-center text-14 text-muted">加载中…</div>

            <template v-else-if="detail">
              <section class="flex flex-col gap-2">
                <h3 class="m-0 text-18 font-semibold leading-[26px] text-ink">问答描述</h3>
                <p class="m-0 max-w-[518px] whitespace-pre-wrap break-words text-16 font-normal leading-6 text-black">
                  {{ detail.item.body }}
                </p>
              </section>

              <section class="flex flex-col gap-2">
                <h3 class="m-0 text-18 font-semibold leading-[26px] text-ink">基础信息</h3>
                <div class="flex flex-col">
                  <div class="flex min-h-[22px] gap-4 text-14 leading-[22px]">
                    <span class="w-14 flex-none text-regular">问答类别</span>
                    <span class="break-words text-black">{{ targetTypeText(detail.item.targetType) }}</span>
                  </div>
                  <div class="flex min-h-[22px] gap-4 text-14 leading-[22px]">
                    <span class="w-14 flex-none text-regular">发布时间</span>
                    <span class="break-words text-black">{{ formatTime(detail.item.createdAt) }}</span>
                  </div>
                  <div class="flex min-h-[22px] gap-4 text-14 leading-[22px]">
                    <span class="w-14 flex-none text-regular">危险程度</span>
                    <span class="break-words text-black">{{ riskText(detail.item.riskLevel) }}</span>
                  </div>
                  <div class="flex min-h-[22px] items-center gap-4 text-14 leading-[22px]">
                    <span class="w-14 flex-none text-regular">审核次数</span>
                    <span class="flex items-center gap-1 text-black">
                      <span>{{ detail.item.reviewAttempts }}/2</span>
                      <span
                        v-if="detail.item.arbitrationRequired"
                        class="rounded-[2px] bg-[#fff1e6] px-1 text-12 leading-[18px] text-[#b25e00]"
                      >
                        待仲裁
                      </span>
                    </span>
                  </div>
                  <div class="flex min-h-[22px] gap-4 text-14 leading-[22px]">
                    <span class="w-14 flex-none text-regular">发布用户</span>
                    <span class="break-words text-black">{{ detail.item.authorNickname }}</span>
                  </div>
                  <div v-if="!pending" class="flex min-h-[22px] gap-4 text-14 leading-[22px]">
                    <span class="w-14 flex-none text-regular">当前状态</span>
                    <span class="break-words text-black">{{ adminStatusText(detail.item.displayStatus) }}</span>
                  </div>
                </div>
              </section>

              <section v-if="detail.context.length > 0" class="flex flex-col gap-2">
                <h3 class="m-0 text-18 font-semibold leading-[26px] text-ink">上下文</h3>
                <AdminReviewContext
                  :target-type="detail.item.targetType"
                  :target-id="detail.item.targetId"
                  :entries="detail.context"
                  :before-remaining="detail.contextBeforeRemaining"
                  :after-remaining="detail.contextAfterRemaining"
                  :pending="pending"
                />
              </section>

              <section v-if="detail.records.length > 0" class="flex flex-col gap-2">
                <h3 class="m-0 text-18 font-semibold leading-[26px] text-ink">审核记录</h3>
                <ol class="m-0 flex list-none flex-col gap-2.5 p-0">
                  <li
                    v-for="record in detail.records.slice().reverse()"
                    :key="record.id"
                    class="flex flex-col gap-1 text-14 leading-[22px]"
                  >
                    <div class="flex flex-wrap items-center gap-x-2">
                      <span class="text-black">{{ recordTitle(record) }}</span>
                      <span class="text-muted">{{ formatTime(record.createdAt) }}</span>
                    </div>
                    <div v-if="record.operatorNickname || record.note" class="flex flex-wrap items-center gap-x-2 text-muted">
                      <span v-if="record.operatorNickname">{{ record.operatorNickname }}</span>
                      <span v-if="record.note" class="break-words">备注：{{ record.note }}</span>
                    </div>
                  </li>
                </ol>
              </section>
            </template>
          </div>

          <footer v-if="detail && !loading" class="flex h-8 flex-none items-center justify-between gap-2.5">
            <button
              class="inline-flex h-8 cursor-pointer items-center justify-center whitespace-nowrap rounded-card border-0 bg-hover px-4 py-2 text-14 font-normal leading-[22px] text-black hover:bg-line"
              type="button"
              @click="emit('close')"
            >
              返回列表
            </button>
            <div class="flex items-center gap-2.5">
              <template v-if="pending">
                <button
                  class="inline-flex h-8 cursor-pointer items-center justify-center whitespace-nowrap rounded-card border-0 bg-hover px-4 py-2 text-14 font-normal leading-[22px] text-black hover:bg-line"
                  type="button"
                  @click="emit('decide', 'reject')"
                >
                  拒绝
                </button>
                <button
                  class="inline-flex h-8 cursor-pointer items-center justify-center whitespace-nowrap rounded-card border-0 bg-[#4a83e3] px-4 py-2 text-14 font-normal leading-[22px] text-white hover:bg-[#3874d0]"
                  type="button"
                  @click="emit('decide', 'approve')"
                >
                  通过
                </button>
              </template>
              <button
                v-else-if="detail.item.canUndo"
                class="inline-flex h-8 cursor-pointer items-center justify-center whitespace-nowrap rounded-card border-0 bg-hover px-4 py-2 text-14 font-normal leading-[22px] text-danger hover:bg-line"
                type="button"
                @click="emit('undo')"
              >
                撤销审核
              </button>
            </div>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.2s ease;
}

.drawer-enter-active .drawer,
.drawer-leave-active .drawer {
  transition: transform 0.24s ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .drawer,
.drawer-leave-to .drawer {
  transform: translateX(100%);
}
</style>
