<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useMessage } from "naive-ui";
import {
  api,
  type AdminDecision,
  type AdminReviewDetail,
  type AdminReviewItem,
  type ReviewTargetType,
} from "@/api";
import { useUserStore } from "@/stores/user";
import { adminSelectTheme } from "@/components/admin/adminSelectTheme";
import AdminHoverSidebar from "@/components/admin/AdminHoverSidebar.vue";
import AdminWorkQueue from "@/components/admin/AdminWorkQueue.vue";
import AdminWorkDetail from "@/components/admin/AdminWorkDetail.vue";

const router = useRouter();
const message = useMessage();
const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const selectTheme = adminSelectTheme({ padding: "0 16px", radius: "0px" });

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

const QUEUE_PAGE_SIZE = 10;

const typeFilter = ref<ReviewTargetType | "">("");
const riskFilter = ref("");
/** 只在点「获取数据」时快照，翻页与提交后刷新都沿用已应用条件 */
const appliedType = ref<ReviewTargetType | "">("");
const appliedRisk = ref("");
const items = ref<AdminReviewItem[]>([]);
const selectedKey = ref<string | null>(null);
const detail = ref<AdminReviewDetail | null>(null);
const decision = ref<AdminDecision | "">("");
const queuePage = ref(1);
const queueTotal = ref(0);
const queueLoading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const error = ref("");

let detailSeq = 0;
let queueSeq = 0;

const selectedItem = computed(
  () => items.value.find((item) => keyOf(item) === selectedKey.value) ?? null,
);

function keyOf(item: AdminReviewItem): string {
  return `${item.targetType}-${item.targetId}`;
}

function onType(value: string | number | null) {
  typeFilter.value = (value ?? "") as ReviewTargetType | "";
}

function onRisk(value: string | number | null) {
  riskFilter.value = value == null ? "" : String(value);
}

async function loadDetail(key: string | null) {
  const item = items.value.find((entry) => keyOf(entry) === key);
  if (!item) {
    detail.value = null;
    detailLoading.value = false;
    return;
  }
  const seq = ++detailSeq;
  detailLoading.value = true;
  decision.value = "";
  try {
    const result = await api.adminReviewDetail(item.targetType, item.targetId);
    if (seq !== detailSeq) return;
    detail.value = result;
  } catch (e: any) {
    if (seq !== detailSeq) return;
    detail.value = null;
    message.error(e?.message ?? "详情加载失败");
  } finally {
    if (seq === detailSeq) detailLoading.value = false;
  }
}

async function loadQueue(nextPage: number, preferKey: string | null = null) {
  const seq = ++queueSeq;
  queueLoading.value = true;
  error.value = "";
  try {
    const page = await api.adminReviewQueue({
      type: appliedType.value || undefined,
      risk: appliedRisk.value || undefined,
      page: nextPage,
      size: QUEUE_PAGE_SIZE,
    });
    if (seq !== queueSeq) return;

    // 提交/撤销后总数可能缩水，请求页越界时回到最后一页
    const lastPage = Math.max(1, Math.ceil(page.total / QUEUE_PAGE_SIZE));
    if (page.total > 0 && nextPage > lastPage) {
      void loadQueue(lastPage);
      return;
    }

    queuePage.value = page.page;
    queueTotal.value = page.total;
    items.value = page.items;
    const nextKey =
      preferKey && page.items.some((item) => keyOf(item) === preferKey)
        ? preferKey
        : page.items[0]
          ? keyOf(page.items[0])
          : null;
    selectedKey.value = nextKey;
    await loadDetail(nextKey);
  } catch (e: any) {
    if (seq !== queueSeq) return;
    error.value = e?.message ?? "列表加载失败";
    items.value = [];
    queuePage.value = 1;
    queueTotal.value = 0;
    selectedKey.value = null;
    detail.value = null;
  } finally {
    if (seq === queueSeq) queueLoading.value = false;
  }
}

function selectItem(item: AdminReviewItem) {
  const key = keyOf(item);
  if (key === selectedKey.value) return;
  selectedKey.value = key;
  void loadDetail(key);
}

function changePage(next: number) {
  if (queueLoading.value || next === queuePage.value) return;
  void loadQueue(next);
}

function refresh() {
  if (queueLoading.value) return;
  appliedType.value = typeFilter.value;
  appliedRisk.value = riskFilter.value;
  void loadQueue(1);
}

async function submit() {
  const item = selectedItem.value;
  if (!item || !decision.value || submitting.value) return;
  submitting.value = true;
  const current = decision.value;
  const index = items.value.findIndex((entry) => keyOf(entry) === keyOf(item));
  const nextItem = items.value[index + 1] ?? items.value[index - 1] ?? null;
  try {
    await api.adminDecide(item.targetType, item.targetId, current);
    message.success(current === "approve" ? "已通过" : "已拒绝");
    decision.value = "";
    // 被审内容会离开待办队列；等刷新回来后优先选中原位置的下一条
    const totalAfter = Math.max(0, queueTotal.value - 1);
    const lastPage = Math.max(1, Math.ceil(totalAfter / QUEUE_PAGE_SIZE));
    const targetPage = Math.min(queuePage.value, lastPage);
    await loadQueue(targetPage, targetPage === queuePage.value && nextItem ? keyOf(nextItem) : null);
  } catch (e: any) {
    message.error(e?.message ?? "操作失败");
  } finally {
    submitting.value = false;
  }
}

function goHome() {
  void router.push("/");
}

function goReviewList() {
  void router.push({ name: "admin-review" });
}

onMounted(() => {
  void loadQueue(1);
});
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-surface text-ink">
    <AdminHoverSidebar :nickname="user?.nickname" :avatar-url="user?.avatarUrl" @home="goHome" @review="goReviewList" />

    <section class="flex min-w-0 flex-1 flex-col gap-4 overflow-hidden bg-canvas p-2.5">
      <header class="flex h-16 flex-none items-center gap-4 bg-surface px-4">
        <button
          class="flex h-8 flex-none cursor-pointer items-center border-0 bg-transparent px-0 py-0 text-14 font-semibold leading-[22px] text-black hover:bg-transparent hover:text-[#3874d0]"
          type="button"
          title="返回审核列表"
          aria-label="返回审核列表"
          @click="goReviewList"
        >
          <
        </button>

        <n-config-provider abstract :theme-overrides="selectTheme">
          <div class="flex items-center gap-8">
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

        <button
          class="inline-flex h-8 flex-none cursor-pointer items-center justify-center whitespace-nowrap rounded-[2px] border-0 bg-[#4a83e3] px-4 py-2 text-14 font-normal leading-[22px] text-white enabled:hover:bg-[#3874d0] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-[#4a83e3]"
          type="button"
          :disabled="queueLoading"
          @click="refresh"
        >
          获取数据
        </button>
      </header>

      <div class="flex min-h-0 flex-1 gap-4 overflow-hidden bg-surface">
        <AdminWorkQueue
          :items="items"
          :selected-key="selectedKey"
          :loading="queueLoading"
          :error="error"
          :page="queuePage"
          :page-size="QUEUE_PAGE_SIZE"
          :total="queueTotal"
          @select="selectItem"
          @update:page="changePage"
        />
        <AdminWorkDetail
          :detail="detail"
          :loading="detailLoading"
          :deciding="submitting"
          :decision="decision"
          @update:decision="decision = $event"
          @submit="submit"
        />
      </div>
    </section>
  </div>
</template>