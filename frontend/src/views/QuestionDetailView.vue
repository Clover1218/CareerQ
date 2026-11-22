<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useDialog, useMessage } from "naive-ui";
import { api, type Followup, type QuestionDetail } from "../api";
import { useUserStore } from "@/stores/user";
import { addViewHistory } from "@/utils/history";
import FollowupDiscussion from "@/components/FollowupDiscussion.vue";
import ReportDialog from "@/components/ReportDialog.vue";
import { buildAnswerThreads, detailThreadItems, type AnswerThread } from "@/utils/answerThreads";
import { canWithdrawAnswer, useAnswerWithdrawal } from "@/composables/useAnswerWithdrawal";
import { removedAnswerText } from "@/utils/answerRemoval";

const route = useRoute();
const router = useRouter();
const dialog = useDialog();
const message = useMessage();
const userStore = useUserStore();
const { user: me } = storeToRefs(userStore);
let active = true;
onBeforeUnmount(() => { active = false; });
const detail = ref<QuestionDetail | null>(null);
const loading = ref(true);
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
const followupNotice = ref("");
const withdrawingFollowupId = ref<number | null>(null);
const highlightedFollowupId = ref<number | null>(null);
let appliedFollowupLink: string | null = null;

const questionId = Number(route.params.id);
const openedFromFeed = computed(() => route.query.from === "feed");
const openedFromHr = computed(() => route.query.from === "hr");
const openedFromHrProfile = computed(() => route.query.from === "hr-profile");
const openedFromHrMessages = computed(() => route.query.from === "hr-messages");
const studentReturnTo = computed(() => {
  if (me.value?.role !== "student" || typeof route.query.returnTo !== "string") return null;
  const value = route.query.returnTo;
  if (route.query.from === "student-messages" && (value === "/messages" || value.startsWith("/messages?"))) return value;
  if (route.query.from === "student-favorites" && value.startsWith("/me?tab=favorites")) return value;
  return null;
});
const studentReturnLabel = computed(() => route.query.from === "student-messages" ? "返回消息中心" : "返回我的收藏");
const returnLabel = computed(() => {
  if (studentReturnTo.value) return studentReturnLabel.value;
  if (openedFromHrMessages.value) return "返回消息中心";
  if (openedFromHrProfile.value) return "返回个人中心";
  if (openedFromHr.value) return "返回 HR 工作台";
  return "返回列表";
});
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
const likingAnswerId = ref<number | null>(null);
const reportOpen = ref(false);
const reportAnswerId = ref(0);
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
  const studentEntry = studentReturnTo.value !== null;
  if ((!openedFromHrProfile.value && !openedFromHrMessages.value && !studentEntry) || (!studentEntry && me.value?.role !== "hr") || !current || typeof requested !== "string") return;
  const id = Number(requested);
  if (!Number.isSafeInteger(id) || id <= 0) return;
  const answer = current.answers.find(item => item.id === id && (studentEntry ? item.status === "published" : item.author.id === me.value?.id));
  if (!answer) {
    answerNotice.value = "这条回答已移除，详情仅展示仍然可见的讨论。";
    return;
  }
  if (answer.followupId != null) openFollowupThread(answer.followupId);
  highlightedAnswerId.value = id;
  await nextTick();
  document.getElementById(answer.followupId == null ? `answer-${id}` : `reply-${id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
}, { flush: "post" });

watch([detail, () => route.query.followup], async ([current, requested]) => {
  if (!studentReturnTo.value || !current || typeof requested !== "string") return;
  const followup = current.followups.find(item => item.id === Number(requested));
  if (!followup) return;
  highlightedFollowupId.value = followup.id; openFollowupThread(followup.id);
  await nextTick();
  document.getElementById(`followup-${followup.id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
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
  if (openedFromFeed.value && previousRoute === "/") {
    router.back();
    return;
  }
  if (studentReturnTo.value) {
    if (previousRoute === studentReturnTo.value) router.back();
    else void router.push(studentReturnTo.value);
    return;
  }
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
  // 直接打开详情时先确定会话，避免把已登录学生的首次浏览记入游客历史。
  await userStore.fetchUserInfo();
  if (!active) return;
  const viewerId = me.value?.id ?? null;
  error.value = "";
  try {
    const data = await api.getQuestion(questionId);
    if (!active || viewerId !== (me.value?.id ?? null)) return;
    detail.value = data;
    if (selectedRootAnswerId.value != null && !selectedRootAnswer.value) {
      selectedRootAnswerId.value = null;
      followupError.value = "原回答当前不可追问，请重新选择已发布的回答。";
    }
    // 记录浏览历史（本地存储，后端接口暂未实现）
    addViewHistory({ id: detail.value.question.id, title: detail.value.question.title }, viewerId);
  } catch (e: any) {
    error.value = openedFromHrMessages.value ? "相关内容已不可查看，可能已被撤销或下架。" : e.message;
  } finally {
    loading.value = false;
  }
}

function requestFollowupWithdrawal(followup: Followup) {
  if (withdrawingFollowupId.value !== null || me.value?.role !== "student"
      || me.value.id !== followup.author.id || !["published", "pending_machine", "pending_human"].includes(followup.status)) return;
  dialog.warning({
    title: "撤销追问",
    content: "撤销后这条追问将不再公开，已有 HR 回复按各自状态保留。确定撤销吗？",
    positiveText: "确定撤销",
    negativeText: "取消",
    onPositiveClick: async () => {
      if (withdrawingFollowupId.value !== null) return false;
      withdrawingFollowupId.value = followup.id;
      try {
        const removed = await api.withdrawFollowup(questionId, followup.id);
        followup.status = removed.status;
        followup.body = removed.body;
        await load();
        if (error.value) message.warning("追问已撤销，请刷新页面查看最新状态。");
        else message.success("追问已撤销。");
        return true;
      } catch (cause) {
        message.error(cause instanceof Error ? cause.message : "撤销失败，请稍后重试。");
        return false;
      } finally {
        withdrawingFollowupId.value = null;
      }
    },
  });
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
  followupNotice.value = "";
  if (!followupBody.value.trim()) { followupError.value = "请先填写追问内容。"; return; }
  const target = selectedRootAnswer.value;
  if (!canStudentFollowup.value || !target) {
    followupError.value = "请先选择一条已发布的主回答。";
    return;
  }
  followupSubmitting.value = true;
  try {
    const followup = await api.followup(questionId, followupBody.value.trim(), target.author.id, target.id);
    if (followup.status === "rejected") {
      followupError.value = "追问未通过审核，请修改内容后重新提交。";
    } else {
      followupBody.value = "";
      followupNotice.value = followup.status === "published" ? "追问已发布。" : "追问已提交，正在等待审核；通过后才会公开。";
    }
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

function requireAnswerInteractionLogin() {
  if (me.value) return true;
  void router.push({ path: "/login", query: { redirect: route.fullPath } });
  return false;
}

async function toggleAnswerLike(answerId: number) {
  const answer = detail.value?.answers.find((item) => item.id === answerId);
  if (!answer || answer.status !== "published" || likingAnswerId.value !== null || !requireAnswerInteractionLogin()) return;
  const viewerId = me.value?.id;
  const next = !answer.liked;
  likingAnswerId.value = answerId;
  try {
    if (next) await api.interact("answer", answerId, "like");
    else await api.uninteract("answer", answerId, "like");
    if (!active || viewerId !== me.value?.id) return;
    const current = detail.value?.answers.find((item) => item.id === answerId);
    if (current && current.liked !== next) {
      current.liked = next;
      current.likeCount = Math.max(0, current.likeCount + (next ? 1 : -1));
    }
  } catch (e) {
    if (active) message.error(e instanceof Error ? e.message : "操作失败");
  } finally {
    likingAnswerId.value = null;
  }
}

function reportAnswer(answerId: number) {
  if (!requireAnswerInteractionLogin()) return;
  reportAnswerId.value = answerId;
  reportOpen.value = true;
}

const reviewStatus: Record<string, string> = {
  REVIEWING: "审核中",
  PUBLISHED: "已发布",
  REJECTED: "审核未通过",
  REMOVED: "已下架",
  WITHDRAWN: "已撤回",
  published: "已发布",
  pending_machine: "待审核",
  pending_human: "待审核",
  rejected: "审核未通过",
  removed: "已撤回",
};

onMounted(load);
</script>

<template>
  <div class="question-detail-page">
  <div class="detail-navigation">
    <button type="button" class="back-link" @click="returnToPreviousPage">
      <span aria-hidden="true">←</span> {{ returnLabel }}
    </button>
  </div>
  <p v-if="loading && !detail" class="card detail-loading" role="status">正在加载问答…</p>
  <p v-if="error" class="error">{{ error }}</p>

<section v-if="detail" class="card question-hero detail-enter">
    <h1 class="detail-title">{{ detail.question.title }}</h1>
    <div class="question-tags">
      <span class="question-status" :class="detail.question.displayStatus">{{ reviewStatus[detail.question.displayStatus] ?? detail.question.displayStatus }}</span>
      <span v-for="t in detail.question.tags" :key="t" class="tag">{{ t }}</span>
    </div>
    <div class="question-author">
      <span class="question-author-name">{{ detail.question.author.nickname }}</span>
      <time class="question-author-time" :datetime="detail.question.createdAt">提问于 {{ detail.question.createdAt.replace('T', ' ').slice(0, 16) }}</time>
    </div>
    <p class="question-body question-content">{{ detail.question.body }}</p>
    <div class="question-summary">
      <span>{{ mainAnswers.filter(answer => answer.status === 'published').length }} 条 HR 主回答</span>
      <span>{{ detail.question.followupCount }} 条学生追问</span>
    </div>
  </section>

<section v-if="detail" class="answer-floors detail-enter">
    <div class="answers-heading">
      <div><p class="section-kicker">HR 经验分享</p><h2>回答与追问</h2></div>
      <span class="answer-count">{{ mainAnswers.filter(answer => answer.status === 'published').length }} 条主回答</span>
    </div>
    <div v-if="conversations.length === 0" class="answer-empty">还没有 HR 回答，问题正在等待有经验的人。</div>
    <article v-for="conversation in conversations" :id="`answer-${conversation.rootAnswerId}`" :key="conversation.key" class="card answer-floor" :class="{ 'is-highlighted': highlightedAnswerId === conversation.rootAnswerId }">
      <template v-if="conversation.rootAnswer">
        <div class="answer-head">
          <div class="answer-author">
            <div class="answer-author-line">
              <strong>{{ conversation.nickname }}</strong>
              <span class="hr-badge">HR</span>
              <span v-if="me?.id === conversation.hrId" class="me-badge">我</span>
            </div>
            <p v-if="conversation.company || conversation.position" class="answer-role">{{ [conversation.company, conversation.position].filter(Boolean).join(' · ') }}</p>
          </div>
          <time class="answer-time" :datetime="conversation.rootAnswer.createdAt">{{ conversation.rootAnswer.createdAt.replace('T', ' ').slice(0, 16) }}</time>
        </div>
<p v-if="conversation.rootAnswer.displayStatus === 'REMOVED'" class="muted answer-content">{{ removedAnswerText(conversation.rootAnswer) }}相关追问与回复保留。</p>
        <p v-else class="question-body answer-content">{{ conversation.rootAnswer.body }}</p>
        <span v-if="conversation.rootAnswer.displayStatus !== 'PUBLISHED'" class="tag answer-review-tag">{{ reviewStatus[conversation.rootAnswer.displayStatus] ?? conversation.rootAnswer.displayStatus }}</span>
        <div class="floor-actions answer-actions">
          <button v-if="me && conversation.rootAnswer.status === 'published'" class="answer-action" @click="markUseful(conversation.rootAnswer.id)">有用（{{ detail.answers.find((answer) => answer.id === conversation.rootAnswerId)?.usefulCount ?? 0 }}）</button>
          <template v-if="conversation.rootAnswer.status === 'published'">
            <button type="button" class="answer-action" :class="{ 'is-liked': conversation.rootAnswer.liked }" :aria-pressed="conversation.rootAnswer.liked" :disabled="likingAnswerId !== null" @click="toggleAnswerLike(conversation.rootAnswer.id)">
              {{ conversation.rootAnswer.liked ? '已点赞' : '点赞' }}（{{ conversation.rootAnswer.likeCount }}）
            </button>
            <button type="button" class="answer-action answer-action--quiet" @click="reportAnswer(conversation.rootAnswer.id)">举报</button>
          </template>
          <button v-if="canStudentFollowup && conversation.rootAnswer.status === 'published'" type="button" class="answer-action answer-action--followup" @click="askAboutAnswer(conversation.rootAnswer.id)">追问这条回答</button>
          <button v-if="me?.role === 'hr' && me.id === conversation.rootAnswer.author.id && canWithdrawAnswer(conversation.rootAnswer)" type="button" class="answer-action answer-action--danger withdraw-answer" :disabled="withdrawingAnswerId !== null" @click="requestWithdrawal(conversation.rootAnswer)">
            {{ withdrawingAnswerId === conversation.rootAnswer.id ? '撤销中…' : '撤销这条回答' }}
          </button>
        </div>
      </template>
      <button type="button" class="followup-toggle" :aria-expanded="openFollowupKeys.includes(conversation.key)" @click="toggleFollowups(conversation.key)">
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
          :withdrawing-followup-id="withdrawingFollowupId"
          :liking-answer-id="likingAnswerId"
          @reply="selectFollowup" @useful="markUseful" @withdraw="requestWithdrawal"
          @withdraw-followup="requestFollowupWithdrawal" @like="toggleAnswerLike" @report="reportAnswer"
        />
      </div>
    </article>
  </section>

  <div v-if="detail && canStudentFollowup" ref="followupFormRef" class="card composer-card">
    <h3>针对一条回答追问</h3>
    <template v-if="selectedRootAnswer">
      <p class="muted">正在追问 HR · {{ selectedRootAnswer.author.nickname }}的这条回答：</p>
      <blockquote class="selected-answer-preview">{{ followupOptionLabel(selectedRootAnswer.body) }}</blockquote>
      <div class="field"><textarea v-model="followupBody" placeholder="针对这条回答继续追问…" /></div>
      <button class="primary" :disabled="followupSubmitting" @click="submitFollowup">{{ followupSubmitting ? '发送中…' : '发送追问' }}</button>
    </template>
    <p v-else class="muted">点击上方某条回答下的“追问这条回答”，选择追问对象。</p>
    <p v-if="followupError" class="answer-error" role="alert">{{ followupError }}</p>
    <p v-if="followupNotice" class="answer-notice" role="status">{{ followupNotice }}</p>
  </div>

  <div v-if="detail && me?.role === 'hr' && detail.question.scope === 'public'" ref="answerFormRef" class="card composer-card">
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
  <ReportDialog v-if="reportOpen" v-model:show="reportOpen" target-type="answer" :target-id="reportAnswerId" />
  </div>
</template>

<style scoped>
.question-detail-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 28px;
}

.question-detail-page > .card,
.answer-floors > .card {
  margin: 0;
}

.detail-loading {
  padding: 36px 24px;
  color: var(--color-text-muted);
  text-align: center;
}

.question-hero {
  overflow: hidden;
  border: 1px solid #e1e9f3;
  border-radius: 16px;
  background: linear-gradient(145deg, #ffffff 0%, #f7faff 100%);
  padding: 25px 28px 22px;
  box-shadow: 0 16px 40px -32px rgba(35, 65, 105, 0.42);
}

.detail-navigation { margin-bottom: -6px; }

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  padding: 6px 10px;
  color: var(--color-brand);
  font-size: 14px;
  font-weight: 600;
}

.back-link:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand-dark);
}

.detail-title {
  margin: 0 0 13px;
  color: var(--color-text-primary);
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 38px;
  overflow-wrap: anywhere;
}

.question-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.question-tags .tag {
  border: 1px solid rgba(227, 232, 239, 0.75);
  background: rgba(255, 255, 255, 0.75);
  padding: 3px 10px;
}

.question-status {
  border-radius: 999px;
  padding: 3px 10px;
  background: var(--color-bg-hover);
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.question-status.published {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.question-status.pending_machine,
.question-status.pending_human {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.question-status.rejected {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.question-author {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  margin: 12px 0 16px;
}

.question-author-name {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.question-author-time {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 17px;
}

.question-content {
  margin: 0;
  border-top: 1px solid #e8edf4;
  padding-top: 19px;
  color: #3f4b5f;
  font-size: 16px;
  font-weight: 400;
  letter-spacing: 0.01em;
  line-height: 31px;
  overflow-wrap: anywhere;
}

.question-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 19px;
  border-top: 1px solid #e8edf4;
  padding-top: 14px;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 20px;
}

.answer-floors {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.answers-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin: 8px 2px 1px;
}

.section-kicker {
  margin: 0 0 3px;
  color: var(--color-brand);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.answers-heading h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 21px;
  font-weight: 650;
  line-height: 30px;
}

.answer-count {
  border-radius: 999px;
  background: var(--color-bg-hover);
  padding: 4px 10px;
  color: var(--color-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.answer-empty {
  border: 1px dashed var(--color-border-default);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.6);
  padding: 30px 20px;
  color: var(--color-text-muted);
  font-size: 14px;
  text-align: center;
}

.answer-floor {
  scroll-margin-top: 80px;
  border: 1px solid #e3e9f0;
  border-radius: 14px;
  background: var(--color-bg-surface);
  padding: 22px 24px;
  box-shadow: 0 8px 24px -24px rgba(35, 55, 85, 0.4);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.answer-floor:hover {
  border-color: #d3dfed;
  box-shadow: 0 12px 28px -22px rgba(35, 55, 85, 0.32);
}

.answer-floor.is-highlighted {
  outline: 2px solid #b9cdf0;
  outline-offset: 3px;
  background: #f8faff;
}

.answer-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  min-width: 0;
  align-items: start;
  gap: 5px 16px;
  overflow-wrap: anywhere;
}

.answer-author {
  min-width: 0;
}

.answer-author-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.answer-author-line strong {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.hr-badge,
.me-badge {
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 10px;
  line-height: 16px;
}

.hr-badge {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.me-badge {
  background: var(--color-bg-hover);
  color: var(--color-text-muted);
}

.answer-role {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 19px;
}

.answer-time {
  padding-top: 2px;
  color: var(--color-text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
  white-space: nowrap;
}

.answer-content {
  margin: 16px 0 0;
  color: #435065;
  font-size: 16px;
  font-weight: 400;
  line-height: 30px;
  overflow-wrap: anywhere;
}

.answer-review-tag {
  display: inline-block;
  margin-top: 10px;
}

.floor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.answer-actions {
  margin-top: 17px;
  border-top: 1px solid var(--color-border-subtle);
  padding-top: 11px;
}

.answer-action {
  min-height: 33px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  padding: 5px 9px;
  color: var(--color-text-regular);
  font-size: 12px;
  line-height: 22px;
}

.answer-action:hover,
.answer-action.is-liked {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.answer-action--quiet {
  color: var(--color-text-muted);
}

.answer-action--followup {
  margin-left: auto;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-weight: 500;
}

.answer-action--danger {
  color: var(--color-danger);
}

.followup-toggle {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  margin-top: 9px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  padding: 5px 8px;
  color: var(--color-brand);
  font-size: 13px;
  font-weight: 500;
}

.followup-toggle:hover {
  background: var(--color-brand-soft);
}

.floor-followups {
  margin-top: 9px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 11px;
  background: #f8fafc;
  padding: 14px;
  animation: detail-rise 160ms ease-out;
}

.composer-card {
  border: 1px solid #e3e9f0;
  border-radius: 14px;
  background: var(--color-bg-surface);
  padding: 23px 24px;
}

.composer-card h3 {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 650;
  line-height: 28px;
}

.composer-card .muted {
  line-height: 23px;
}

.composer-card .primary {
  min-height: 40px;
  padding: 8px 18px;
}

.answer-modes {
  display: inline-flex;
  gap: 3px;
  margin: 0 0 15px;
  border: 1px solid var(--color-border-default);
  border-radius: 8px;
  padding: 3px;
  background: #f3f5f7;
}

.answer-mode-button {
  min-height: 34px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  padding: 0 12px;
  color: var(--color-text-regular);
  font: inherit;
  cursor: pointer;
}

.answer-mode-button.active {
  background: #fff;
  color: var(--color-brand);
  box-shadow: 0 1px 3px rgba(17, 24, 39, 0.08);
}

.selected-answer-preview {
  margin: 10px 0;
  border-left: 3px solid #9bb9e5;
  border-radius: 0 8px 8px 0;
  background: #f6f9ff;
  padding: 11px 13px;
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 23px;
  overflow-wrap: anywhere;
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

.answer-notice,
.answer-error {
  margin: 0 0 10px;
  font-size: 14px;
}

.answer-notice {
  color: var(--color-success);
}

.answer-error {
  color: var(--color-danger);
}

.detail-enter {
  animation: detail-rise 200ms ease-out;
}

@keyframes detail-rise {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 640px) {
  .question-hero {
    border-radius: 14px;
    padding: 21px 18px 18px;
  }

  .detail-title {
    font-size: 22px;
    line-height: 32px;
  }

  .question-content {
    font-size: 15px;
    line-height: 29px;
  }

  .answer-floor,
  .composer-card {
    padding: 18px 16px;
  }

  .answer-content {
    font-size: 15px;
    line-height: 29px;
  }

  .answer-action--followup {
    margin-left: 0;
  }

  .floor-followups {
    padding: 10px;
  }
}

@media (max-width: 520px) {
  .answer-head { grid-template-columns: minmax(0, 1fr); }
  .answer-time { padding-top: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .detail-enter,
  .floor-followups {
    animation: none;
  }

  .answer-floor {
    transition: none;
  }
}
</style>
