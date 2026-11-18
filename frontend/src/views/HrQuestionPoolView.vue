<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, type Question } from "../api";

type PoolTab = "unanswered" | "all" | "mine";

const tabs: { id: PoolTab; label: string }[] = [
  { id: "unanswered", label: "待回答" },
  { id: "all", label: "全部公开问题" },
  { id: "mine", label: "我回答过的" },
];
const activeTab = ref<PoolTab>("unanswered");
const items = ref<Question[]>([]);
const cursors = ref<(number | null)[]>([null]);
const page = ref(1);
const nextCursor = ref<number | null>(null);
const loading = ref(false);
const hasLoaded = ref(false);
const error = ref("");

async function loadPage(targetPage: number) {
  if (loading.value || targetPage < 1) return;
  const cursor = cursors.value[targetPage - 1] ?? null;
  if (targetPage > 1 && cursor === null) return;

  loading.value = true;
  error.value = "";
  try {
    const result = await loadTabPage(cursor);
    if (targetPage > 1 && result.items.length === 0) {
      nextCursor.value = null;
      hasLoaded.value = true;
      return;
    }
    items.value = result.items;
    nextCursor.value = result.nextCursor ?? null;
    page.value = targetPage;
    hasLoaded.value = true;
    if (nextCursor.value !== null) {
      cursors.value[targetPage] = nextCursor.value;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "问题池加载失败";
  } finally {
    loading.value = false;
  }
}

function loadTabPage(cursor: number | null) {
  if (activeTab.value === "all") return api.listQuestions(undefined, cursor, 5);
  if (activeTab.value === "mine") return api.hrAnsweredQuestions(cursor, 5);
  return api.hrQuestionPool(cursor, 5);
}

function selectTab(tab: PoolTab) {
  if (loading.value || tab === activeTab.value) return;
  activeTab.value = tab;
  items.value = [];
  cursors.value = [null];
  page.value = 1;
  nextCursor.value = null;
  hasLoaded.value = false;
  void loadPage(1);
}

function nextPage() {
  if (nextCursor.value == null) return;
  void loadPage(page.value + 1);
}

function previousPage() {
  if (page.value > 1) void loadPage(page.value - 1);
}

onMounted(() => loadPage(1));
</script>

<template>
  <div class="between">
    <div>
      <h2>HR 工作台</h2>
      <p class="muted">查看公开问题、继续浏览全部内容，或回顾自己的回答。</p>
    </div>
    <span class="muted">第 {{ page }} 页 · 本页 {{ items.length }} 条</span>
  </div>

  <div class="segmented-control" role="tablist" aria-label="问题视图">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      role="tab"
      :aria-selected="activeTab === tab.id"
      :class="{ active: activeTab === tab.id }"
      :disabled="loading"
      @click="selectTab(tab.id)"
    >
      {{ tab.label }}
    </button>
  </div>

  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="loading && items.length === 0" class="muted">正在加载问题…</p>
  <p v-else-if="hasLoaded && !error && items.length === 0" class="muted">
    {{ activeTab === "unanswered" ? "目前没有待回答的问题。" : activeTab === "mine" ? "你还没有已发布的回答。" : "目前没有公开问题。" }}
  </p>

  <article v-for="question in items" :key="question.id" class="card">
    <div class="between">
      <router-link :to="`/questions/${question.id}`" class="question-title">
        {{ question.title }}
      </router-link>
      <span class="muted">{{ question.answerCount }} 个回答</span>
    </div>
    <p class="question-body muted">{{ question.body }}</p>
    <div class="row">
      <span v-for="tag in question.tags" :key="tag" class="tag">{{ tag }}</span>
      <span class="muted" style="margin-left: auto">{{ question.createdAt }}</span>
      <router-link
        v-if="activeTab !== 'mine' && question.answerCount === 0"
        class="btn primary"
        :to="`/hr/questions/${question.id}/answer`"
      >
        回答
      </router-link>
      <router-link v-else class="btn" :to="`/questions/${question.id}`">查看</router-link>
    </div>
  </article>

  <div v-if="page > 1 || nextCursor !== null" class="pagination">
    <button v-if="page > 1" :disabled="loading" @click="previousPage">上一页</button>
    <span class="muted">第 {{ page }} 页</span>
    <button v-if="nextCursor !== null" :disabled="loading" @click="nextPage">下一页</button>
  </div>
  <p v-if="hasLoaded && !loading && !error && items.length > 0 && nextCursor === null" class="muted pagination-end">
    问题已经看完咯
  </p>
</template>
