<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { api, type QuestionDetail } from "../api";
import { useUserStore } from "@/stores/user";
import { addViewHistory } from "@/utils/history";

const route = useRoute();
const router = useRouter();
const { user: me } = storeToRefs(useUserStore());
const detail = ref<QuestionDetail | null>(null);
const error = ref("");
const answerError = ref("");
const answerNotice = ref("");
const answerBody = ref("");
const followupBody = ref("");
const answerSubmitting = ref(false);
const answerMode = ref<"question" | "followup">("question");
const selectedFollowupId = ref<number | null>(null);
const answerFormRef = ref<HTMLElement | null>(null);

const questionId = Number(route.params.id);
const openedFromHr = computed(() => route.query.from === "hr");
const hrFollowups = computed(() => detail.value?.followups.filter(
  (followup) => followup.senderRole === "student" && followup.status === "published",
) ?? []);
const mainAnswers = computed(() => detail.value?.answers.filter((answer) => answer.followupId == null) ?? []);

function followupOptionLabel(body: string) {
  const characters = Array.from(body.trim());
  return characters.length > 36 ? `${characters.slice(0, 36).join("")}…` : body;
}

function followupAnswers(followupId: number) {
  return detail.value?.answers.filter((answer) => answer.followupId === followupId) ?? [];
}

function setAnswerMode(mode: "question" | "followup") {
  if (answerMode.value !== mode) answerBody.value = "";
  answerError.value = "";
  answerMode.value = mode;
}

async function selectFollowup(followupId: number) {
  if (selectedFollowupId.value !== followupId) answerBody.value = "";
  answerError.value = "";
  answerMode.value = "followup";
  selectedFollowupId.value = followupId;
  await nextTick();
  answerFormRef.value?.scrollIntoView({ behavior: "smooth", block: "center" });
}

function changeFollowupTarget(event: Event) {
  const target = event.target as HTMLSelectElement;
  if (selectedFollowupId.value !== Number(target.value)) answerBody.value = "";
  answerError.value = "";
  selectedFollowupId.value = Number(target.value);
}

function returnToPreviousPage() {
  const previousRoute = window.history.state?.back;
  if (openedFromHr.value && typeof previousRoute === "string" && previousRoute.startsWith("/hr")) {
    router.back();
    return;
  }
  void router.push(openedFromHr.value ? "/hr" : "/");
}

async function load() {
  try {
    detail.value = await api.getQuestion(questionId);
    if (!hrFollowups.value.some((followup) => followup.id === selectedFollowupId.value)) {
      selectedFollowupId.value = hrFollowups.value.at(-1)?.id ?? null;
    }
    // 记录浏览历史（本地存储，后端接口暂未实现）
    addViewHistory({ id: detail.value.question.id, title: detail.value.question.title });  } catch (e: any) {
    error.value = e.message;
  }
}

async function submitAnswer() {
  if (answerSubmitting.value) return;
  answerError.value = "";
  answerNotice.value = "";
  if (!answerBody.value.trim()) {
    answerError.value = answerMode.value === "followup" ? "请先填写追问回复内容。" : "请先填写回答内容。";
    return;
  }
  if (answerMode.value === "followup" && selectedFollowupId.value == null) {
    answerError.value = "请先选择要回复的追问。";
    return;
  }
  answerSubmitting.value = true;
  try {
    let submittedAnswer;
    if (answerMode.value === "followup") {
      submittedAnswer = await api.answerFollowup(questionId, selectedFollowupId.value!, answerBody.value.trim());
    } else {
      submittedAnswer = await api.answer(questionId, answerBody.value.trim());
    }
    answerBody.value = "";
    await load();
    answerNotice.value = submittedAnswer.status === "published"
      ? "回复已发布。"
      : "回复已提交，正在等待审核。";
  } catch (e: any) {
    answerError.value = e.message;
  } finally {
    answerSubmitting.value = false;
  }
}

async function submitFollowup() {
  if (!followupBody.value.trim()) return;
  try {
    await api.followup(questionId, followupBody.value.trim());
    followupBody.value = "";
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

async function markUseful(answerId: number) {
  try {
    await api.interact("answer", answerId, "useful");
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

const reviewStatus: Record<string, string> = {
  pending_human: "待审核",
  rejected: "审核未通过",
};

onMounted(load);
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>

  <div v-if="detail" class="card">
    <div class="between">
      <h2 style="margin: 0">{{ detail.question.title }}</h2>
      <button type="button" class="link muted" @click="returnToPreviousPage">
        {{ openedFromHr ? "返回 HR 工作台" : "返回列表" }}
      </button>
    </div>
    <div class="row" style="margin: 8px 0">
      <span class="tag">公开</span>
      <span class="tag">{{ reviewStatus[detail.question.status] ?? detail.question.status }}</span>
      <span v-for="t in detail.question.tags" :key="t" class="tag">{{ t }}</span>
      <span class="muted">{{ detail.question.author.nickname }}</span>
    </div>
    <p class="question-body">{{ detail.question.body }}</p>
  </div>

  <div v-if="detail" class="card">
    <h3>主问题回答（{{ mainAnswers.length }}）</h3>
    <p v-if="mainAnswers.length === 0" class="muted">还没有回答主问题。</p>
    <div v-for="a in mainAnswers" :key="a.id" class="answer">
      <div class="answer-head">
        <span>{{ a.author.nickname }}</span>
        <span>·</span>
        <span>{{ a.lengthType === "one_liner" ? "一句话建议" : "详细经验" }}</span>
      </div>
      <p class="question-body">{{ a.body }}</p>
      <span v-if="a.status !== 'published'" class="tag">{{ reviewStatus[a.status] }}</span>
      <button v-if="me && a.status === 'published'" @click="markUseful(a.id)">有用（{{ a.usefulCount }}）</button>
    </div>
  </div>

  <div v-if="detail" class="card">
    <h3>追问（{{ detail.followups.length }}）</h3>
    <p v-if="detail.followups.length === 0" class="muted">暂无追问。</p>
    <div v-for="f in detail.followups" :key="f.id" class="followup-thread">
      <div class="followup">
        <strong>{{ f.senderRole === "hr" ? "HR" : "学生" }}：</strong>{{ f.body }}
        <span v-if="f.status !== 'published'" class="tag">{{ reviewStatus[f.status] }}</span>
        <button
          v-if="me?.role === 'hr' && detail.question.scope === 'public' && f.senderRole === 'student' && f.status === 'published'"
          type="button"
          class="link muted"
          @click="selectFollowup(f.id)"
        >
          回答这条追问
        </button>
      </div>
      <div v-for="reply in followupAnswers(f.id)" :key="reply.id" class="followup-answer">
        <div class="answer-head">
          <span>HR 回复</span>
          <span>·</span>
          <span>{{ reply.lengthType === "one_liner" ? "一句话建议" : "详细经验" }}</span>
        </div>
        <p class="question-body">{{ reply.body }}</p>
        <span v-if="reply.status !== 'published'" class="tag">{{ reviewStatus[reply.status] }}</span>
        <button v-if="me && reply.status === 'published'" @click="markUseful(reply.id)">
          有用（{{ reply.usefulCount }}）
        </button>
      </div>
    </div>
    <template v-if="me?.role === 'student' && detail.question.scope === 'public'">
      <div class="field" style="margin-top: 12px">
        <textarea v-model="followupBody" placeholder="继续追问…" />
      </div>
      <button class="primary" @click="submitFollowup">发送追问</button>
    </template>
    <p v-else-if="!me && detail.question.scope === 'public'" class="muted">
      <router-link :to="{ path: '/login', query: { redirect: $route.fullPath } }">登录</router-link>
      或 <router-link to="/register">注册</router-link> 后可追问
    </p>
  </div>

  <div v-if="detail && me?.role === 'hr' && detail.question.scope === 'public'" ref="answerFormRef" class="card">
    <h3>以 HR 身份回答</h3>
    <div class="answer-modes" role="group" aria-label="选择回答对象">
      <button
        type="button"
        class="answer-mode-button"
        :class="{ active: answerMode === 'question' }"
        :aria-pressed="answerMode === 'question'"
        @click="setAnswerMode('question')"
      >
        回答主问题
      </button>
      <button
        v-if="hrFollowups.length > 0"
        type="button"
        class="answer-mode-button"
        :class="{ active: answerMode === 'followup' }"
        :aria-pressed="answerMode === 'followup'"
        @click="setAnswerMode('followup')"
      >
        回复追问
      </button>
    </div>
    <label v-if="answerMode === 'followup'" class="field followup-target">
      <span>选择要回复的学生追问</span>
      <select :value="selectedFollowupId ?? undefined" aria-label="选择要回复的追问" @change="changeFollowupTarget">
        <option v-for="followup in hrFollowups" :key="followup.id" :value="followup.id" :title="followup.body">
          {{ followupOptionLabel(followup.body) }}
        </option>
      </select>
    </label>
    <div class="field">
      <textarea
        v-model="answerBody"
        :placeholder="answerMode === 'question' ? '写下针对主问题的经验或建议…' : '写下针对这条追问的回答…'"
      />
    </div>
    <p v-if="answerError" class="answer-error" role="alert">{{ answerError }}</p>
    <p v-if="answerNotice" class="answer-notice" role="status">{{ answerNotice }}</p>
    <button
      type="button"
      class="primary"
      :disabled="answerSubmitting || (answerMode === 'followup' && selectedFollowupId == null)"
      @click="submitAnswer"
    >
      {{ answerSubmitting ? '提交中…' : answerMode === 'question' ? '提交主问题回答' : '提交追问回复' }}
    </button>
  </div>
</template>

<style scoped>
.answer-modes {
  display: inline-flex;
  gap: 2px;
  margin: 0 0 14px;
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  padding: 3px;
  background: #f3f5f7;
}

.answer-mode-button {
  min-height: 34px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  padding: 0 11px;
  color: var(--color-text-regular);
  font: inherit;
  cursor: pointer;
}

.answer-mode-button.active {
  background: #fff;
  color: var(--color-text-primary);
  box-shadow: 0 1px 2px #1118271a;
}

.followup-thread + .followup-thread {
  margin-top: 18px;
}

.followup-answer {
  margin: 10px 0 0 14px;
  border-left: 2px solid #9bc4ac;
  padding: 4px 0 4px 12px;
}

.followup-target {
  margin: 0 0 12px;
}

.followup-target select {
  width: 100%;
  min-height: 40px;
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  background: #fff;
  padding: 8px 10px;
  color: var(--color-text-primary);
  font: inherit;
}

.answer-notice {
  margin: 0 0 10px;
  color: #356a50;
  font-size: 14px;
}

.answer-error {
  margin: 0 0 10px;
  color: var(--color-danger);
  font-size: 14px;
}
</style>
