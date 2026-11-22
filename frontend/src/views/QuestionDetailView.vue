<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { api, type Followup, type QuestionDetail } from "../api";
import { useUserStore } from "@/stores/user";
import { addViewHistory } from "@/utils/history";
import FollowupDiscussion from "@/components/FollowupDiscussion.vue";
import { buildAnswerThreads, detailThreadItems, type AnswerThread } from "@/utils/answerThreads";
import { canWithdrawAnswer, useAnswerWithdrawal } from "@/composables/useAnswerWithdrawal";
import { removedAnswerText } from "@/utils/answerRemoval";

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
const followupFormRef = ref<HTMLElement | null>(null);
const followupSubmitting = ref(false);
const followupError = ref("");
const highlightedFollowupId = ref<number | null>(null);
let appliedFollowupLink: string | null = null;

const questionId = Number(route.params.id);
const openedFromHr = computed(() => route.query.from === "hr");
const openedFromHrProfile = computed(() => route.query.from === "hr-profile");
const openedFromHrMessages = computed(() => route.query.from === "hr-messages");
const highlightedAnswerId = ref<number | null>(null);
const hrFollowups = computed(() => detail.value?.followups.filter(
  canReplyFollowup,
) ?? []);
const pendingHrFollowups = computed(() => hrFollowups.value.filter(
  (followup) => followupReplyState(followup) === "pending",
));
const selectedFollowup = computed(() => hrFollowups.value.find(
  (followup) => followup.id === selectedFollowupId.value,
));
const mainAnswers = computed(() => detail.value?.answers.filter((answer) => answer.followupId == null) ?? []);
const conversations = computed(() => detail.value ? buildAnswerThreads(detailThreadItems(detail.value)) : []);
const selectedRootAnswerId = ref<number | null>(null);
const selectedRootAnswer = computed(() => mainAnswers.value.find(
  (answer) => answer.id === selectedRootAnswerId.value && answer.status === "published",
));
const canStudentFollowup = computed(() => me.value?.role === "student"
  && me.value.id === detail.value?.question.author.id && detail.value.question.status === "published");
const openFollowupKeys = ref<string[]>([]);
const { requestWithdrawal, withdrawingAnswerId } = useAnswerWithdrawal(async () => {
  await load();
  answerNotice.value = "这条内容已撤销。";
});

function threadFollowups(conversation: AnswerThread) {
  const ids = new Set(conversation.messages.filter((item) => item.kind === "followup").map((item) => item.id));
  return detail.value?.followups.filter((followup) => ids.has(followup.id)) ?? [];
}

function toggleFollowups(key: string) {
  openFollowupKeys.value = openFollowupKeys.value.includes(key)
    ? openFollowupKeys.value.filter((item) => item !== key) : [...openFollowupKeys.value, key];
}

function openFollowupThread(followupId: number) {
  const thread = conversations.value.find((item) => item.messages.some((message) => message.kind === "followup" && message.id === followupId));
  if (thread && !openFollowupKeys.value.includes(thread.key)) openFollowupKeys.value.push(thread.key);
}

async function askAboutAnswer(answerId: number) {
  if (!canStudentFollowup.value) return;
  if (selectedRootAnswerId.value !== answerId && followupBody.value.trim()) {
    followupError.value = "请先发送或清空当前草稿，再切换要追问的回答。";
    followupFormRef.value?.scrollIntoView({ behavior: "smooth", block: "center" });
    return;
  }
  selectedRootAnswerId.value = answerId;
  followupError.value = "";
  await nextTick();
  followupFormRef.value?.scrollIntoView({ behavior: "smooth", block: "center" });
}

function canReplyFollowup(followup: Followup) {
  return me.value?.role === "hr"
    && detail.value?.question.scope === "public"
    && detail.value.question.status === "published"
    && followup.senderRole === "student"
    && followup.status === "published"
    && followup.hrId === me.value.id;
}

function followupReplyState(followup: Followup): "published" | "reviewing" | "pending" {
  const replies = detail.value?.answers.filter(
    (answer) => answer.followupId === followup.id && answer.author.id === me.value?.id,
  ) ?? [];
  if (replies.some((reply) => reply.status === "published")) return "published";
  if (replies.some((reply) => reply.status === "pending_machine" || reply.status === "pending_human")) return "reviewing";
  return "pending";
}

watch([hrFollowups, pendingHrFollowups], ([followups, pending]) => {
  if (followups.some((followup) => followup.id === selectedFollowupId.value)) return;
  selectedFollowupId.value = pending[0]?.id ?? followups.at(-1)?.id ?? null;
  if (answerMode.value === "followup") {
    answerBody.value = "";
    answerError.value = "";
    if (followups.length === 0) answerMode.value = "question";
  }
});

watch([hrFollowups, () => route.query.followup], async ([followups, requested]) => {
  if (typeof requested !== "string" || requested === appliedFollowupLink
      || !detail.value || me.value?.role !== "hr") return;
  const id = Number(requested);
  if (!Number.isSafeInteger(id) || id <= 0) return;
  appliedFollowupLink = requested;
  if (openedFromHrMessages.value && followups.some(followup => followup.id === id)) {
    highlightedFollowupId.value = id;
    openFollowupThread(id);
    if (followups.some(followup => followup.id === id && followupReplyState(followup) === "pending")) {
      answerMode.value = "followup";
      selectedFollowupId.value = id;
    }
    await nextTick();
    document.getElementById(`followup-${id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
    return;
  }
  if (followups.some((followup) => followup.id === id && followupReplyState(followup) === "pending")) {
    await selectFollowup(id, "followup");
  } else {
    answerNotice.value = "这条追问已处理或当前不可回复，请查看其他发给你的追问。";
  }
});

// HR 个人记录只定位对应内容，不自动进入“回复追问”模式。
watch([detail, () => route.query.answer], async ([current, requested]) => {
  if ((!openedFromHrProfile.value && !openedFromHrMessages.value) || me.value?.role !== "hr" || !current || typeof requested !== "string") return;
  const id = Number(requested);
  if (!Number.isSafeInteger(id) || id <= 0) return;
  const answer = current.answers.find(item => item.id === id && item.author.id === me.value?.id);
  if (!answer) {
    answerNotice.value = "这条回答已移除，详情仅展示仍然可见的讨论。";
    return;
  }
  if (answer.followupId != null) openFollowupThread(answer.followupId);
  highlightedAnswerId.value = id;
  await nextTick();
  document.getElementById(answer.followupId == null ? `answer-${id}` : `reply-${id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
}, { flush: "post" });

function followupOptionLabel(body: string) {
  const characters = Array.from(body.trim());
  return characters.length > 36 ? `${characters.slice(0, 36).join("")}…` : body;
}

function followupAnswers(followupId: number) {
  return detail.value?.answers.filter((answer) => answer.followupId === followupId) ?? [];
}

function followupTargetName(followup: Followup) {
  const author = detail.value?.answers.find((answer) => answer.id === followup.rootAnswerId)?.author;
  const nickname = author?.nickname.trim();
  return nickname ? `HR · ${nickname}` : "指定 HR（昵称暂不可用）";
}

function setAnswerMode(mode: "question" | "followup") {
  if (mode === "followup" && hrFollowups.value.length === 0) return;
  if (answerMode.value !== mode) answerBody.value = "";
  answerError.value = "";
  answerMode.value = mode;
}

async function selectFollowup(followupId: number, focus: "form" | "followup" = "form") {
  if (!hrFollowups.value.some((followup) => followup.id === followupId)) return;
  if (selectedFollowupId.value !== followupId) answerBody.value = "";
  answerError.value = "";
  answerMode.value = "followup";
  selectedFollowupId.value = followupId;
  highlightedFollowupId.value = followupId;
  openFollowupThread(followupId);
  await nextTick();
  const target = focus === "followup" ? document.getElementById(`followup-${followupId}`) : answerFormRef.value;
  target?.scrollIntoView({ behavior: "smooth", block: focus === "followup" ? "start" : "center" });
}

function changeFollowupTarget(event: Event) {
  const target = event.target as HTMLSelectElement;
  const followupId = Number(target.value);
  if (!hrFollowups.value.some((followup) => followup.id === followupId)) return;
  if (selectedFollowupId.value !== followupId) answerBody.value = "";
  answerError.value = "";
  selectedFollowupId.value = followupId;
}

function returnToPreviousPage() {
  const previousRoute = window.history.state?.back;
  if (openedFromHrMessages.value) {
    if (typeof previousRoute === "string" && previousRoute.startsWith("/hr/messages")) router.back();
    else void router.push("/hr/messages");
    return;
  }
  if (openedFromHrProfile.value) {
    if (typeof previousRoute === "string" && previousRoute.startsWith("/hr/profile")) router.back();
    else void router.push("/hr/profile");
    return;
  }
  if (openedFromHr.value && typeof previousRoute === "string" && previousRoute.startsWith("/hr")) {
    router.back();
    return;
  }
  void router.push(openedFromHr.value ? "/hr" : "/");
}

async function load() {
  try {
    detail.value = await api.getQuestion(questionId);
    if (selectedRootAnswerId.value != null && !selectedRootAnswer.value) {
      selectedRootAnswerId.value = null;
      followupError.value = "原回答当前不可追问，请重新选择已发布的回答。";
    }
    // 记录浏览历史（本地存储，后端接口暂未实现）
    addViewHistory({ id: detail.value.question.id, title: detail.value.question.title });
  } catch (e: any) {
    error.value = openedFromHrMessages.value ? "相关内容已不可查看，可能已被撤销或下架。" : e.message;
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
  if (answerMode.value === "followup" && !selectedFollowup.value) {
    answerError.value = "请选择发给你的、可以回复的学生追问。";
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
    if (submittedAnswer.status === "rejected") {
      answerError.value = "回答未通过审核，请修改内容后重新提交。";
    } else {
      answerBody.value = "";
      answerNotice.value = submittedAnswer.status === "published" ? "回复已发布。" : "回复已提交，正在等待审核。";
    }
    await load();
  } catch (e: any) {
    answerError.value = e.message;
  } finally {
    answerSubmitting.value = false;
  }
}

async function submitFollowup() {
  if (followupSubmitting.value) return;
  followupError.value = "";
  if (!followupBody.value.trim()) { followupError.value = "请先填写追问内容。"; return; }
  const target = selectedRootAnswer.value;
  if (!canStudentFollowup.value || !target) {
    followupError.value = "请先选择一条已发布的主回答。";
    return;
  }
  followupSubmitting.value = true;
  try {
    const followup = await api.followup(questionId, followupBody.value.trim(), target.author.id, target.id);
    followupBody.value = "";
    await load();
    openFollowupThread(followup.id);
  } catch (e: any) {
    followupError.value = e.message;
  } finally {
    followupSubmitting.value = false;
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
  pending_machine: "待审核",
  pending_human: "待审核",
  rejected: "审核未通过",
};
const authorReviewNotice = computed(() => {
  if (!detail.value || !me.value || detail.value.question.author.id !== me.value.id) return "";
  const status = detail.value.question.status;
  if (status === "pending_machine" || status === "pending_human") {
    return "你的问题正在审核中，审核通过后才会出现在公开问答；可在「我的问答」查看进度。";
  }
  if (status === "rejected" || status === "public_rejected") {
    return "你的问题未通过审核，不会公开展示；可修改内容后重新提问。";
  }
  if (status === "public_pending") {
    return "公开稿正在审核中，通过后会出现在工单公开问答。";
  }
  return "";
});

onMounted(load);
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <button v-if="error && openedFromHrMessages" type="button" class="link muted" @click="returnToPreviousPage">返回消息中心</button>

  <div v-if="detail" class="card">
    <div class="between">
      <h2 style="margin: 0">{{ detail.question.title }}</h2>
      <button type="button" class="link muted" @click="returnToPreviousPage">
        {{ openedFromHrMessages ? "返回消息中心" : openedFromHrProfile ? "返回个人中心" : openedFromHr ? "返回 HR 工作台" : "返回列表" }}
      </button>
    </div>
    <div class="row" style="margin: 8px 0">
      <span class="tag">公开</span>
      <span class="tag">{{ reviewStatus[detail.question.status] ?? detail.question.status }}</span>
      <span v-for="t in detail.question.tags" :key="t" class="tag">{{ t }}</span>
      <span class="muted">{{ detail.question.author.nickname }}</span>
    </div>
    <p class="question-body">{{ detail.question.body }}</p>
    <p v-if="authorReviewNotice" class="review-notice">{{ authorReviewNotice }}</p>
  </div>

  <section v-if="detail" class="answer-floors">
    <h3>主问题回答（{{ mainAnswers.filter((answer) => answer.status === 'published').length }}）</h3>
    <p v-if="conversations.length === 0" class="muted">还没有回答主问题。</p>
    <article v-for="conversation in conversations" :id="`answer-${conversation.rootAnswerId}`" :key="conversation.key" class="card answer-floor" :class="{ 'is-highlighted': highlightedAnswerId === conversation.rootAnswerId }">
      <template v-if="conversation.rootAnswer">
        <div class="answer-head">
          <strong>HR · {{ conversation.nickname }}</strong>
          <span v-if="me?.id === conversation.hrId">（我）</span>
          <time>{{ conversation.rootAnswer.createdAt.replace('T', ' ').slice(0, 16) }}</time>
        </div>
        <p v-if="conversation.rootAnswer.status === 'removed'" class="muted">{{ removedAnswerText(conversation.rootAnswer) }}相关追问与回复保留。</p>
        <p v-else class="question-body">{{ conversation.rootAnswer.body }}</p>
        <span v-if="reviewStatus[conversation.rootAnswer.status]" class="tag">{{ reviewStatus[conversation.rootAnswer.status] }}</span>
        <div class="floor-actions">
          <button v-if="me && conversation.rootAnswer.status === 'published'" @click="markUseful(conversation.rootAnswer.id)">有用（{{ detail.answers.find((answer) => answer.id === conversation.rootAnswerId)?.usefulCount ?? 0 }}）</button>
          <button v-if="canStudentFollowup && conversation.rootAnswer.status === 'published'" type="button" @click="askAboutAnswer(conversation.rootAnswer.id)">追问这条回答</button>
          <button v-if="me?.role === 'hr' && me.id === conversation.rootAnswer.author.id && canWithdrawAnswer(conversation.rootAnswer)" type="button" class="withdraw-answer" :disabled="withdrawingAnswerId !== null" @click="requestWithdrawal(conversation.rootAnswer)">
            {{ withdrawingAnswerId === conversation.rootAnswer.id ? '撤销中…' : '撤销这条回答' }}
          </button>
        </div>
      </template>
      <button type="button" class="link followup-toggle" :aria-expanded="openFollowupKeys.includes(conversation.key)" @click="toggleFollowups(conversation.key)">
        {{ openFollowupKeys.includes(conversation.key) ? '收起追问' : '展开追问' }}（{{ threadFollowups(conversation).length }}）
      </button>
      <div v-if="openFollowupKeys.includes(conversation.key)" class="floor-followups">
        <p v-if="threadFollowups(conversation).length === 0" class="muted">这条回答暂时没有追问。</p>
        <FollowupDiscussion
          v-for="followup in threadFollowups(conversation)" :key="followup.id"
          :followup="followup" :replies="followupAnswers(followup.id)" :viewer="me"
          :target-name="followupTargetName(followup)" :can-reply="canReplyFollowup(followup)"
          :reply-state="followupReplyState(followup)" :highlighted="highlightedFollowupId === followup.id"
          :withdrawing-answer-id="withdrawingAnswerId"
          :highlighted-answer-id="highlightedAnswerId"
          @reply="selectFollowup" @useful="markUseful" @withdraw="requestWithdrawal"
        />
      </div>
    </article>
  </section>

  <div v-if="detail && canStudentFollowup" ref="followupFormRef" class="card">
    <h3>针对一条回答追问</h3>
    <template v-if="selectedRootAnswer">
      <p class="muted">正在追问 HR · {{ selectedRootAnswer.author.nickname }}的这条回答：</p>
      <blockquote class="selected-answer-preview">{{ followupOptionLabel(selectedRootAnswer.body) }}</blockquote>
      <div class="field"><textarea v-model="followupBody" placeholder="针对这条回答继续追问…" /></div>
      <button class="primary" :disabled="followupSubmitting" @click="submitFollowup">{{ followupSubmitting ? '发送中…' : '发送追问' }}</button>
    </template>
    <p v-else class="muted">点击上方某条回答下的“追问这条回答”，选择追问对象。</p>
    <p v-if="followupError" class="answer-error" role="alert">{{ followupError }}</p>
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
      :disabled="answerSubmitting || (answerMode === 'followup' && !selectedFollowup)"
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

.answer-floor { scroll-margin-top: 80px; }
.answer-floor.is-highlighted { outline: 2px solid #b9cdf0; outline-offset: 3px; background: #f8faff; }
.floor-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.withdraw-answer { color: #9b4141; border-color: #e4c9c9; }
.followup-toggle { margin-top: 16px; color: var(--color-brand); }
.floor-followups { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--color-border-default); }
.selected-answer-preview { margin: 10px 0; padding: 10px 12px; border-left: 3px solid #9bb9e5; background: #f6f9ff; overflow-wrap: anywhere; }

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
