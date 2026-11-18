<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api, type ModerationQueueItem, type ModerationTargetType } from "../api";

type QueueFilter = "all" | ModerationTargetType;

const labels: Record<ModerationTargetType, string> = {
  question: "问题",
  answer: "回答",
  followup: "追问",
  public_post: "公开内容",
};
const filters: { id: QueueFilter; label: string }[] = [
  { id: "all", label: "全部" },
  { id: "question", label: "问题" },
  { id: "answer", label: "回答" },
  { id: "followup", label: "追问" },
  { id: "public_post", label: "公开内容" },
];

const queue = ref<ModerationQueueItem[]>([]);
const selectedId = ref<string | null>(null);
const filter = ref<QueueFilter>("all");
const note = ref("");
const loading = ref(false);
const acting = ref(false);
const error = ref("");
const notice = ref("");

const visibleItems = computed(() => filter.value === "all"
  ? queue.value
  : queue.value.filter((item) => item.targetType === filter.value));
const selected = computed(() => visibleItems.value.find((item) => itemKey(item) === selectedId.value) ?? null);
const counts = computed(() => Object.fromEntries([
  ["all", queue.value.length],
  ...Object.keys(labels).map((type) => [type, queue.value.filter((item) => item.targetType === type).length]),
]) as Record<QueueFilter, number>);

function itemKey(item: ModerationQueueItem) {
  return `${item.targetType}:${item.targetId}`;
}

async function loadQueue() {
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    queue.value = await api.moderationQueue();
    if (!queue.value.some((item) => itemKey(item) === selectedId.value)) {
      selectedId.value = visibleItems.value[0] ? itemKey(visibleItems.value[0]) : null;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "审核队列加载失败";
  } finally {
    loading.value = false;
  }
}

function selectFilter(next: QueueFilter) {
  filter.value = next;
  if (!visibleItems.value.some((item) => itemKey(item) === selectedId.value)) {
    selectedId.value = visibleItems.value[0] ? itemKey(visibleItems.value[0]) : null;
  }
}

async function decide(decision: "approve" | "reject" | "remove") {
  if (!selected.value || acting.value) return;
  const current = selected.value;
  if (decision === "remove" && !window.confirm("确认下架这条内容？")) return;

  acting.value = true;
  error.value = "";
  notice.value = "";
  try {
    await api.decideModeration(current.targetType, current.targetId, decision, note.value);
    queue.value = queue.value.filter((item) => itemKey(item) !== itemKey(current));
    note.value = "";
    selectedId.value = visibleItems.value[0] ? itemKey(visibleItems.value[0]) : null;
    notice.value = decision === "approve" ? "已通过审核。" : decision === "reject" ? "已驳回内容。" : "已下架内容。";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "审核操作失败，请重试。";
  } finally {
    acting.value = false;
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString("zh-CN", { dateStyle: "medium", timeStyle: "short" });
}

onMounted(loadQueue);
</script>

<template>
  <div class="moderation-heading">
    <div>
      <h2>内容审核</h2>
      <p class="muted">按提交时间查看等待人工复核的公开内容。</p>
    </div>
    <button type="button" :disabled="loading" @click="loadQueue">
      {{ loading ? "刷新中…" : "刷新队列" }}
    </button>
  </div>

  <div class="segmented-control moderation-filters" role="tablist" aria-label="审核内容类型">
    <button
      v-for="item in filters"
      :key="item.id"
      type="button"
      role="tab"
      :aria-selected="filter === item.id"
      :class="{ active: filter === item.id }"
      @click="selectFilter(item.id)"
    >
      {{ item.label }} <span class="filter-count">{{ counts[item.id] }}</span>
    </button>
  </div>

  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="notice" class="success" role="status">{{ notice }}</p>

  <div class="moderation-layout">
    <section class="moderation-list" aria-label="待审核列表">
      <p v-if="loading && queue.length === 0" class="muted">正在加载审核队列…</p>
      <p v-else-if="!loading && visibleItems.length === 0" class="muted moderation-empty">
        {{ filter === "all" ? "当前没有待审核内容。" : `当前没有待审核的${filters.find((item) => item.id === filter)?.label}。` }}
      </p>
      <article
        v-for="item in visibleItems"
        :key="itemKey(item)"
        class="moderation-item"
        :class="{ selected: itemKey(item) === selectedId }"
      >
        <button class="moderation-item-select" type="button" @click="selectedId = itemKey(item)">
          <span class="moderation-item-top">
            <span class="tag moderation-type">{{ labels[item.targetType] }}</span>
            <time class="muted">{{ formatDate(item.createdAt) }}</time>
          </span>
          <strong>{{ item.title || item.questionTitle || `待审核${labels[item.targetType]}` }}</strong>
          <span class="moderation-snippet">{{ item.snippet || "（无摘要）" }}</span>
          <span class="muted">{{ item.authorNickname || "匿名用户" }}</span>
        </button>
      </article>
    </section>

    <section v-if="selected" class="moderation-detail" aria-label="审核详情">
      <div class="moderation-detail-head">
        <div>
          <span class="tag moderation-type">{{ labels[selected.targetType] }}</span>
          <span class="tag">待审核</span>
        </div>
        <time class="muted">{{ formatDate(selected.createdAt) }}</time>
      </div>
      <h3>{{ selected.title || selected.questionTitle || `待审核${labels[selected.targetType]}` }}</h3>
      <p v-if="selected.questionId && selected.targetType !== 'question'" class="muted">
        所属问题：{{ selected.questionTitle || `问题 #${selected.questionId}` }}
      </p>
      <p class="muted">提交者：{{ selected.authorNickname || "匿名用户" }}</p>
      <div class="moderation-content">
        <h4>{{ selected.targetType === "question" || selected.targetType === "public_post" ? "问题内容" : labels[selected.targetType] + "内容" }}</h4>
        <p class="question-body">{{ selected.body || selected.snippet || "（没有正文）" }}</p>
      </div>
      <div v-if="selected.hrAnswer" class="moderation-content">
        <h4>HR 回答</h4>
        <p class="question-body">{{ selected.hrAnswer }}</p>
      </div>

      <div class="field moderation-note">
        <label for="moderation-note">审核备注</label>
        <textarea id="moderation-note" v-model="note" rows="3" maxlength="500" placeholder="可选，供审核记录留档" />
      </div>
      <div class="moderation-actions">
        <button class="primary" type="button" :disabled="acting" @click="decide('approve')">
          {{ acting ? "处理中…" : "通过" }}
        </button>
        <button type="button" :disabled="acting" @click="decide('reject')">驳回</button>
        <button class="danger" type="button" :disabled="acting" @click="decide('remove')">下架</button>
      </div>
    </section>
  </div>
</template>
