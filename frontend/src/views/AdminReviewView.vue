<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { NInput, useDialog, useMessage } from "naive-ui";
import {
  api,
  type AdminDecision,
  type AdminReviewDetail,
  type AdminReviewItem,
  type AdminReviewPage,
  type ReviewTargetType,
} from "@/api";
import { useUserStore } from "@/stores/user";
import AdminSidebar from "@/components/admin/AdminSidebar.vue";
import AdminTabs, { type AdminTab } from "@/components/admin/AdminTabs.vue";
import AdminFilterBar from "@/components/admin/AdminFilterBar.vue";
import AdminReviewTable from "@/components/admin/AdminReviewTable.vue";
import AdminPagination from "@/components/admin/AdminPagination.vue";
import AdminReviewDrawer from "@/components/admin/AdminReviewDrawer.vue";

const router = useRouter();
const dialog = useDialog();
const message = useMessage();
const userStore = useUserStore();
const { user, canArbitrate } = storeToRefs(userStore);

const activeTab = ref<AdminTab>("pending");
const typeFilter = ref<ReviewTargetType | "">("");
const riskFilter = ref("");
const statusFilter = ref("");
const arbitrationFilter = ref("");
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const deciding = ref(false);
const error = ref("");
const data = ref<AdminReviewPage>({ items: [], total: 0, page: 1, size: 20 });
const selected = ref<string[]>([]);

const drawerOpen = ref(false);
const drawerLoading = ref(false);
const detail = ref<AdminReviewDetail | null>(null);
const undoNote = ref("");

const allSelected = computed(
  () => data.value.items.length > 0 && data.value.items.every((item) => selected.value.includes(keyOf(item))),
);

const selectedTargets = computed(() =>
  data.value.items
    .filter((item) => selected.value.includes(keyOf(item)))
    .map((item) => ({ targetType: item.targetType, targetId: item.targetId })),
);

function keyOf(item: AdminReviewItem): string {
  return `${item.targetType}-${item.targetId}`;
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const params = {
      type: typeFilter.value || undefined,
      risk: riskFilter.value || undefined,
      status: activeTab.value === "reviewed" ? statusFilter.value || undefined : undefined,
      arbitration:
        activeTab.value === "pending" && arbitrationFilter.value
          ? arbitrationFilter.value === "true"
          : undefined,
      page: page.value,
      size: pageSize.value,
    };
    data.value =
      activeTab.value === "pending"
        ? await api.adminReviewQueue(params)
        : await api.adminReviewed(params);
    selected.value = selected.value.filter((key) =>
      data.value.items.some((item) => keyOf(item) === key),
    );
  } catch (e: any) {
    error.value = e?.message ?? "列表加载失败";
  } finally {
    loading.value = false;
  }
}

function switchTab(tab: AdminTab) {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  arbitrationFilter.value = "";
  page.value = 1;
  selected.value = [];
  drawerOpen.value = false;
  void load();
}

function changeFilter() {
  page.value = 1;
  selected.value = [];
  void load();
}

function changePage(next: number) {
  page.value = next;
  selected.value = [];
  void load();
}

function changePageSize(next: number) {
  pageSize.value = next;
  page.value = 1;
  selected.value = [];
  void load();
}

function toggleAll() {
  selected.value = allSelected.value ? [] : data.value.items.map(keyOf);
}

function toggleRow(item: AdminReviewItem) {
  const key = keyOf(item);
  selected.value = selected.value.includes(key)
    ? selected.value.filter((value) => value !== key)
    : [...selected.value, key];
}

function confirmBatch(decision: AdminDecision) {
  const verb = decision === "approve" ? "通过" : "拒绝";
  dialog.warning({
    title: `确认${verb}`,
    content: `确定要${verb}选中的 ${selectedTargets.value.length} 条内容吗？`,
    positiveText: `确认${verb}`,
    negativeText: "取消",
    onPositiveClick: () => submitBatch(decision),
  });
}

async function submitBatch(decision: AdminDecision) {
  if (deciding.value || selectedTargets.value.length === 0) return;
  deciding.value = true;
  try {
    await api.adminBatchDecide(selectedTargets.value, decision);
    message.success(decision === "approve" ? "已通过" : "已拒绝");
    selected.value = [];
    await load();
  } catch (e: any) {
    message.error(e?.message ?? "操作失败");
  } finally {
    deciding.value = false;
  }
}

function confirmOne(item: AdminReviewItem, decision: AdminDecision, fromDrawer = false) {
  const verb = decision === "approve" ? "通过" : "拒绝";
  dialog.warning({
    title: `确认${verb}`,
    content: `确定要${verb}这条内容吗？`,
    positiveText: `确认${verb}`,
    negativeText: "取消",
    onPositiveClick: () => decideOne(item, decision, fromDrawer),
  });
}

async function decideOne(item: AdminReviewItem, decision: AdminDecision, fromDrawer = false) {
  const verb = decision === "approve" ? "通过" : "拒绝";
  try {
    await api.adminDecide(item.targetType, item.targetId, decision);
    message.success(`已${verb}`);
    if (fromDrawer) drawerOpen.value = false;
    await load();
  } catch (e: any) {
    message.error(e?.message ?? "操作失败");
  }
}

function confirmUndo(item: AdminReviewItem, fromDrawer = false) {
  undoNote.value = "";
  dialog.warning({
    title: "撤销审核",
    content: () =>
      h("div", { class: "flex flex-col gap-2" }, [
        h("p", { class: "m-0" }, "撤销后内容退回待审池，且你不能再审核该内容。请填写撤销原因："),
        h(NInput, {
          value: undoNote.value,
          type: "textarea",
          maxlength: 500,
          autofocus: true,
          placeholder: "请输入撤销原因（必填）",
          "onUpdate:value": (value: string) => {
            undoNote.value = value;
          },
        }),
      ]),
    positiveText: "确认撤销",
    negativeText: "取消",
    onPositiveClick: () => {
      if (!undoNote.value.trim()) {
        message.warning("请填写撤销原因");
        return false;
      }
      return undoOne(item, fromDrawer);
    },
  });
}

async function undoOne(item: AdminReviewItem, fromDrawer = false): Promise<boolean> {
  try {
    await api.adminUndo(item.targetType, item.targetId, undoNote.value.trim());
    message.success("已撤销审核");
    if (fromDrawer) drawerOpen.value = false;
    await load();
    return true;
  } catch (e: any) {
    message.error(e?.message ?? "操作失败");
    return false;
  }
}

async function openDetail(item: AdminReviewItem) {
  drawerOpen.value = true;
  drawerLoading.value = true;
  detail.value = null;
  try {
    detail.value = await api.adminReviewDetail(item.targetType, item.targetId);
  } catch (e: any) {
    message.error(e?.message ?? "详情加载失败");
    drawerOpen.value = false;
  } finally {
    drawerLoading.value = false;
  }
}

function closeDrawer() {
  drawerOpen.value = false;
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && drawerOpen.value) closeDrawer();
}

watch(drawerOpen, (open) => {
  document.body.style.overflow = open ? "hidden" : "";
});

onMounted(() => {
  window.addEventListener("keydown", onKeydown);
  void load();
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
  document.body.style.overflow = "";
});

function decideFromDrawer(decision: AdminDecision) {
  if (detail.value) {
    confirmOne(detail.value.item, decision, true);
  }
}

function undoFromDrawer() {
  if (detail.value) {
    confirmUndo(detail.value.item, true);
  }
}

function goHome() {
  router.push("/");
}

function goWorkspace() {
  router.push({ name: "admin-review-workspace" });
}
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-canvas text-ink">
    <AdminSidebar :nickname="user?.nickname" @home="goHome" />

    <section class="min-w-0 flex-1 overflow-auto p-4">
      <div class="flex h-full min-h-0 w-full min-w-[828px] flex-col gap-4 bg-surface p-4">
        <AdminTabs :model-value="activeTab" @update:model-value="switchTab">
          <button
            v-if="activeTab === 'pending'"
            class="inline-flex h-8 cursor-pointer items-center justify-center whitespace-nowrap border-0 bg-transparent px-2 py-0 text-14 font-semibold leading-[22px] text-black hover:bg-transparent hover:text-[#3874d0]"
            type="button"
            @click="goWorkspace"
          >
            进入审核台
          </button>
        </AdminTabs>

        <AdminFilterBar
          v-model:type-filter="typeFilter"
          v-model:risk-filter="riskFilter"
          v-model:status-filter="statusFilter"
          v-model:arbitration-filter="arbitrationFilter"
          :tab="activeTab"
          :can-arbitrate="canArbitrate"
          @change="changeFilter"
        >
          <template v-if="selected.length > 0 && activeTab === 'pending'">
            <span class="whitespace-nowrap text-14 leading-[22px] text-regular">已选 {{ selected.length }} 条</span>
            <button
              class="h-8 cursor-pointer rounded-card border-0 bg-hover px-4 py-2 text-14 leading-[22px] text-black enabled:hover:bg-line disabled:cursor-not-allowed disabled:opacity-60"
              type="button"
              :disabled="deciding"
              @click="confirmBatch('reject')"
            >
              批量拒绝
            </button>
            <button
              class="h-8 cursor-pointer rounded-card border-0 bg-[#4a83e3] px-4 py-2 text-14 leading-[22px] text-white enabled:hover:bg-[#3874d0] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-[#4a83e3]"
              type="button"
              :disabled="deciding"
              @click="confirmBatch('approve')"
            >
              批量通过
            </button>
          </template>
        </AdminFilterBar>

        <p v-if="error" class="mx-2 -mt-2 flex items-center gap-3 text-14 leading-[22px] text-danger">
          {{ error }}
          <button
            class="cursor-pointer border-0 bg-transparent p-0 text-14 text-[#3874d0] hover:bg-transparent"
            type="button"
            @click="load"
          >
            重试
          </button>
        </p>

        <AdminReviewTable
          :tab="activeTab"
          :items="data.items"
          :loading="loading"
          :selected="selected"
          :all-selected="allSelected"
          @toggle-all="toggleAll"
          @toggle-row="toggleRow"
          @decide="confirmOne"
          @undo="confirmUndo"
          @detail="openDetail"
        />

        <AdminPagination
          :page="page"
          :page-size="pageSize"
          :total="data.total"
          @update:page="changePage"
          @update:page-size="changePageSize"
        />
      </div>
    </section>

    <AdminReviewDrawer
      :open="drawerOpen"
      :loading="drawerLoading"
      :detail="detail"
      :pending="activeTab === 'pending'"
      @close="closeDrawer"
      @decide="decideFromDrawer"
      @undo="undoFromDrawer"
    />
  </div>
</template>
