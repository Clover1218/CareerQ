<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  api,
  type HrAnsweredQuestion,
  type HrAnsweredPublicQuestion,
  type PageResult,
  type Question,
} from "@/api";

defineOptions({ name: "HrWorkspaceView" });

type WorkTab = "pending" | "answeredPublic" | "answeredMine";

interface WorkItem {
  id: number;
  title: string;
  body: string;
  tags: string[];
  answerCount: number;
  hasPendingAnswer?: boolean;
  followupCount: number;
  createdAt: string;
  myAnswer?: HrAnsweredQuestion["myAnswer"];
}

const PAGE_SIZE = 5;
const router = useRouter();
const activeTab = ref<WorkTab>("pending");
const items = ref<WorkItem[]>([]);
const loading = ref(false);
const error = ref("");
const nextCursor = ref<number | null>(null);
const cursorHistory = ref<(number | null)[]>([null]);
const pageNumber = ref(1);
const returnScrollY = ref<number | null>(null);
let requestSequence = 0;
let activatedBefore = false;
const emptyTitle = computed(() => {
  if (activeTab.value === "pending") return "暂时没有待回答的问题";
  if (activeTab.value === "answeredPublic") return "还没有 HR 回答过的公开问题";
  if (activeTab.value === "answeredMine") return "还没有你的回答记录";
  return "暂时没有公开问题";
});

const tabs: { id: WorkTab; label: string }[] = [
  { id: "pending", label: "待回答" },
  { id: "answeredPublic", label: "已回答公开问题" },
  { id: "answeredMine", label: "我回答过的" },
];

function mapQuestion(question: Question): WorkItem {
  return {
    id: question.id,
    title: question.title,
    body: question.body,
    tags: question.tags,
    answerCount: question.answerCount,
    followupCount: question.followupCount,
    createdAt: question.createdAt,
  };
}

function mapAnsweredQuestion(entry: HrAnsweredQuestion): WorkItem {
  return {
    ...mapQuestion(entry.question),
    myAnswer: entry.myAnswer,
    hasPendingAnswer: entry.myAnswer.status === "pending_human" || entry.myAnswer.status === "pending_machine",
  };
}

function mapAnsweredPublicQuestion(entry: HrAnsweredPublicQuestion): WorkItem {
  return {
    ...mapQuestion(entry.question),
    hasPendingAnswer: entry.hasPendingAnswer,
  };
}

async function fetchPage(cursor: number | null): Promise<PageResult<WorkItem>> {
  if (activeTab.value === "answeredPublic") {
    const page = await api.hrAnsweredPublicQuestions(cursor, PAGE_SIZE);
    return { items: page.items.map(mapAnsweredPublicQuestion), nextCursor: page.nextCursor };
  }
  if (activeTab.value === "answeredMine") {
    const page = await api.hrAnsweredQuestions(cursor, PAGE_SIZE);
    return { items: page.items.map(mapAnsweredQuestion), nextCursor: page.nextCursor };
  }
  const page = await api.hrQuestionPool(cursor, PAGE_SIZE);
  return { items: page.items.map(mapQuestion), nextCursor: page.nextCursor };
}

async function loadPage(cursor: number | null): Promise<boolean> {
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = "";
  try {
    const page = await fetchPage(cursor);
    if (sequence !== requestSequence) return false;
    items.value = page.items;
    nextCursor.value = page.nextCursor;
    return true;
  } catch (cause) {
    if (sequence !== requestSequence) return false;
    error.value = cause instanceof Error ? cause.message : "问题列表加载失败，请稍后重试。";
    return false;
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

function selectTab(tab: WorkTab) {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
}

async function nextPage() {
  if (loading.value || nextCursor.value == null) return;
  const cursor = nextCursor.value;
  if (await loadPage(cursor)) {
    cursorHistory.value.push(cursor);
    pageNumber.value += 1;
  }
}

async function previousPage() {
  if (loading.value || pageNumber.value <= 1) return;
  const previousCursor = cursorHistory.value[cursorHistory.value.length - 2] ?? null;
  if (await loadPage(previousCursor)) {
    cursorHistory.value.pop();
    pageNumber.value -= 1;
  }
}

function openQuestion(item: WorkItem) {
  returnScrollY.value = window.scrollY;
  void router.push({ path: `/questions/${item.id}`, query: { from: "hr" } });
}

function formatTime(value: string) {
  return value.replace("T", " ").slice(0, 16);
}

function answerStatus(status: string) {
  const labels: Record<string, string> = {
    published: "已发布",
    pending_machine: "审核中",
    pending_human: "审核中",
    rejected: "未通过",
    removed: "已下架",
  };
  return labels[status] ?? status;
}

watch(activeTab, () => {
  cursorHistory.value = [null];
  pageNumber.value = 1;
  nextCursor.value = null;
  void loadPage(null);
});

onMounted(() => void loadPage(null));
onActivated(async () => {
  if (!activatedBefore) {
    activatedBefore = true;
    return;
  }
  await loadPage(cursorHistory.value[cursorHistory.value.length - 1] ?? null);
  if (returnScrollY.value !== null) {
    await nextTick();
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    window.scrollTo(0, returnScrollY.value);
    returnScrollY.value = null;
  }
});
</script>

<template>
  <section class="hr-workspace">
    <header class="workspace-heading">
      <div>
        <p class="workspace-kicker">职业问答 · 答主工作区</p>
        <h1>HR 工作台</h1>
        <p class="workspace-description">浏览学生的公开问题，分享经验，并跟进你回答过的问题。</p>
      </div>
      <div class="role-stamp"><span class="role-dot"></span>HR 答主</div>
    </header>

    <nav class="workspace-tabs" aria-label="问题分类">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        class="workspace-tab"
        :class="{ active: activeTab === tab.id }"
        :aria-current="activeTab === tab.id ? 'page' : undefined"
        @click="selectTab(tab.id)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="list-heading">
      <div>
        <h2>{{ tabs.find((tab) => tab.id === activeTab)?.label }}</h2>
        <p v-if="activeTab === 'pending'">优先回答还没有 HR 回应的问题。</p>
        <p v-else-if="activeTab === 'answeredPublic'">任何 HR 回答过的公开问题都会显示在这里。</p>
        <p v-else>查看你提交过回答的问题，以及学生后续追问。</p>
      </div>
      <span class="page-size">每页 {{ PAGE_SIZE }} 条</span>
    </div>

    <p v-if="error" class="workspace-error" role="alert">{{ error }}</p>

    <div v-if="loading && items.length === 0" class="workspace-state" aria-live="polite">
      正在加载问题…
    </div>

    <div v-else-if="!loading && items.length === 0 && !error" class="workspace-state empty-state">
      <span class="empty-mark" aria-hidden="true">✓</span>
      <strong>{{ emptyTitle }}</strong>
      <span>{{ activeTab === 'pending' ? '新的公开问题会出现在这里。' : '有新内容时会显示在这个列表里。' }}</span>
    </div>

    <div v-else class="question-list" :aria-busy="loading">
      <article v-for="item in items" :key="item.id" class="question-row">
        <div class="question-main">
          <div class="question-meta">
            <span class="question-status" :class="item.myAnswer ? 'status-answered' : item.hasPendingAnswer && item.answerCount === 0 ? 'status-review' : item.answerCount === 0 ? 'status-pending' : 'status-active'">
              {{ item.myAnswer ? '我已回答' : item.hasPendingAnswer && item.answerCount === 0 ? '审核中' : item.answerCount === 0 ? '待回答' : item.hasPendingAnswer ? '已有回答 · 另有审核中' : '已有回答' }}
            </span>
            <time>{{ formatTime(item.createdAt) }}</time>
          </div>
          <h3>{{ item.title }}</h3>
          <p class="question-excerpt">{{ item.body }}</p>
          <div class="question-footer">
            <div class="tag-list">
              <span v-for="tag in item.tags.slice(0, 3)" :key="tag" class="question-tag">{{ tag }}</span>
            </div>
            <span>{{ item.answerCount }} 个回答</span>
            <span>{{ item.followupCount }} 条追问</span>
          </div>

          <div v-if="item.myAnswer" class="my-answer-preview">
            <div class="my-answer-heading">
              <strong>{{ item.myAnswer.followupId == null ? '我的回答' : '我对追问的回复' }}</strong>
              <span class="answer-status" :class="`answer-${item.myAnswer.status}`">
                {{ answerStatus(item.myAnswer.status) }}
              </span>
            </div>
            <p>{{ item.myAnswer.body }}</p>
          </div>
        </div>
        <div class="question-action">
          <button type="button" class="open-question" @click="openQuestion(item)">
            {{ item.myAnswer ? item.followupCount > 0 ? '查看追问' : '查看问题' : activeTab === 'pending' && item.answerCount === 0 ? '去回答' : '查看问题' }}
            <span aria-hidden="true">→</span>
          </button>
        </div>
      </article>
    </div>

    <footer class="pagination" aria-label="问题列表翻页">
      <span>第 {{ pageNumber }} 页</span>
      <div class="pagination-actions">
        <button type="button" class="page-button" :disabled="pageNumber === 1 || loading" @click="previousPage">
          上一页
        </button>
        <button type="button" class="page-button" :disabled="nextCursor == null || loading" @click="nextPage">
          下一页
        </button>
      </div>
      <span class="pagination-hint">
        {{ loading ? '加载中…' : nextCursor == null ? '问题已经看完咯' : `本页 ${items.length} 条` }}
      </span>
    </footer>
  </section>
</template>

<style scoped>
.hr-workspace {
  width: min(100% - 32px, 1080px);
  margin: 0 auto;
  padding: 34px 0 64px;
}

.workspace-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 28px;
  border-bottom: 1px solid var(--color-border-default);
}

.workspace-kicker {
  margin: 0 0 8px;
  color: #596579;
  font-size: 13px;
  font-weight: 600;
}

.workspace-heading h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.25;
}

.workspace-description {
  margin: 9px 0 0;
  color: var(--color-text-regular);
  font-size: 15px;
  line-height: 1.6;
}

.role-stamp {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  padding: 8px 11px;
  border: 1px solid #c6ded5;
  border-radius: 6px;
  background: #f1f8f4;
  color: #315f50;
  font-size: 13px;
  font-weight: 600;
}

.role-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #3e8d6d;
}

.workspace-tabs {
  display: flex;
  gap: 26px;
  margin-top: 20px;
  border-bottom: 1px solid var(--color-border-default);
}

.workspace-tab {
  position: relative;
  margin-bottom: -1px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  padding: 12px 1px 13px;
  color: var(--color-text-regular);
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}

.workspace-tab:hover,
.workspace-tab.active {
  color: var(--color-text-primary);
}

.workspace-tab.active {
  border-bottom-color: var(--color-brand);
  font-weight: 650;
}

.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 0 14px;
}

.list-heading h2 {
  margin: 0;
  font-size: 18px;
  line-height: 26px;
}

.list-heading p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 20px;
}

.page-size {
  flex: 0 0 auto;
  color: var(--color-text-muted);
  font-size: 13px;
}

.question-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-border-default);
}

.question-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  padding: 22px 4px;
  border-bottom: 1px solid var(--color-border-default);
}

.question-main {
  min-width: 0;
}

.question-meta,
.question-footer,
.tag-list,
.my-answer-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.question-meta {
  gap: 11px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.question-status,
.answer-status {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  border-radius: 4px;
  padding: 2px 7px;
  font-size: 12px;
  font-weight: 600;
}

.status-pending {
  background: #fff5df;
  color: #805a12;
}

.status-active {
  background: #edf2fa;
  color: #485d7e;
}

.status-review {
  background: #fff5df;
  color: #805a12;
}

.status-answered {
  background: #eaf5ef;
  color: #356a50;
}

.question-row h3 {
  margin: 9px 0 0;
  color: var(--color-text-primary);
  font-size: 18px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.question-excerpt {
  display: -webkit-box;
  overflow: hidden;
  margin: 7px 0 0;
  color: var(--color-text-regular);
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.question-footer {
  gap: 14px;
  margin-top: 12px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.tag-list {
  gap: 6px;
  margin-right: 2px;
}

.question-tag {
  border-radius: 4px;
  background: #f0f2f5;
  padding: 3px 7px;
  color: #586273;
}

.question-action {
  display: flex;
  align-items: flex-start;
  padding-top: 28px;
}

.open-question {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  white-space: nowrap;
  border: 1px solid #cbd7eb;
  border-radius: 5px;
  background: #fff;
  padding: 0 11px;
  color: #2f5ca8;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.open-question:hover {
  border-color: var(--color-brand);
  background: #f6f9ff;
}

.my-answer-preview {
  margin-top: 15px;
  border-left: 3px solid #77a58e;
  background: #f6f9f7;
  padding: 10px 12px;
}

.my-answer-heading {
  justify-content: space-between;
  gap: 12px;
  color: #344b40;
  font-size: 13px;
}

.my-answer-preview p {
  display: -webkit-box;
  overflow: hidden;
  margin: 6px 0 0;
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.answer-published {
  background: #eaf5ef;
  color: #356a50;
}

.answer-pending_machine,
.answer-pending_human {
  background: #fff5df;
  color: #805a12;
}

.answer-rejected,
.answer-removed {
  background: #fbeeee;
  color: #9b4141;
}

.workspace-state {
  display: grid;
  min-height: 190px;
  place-content: center;
  gap: 8px;
  border-top: 1px solid var(--color-border-default);
  color: var(--color-text-muted);
  text-align: center;
  font-size: 14px;
}

.empty-state strong {
  color: var(--color-text-regular);
  font-size: 15px;
}

.empty-mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  justify-self: center;
  border-radius: 50%;
  background: #edf5f0;
  color: #397455;
  font-weight: 700;
}

.workspace-error {
  margin: 12px 0;
  border-left: 3px solid var(--color-danger);
  background: #fff5f5;
  padding: 10px 12px;
  color: #9b3535;
  font-size: 14px;
}

.pagination {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.pagination-actions {
  display: flex;
  gap: 8px;
}

.page-button {
  min-width: 72px;
  min-height: 34px;
  border: 1px solid var(--color-border-default);
  border-radius: 5px;
  background: #fff;
  color: var(--color-text-regular);
  font: inherit;
  cursor: pointer;
}

.page-button:hover:not(:disabled) {
  border-color: #9aabc4;
  background: #f7f9fc;
}

.page-button:disabled {
  cursor: not-allowed;
  color: #b5bac3;
  background: #f5f6f7;
}

.pagination-hint {
  justify-self: end;
  text-align: right;
}

@media (max-width: 640px) {
  .hr-workspace {
    width: min(100% - 24px, 1080px);
    padding-top: 24px;
  }

  .workspace-heading {
    align-items: flex-start;
    padding-bottom: 20px;
  }

  .workspace-heading h1 {
    font-size: 25px;
  }

  .workspace-description {
    max-width: 36ch;
    font-size: 14px;
  }

  .role-stamp {
    padding: 7px 8px;
    font-size: 12px;
  }

  .workspace-tabs {
    gap: 18px;
    overflow-x: auto;
  }

  .workspace-tab {
    flex: 0 0 auto;
    font-size: 14px;
  }

  .question-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
    padding: 17px 2px;
  }

  .question-action {
    justify-content: flex-end;
    padding-top: 0;
  }

  .question-row h3 {
    font-size: 16px;
  }

  .pagination {
    grid-template-columns: 1fr auto;
  }

  .pagination-hint {
    grid-column: 1 / -1;
    justify-self: center;
    text-align: center;
  }
}
</style>
