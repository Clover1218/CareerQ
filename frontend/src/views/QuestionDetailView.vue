<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useDialog, useMessage } from "naive-ui";
import { api, type Followup, type QuestionDetail } from "../api";
import { useUserStore } from "@/stores/user";
import { addViewHistory } from "@/utils/history";
import ClinicRoomLabels from "@/components/ClinicRoomLabels.vue";
import FeedIcon from "@/components/FeedIcon.vue";
import FollowupDiscussion from "@/components/FollowupDiscussion.vue";
import PublicAuthorLink from "@/components/PublicAuthorLink.vue";
import { buildAnswerThreads, detailThreadItems, type AnswerThread } from "@/utils/answerThreads";
import { canWithdrawAnswer, useAnswerWithdrawal } from "@/composables/useAnswerWithdrawal";
import { removedAnswerText } from "@/utils/answerRemoval";
import { clinicTopic, supplementalClinicTags } from "@/utils/clinicTopics";

const ReportDialog = defineAsyncComponent(() => import("@/components/ReportDialog.vue"));

const route = useRoute();
const router = useRouter();
const dialog = useDialog();
const message = useMessage();
const userStore = useUserStore();
const { user: me } = storeToRefs(userStore);
let active = true;
let answerHighlightTimer: ReturnType<typeof setTimeout> | undefined;
onBeforeUnmount(() => {
  active = false;
  clearTimeout(answerHighlightTimer);
});
const detail = ref<QuestionDetail | null>(null);
const loading = ref(true);
const error = ref("");
const answerError = ref("");
const answerNotice = ref("");
const answerDrafts = ref<Record<string, string>>({});
const answerSubmitting = ref(false);
const answerMode = ref<"question" | "followup">("question");
const selectedFollowupId = ref<number | null>(null);
const answerDraftKey = computed(() => {
  if (answerMode.value === "question") return "question";
  return selectedFollowupId.value == null ? null : `followup-${selectedFollowupId.value}`;
});
const answerBody = computed({
  get: () => answerDraftKey.value == null ? "" : answerDrafts.value[answerDraftKey.value] ?? "",
  set: (body: string) => {
    if (answerDraftKey.value != null) answerDrafts.value[answerDraftKey.value] = body;
  },
});
const answerFormRef = ref<HTMLElement | null>(null);
const answerInputRef = ref<HTMLTextAreaElement | null>(null);
const followupSubmitting = ref(false);
const followupError = ref("");
const followupNotice = ref("");
const withdrawingFollowupId = ref<number | null>(null);
const highlightedFollowupId = ref<number | null>(null);
let appliedFollowupLink: string | null = null;

const questionId = Number(route.params.id);
const openedFromClinic = computed(() => route.query.from === "feed" || route.query.from === "ask");
const feedReturnPath = computed(() => {
  const target = route.query.returnTo;
  if (target === "/browse" || target === "/#public-questions") return "/#public-questions";
  if (target === "/#clinic-rooms") return target;
  return typeof target === "string" && /^\/clinic\/(career|resume|interview|intern)$/.test(target) ? target : "/";
});
const returnRoom = computed(() => openedFromClinic.value && feedReturnPath.value.startsWith("/clinic/")
  ? clinicTopic(feedReturnPath.value.split("/").at(-1)) : undefined);
const openedFromHr = computed(() => route.query.from === "hr");
const openedFromClinicDiscussion = computed(() => route.query.from === "clinic-discussion");
const openedFromDiscussionBallot = computed(() => route.query.from === "hr-discussions");
const openedFromHrProfile = computed(() => route.query.from === "hr-profile");
const openedFromHrMessages = computed(() => route.query.from === "hr-messages");
const publicProfileReturnTo = computed(() => {
  const target = route.query.returnTo;
  return route.query.from === "public-profile" && typeof target === "string" && /^\/users\/\d+(\?.*)?$/.test(target)
    ? target : null;
});
const studentReturnTo = computed(() => {
  if (me.value?.role !== "student" || typeof route.query.returnTo !== "string") return null;
  const value = route.query.returnTo;
  if (route.query.from === "student-messages" && (value === "/messages" || value.startsWith("/messages?"))) return value;
  if (route.query.from === "student-favorites" && value.startsWith("/me?tab=favorites")) return value;
  return null;
});
const studentReturnLabel = computed(() => route.query.from === "student-messages" ? "返回消息中心" : "返回我的收藏");
const returnLabel = computed(() => {
  if (publicProfileReturnTo.value) return "返回公开主页";
  if (studentReturnTo.value) return studentReturnLabel.value;
  if (openedFromHrMessages.value) return "返回消息中心";
  if (openedFromHrProfile.value) return "返回个人中心";
  if (openedFromHr.value) return "返回 HR 工作台";
  if (openedFromClinicDiscussion.value) return "返回 HR 共议";
  if (openedFromDiscussionBallot.value) return "返回下期共议";
  if (returnRoom.value) return `返回${returnRoom.value.name}诊室`;
  return "返回列表";
});
const highlightedAnswerId = ref<number | null>(null);
const hrFollowups = computed(() => detail.value?.followups.filter(
  canReplyFollowup,
) ?? []);
const pendingHrFollowups = computed(() => hrFollowups.value.filter(
  (followup) => followupReplyState(followup) === "pending",
));
const questionDisplayStatus = computed(() => detail.value?.question.displayStatus ?? detail.value?.question.status ?? "");
const selectedFollowup = computed(() => hrFollowups.value.find(
  (followup) => followup.id === selectedFollowupId.value,
));
const mainAnswers = computed(() => detail.value?.answers.filter((answer) => answer.followupId == null) ?? []);
const likingAnswerId = ref<number | null>(null);
const publicMainAnswers = computed(() => mainAnswers.value.filter(answer => answer.status === "published"));
const publicAnswerAuthors = computed(() => {
  const answers = new Map<number, typeof publicMainAnswers.value[number]>();
  for (const answer of publicMainAnswers.value) {
    if (!answers.has(answer.author.id)) answers.set(answer.author.id, answer);
  }
  return [...answers.values()];
});
const showAllAuthors = ref(false);
const initialAuthorCount = 4;
const visibleAuthors = computed(() => showAllAuthors.value
  ? publicAnswerAuthors.value : publicAnswerAuthors.value.slice(0, initialAuthorCount));

function jumpToRootAnswer(answerId: number) {
  const answer = document.getElementById("answer-" + answerId);
  answer?.focus({ preventScroll: true });
  answer?.scrollIntoView({ block: "start", behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth" });
}

const reportOpen = ref(false);
const reportAnswerId = ref(0);
const conversations = computed(() => detail.value
  ? buildAnswerThreads(detailThreadItems(detail.value)).map((conversation) => ({
      ...conversation,
      followups: threadFollowups(conversation),
      displayStatus: conversation.rootAnswer.displayStatus ?? conversation.rootAnswer.status,
    }))
  : []);
const selectedRootAnswerId = ref<number | null>(null);
const followupDrafts = ref<Record<number, string>>({});
const followupBody = computed({
  get: () => selectedRootAnswerId.value == null ? "" : followupDrafts.value[selectedRootAnswerId.value] ?? "",
  set: (body: string) => {
    if (selectedRootAnswerId.value != null) followupDrafts.value[selectedRootAnswerId.value] = body;
  },
});
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
  if (!canStudentFollowup.value || followupSubmitting.value) return;
  const trigger = document.getElementById(`followup-trigger-${answerId}`);
  if (!trigger) return;
  const triggerTop = trigger.getBoundingClientRect().top;
  const closing = selectedRootAnswerId.value === answerId;
  selectedRootAnswerId.value = closing ? null : answerId;
  followupError.value = "";
  followupNotice.value = "";
  await nextTick();

  // Keep the clicked answer in view when opening or removing an editor changes the page height.
  window.scrollBy(0, trigger.getBoundingClientRect().top - triggerTop);
  if (closing) {
    trigger.focus({ preventScroll: true });
    return;
  }
  const input = document.getElementById(`followup-input-${answerId}`);
  input?.focus({ preventScroll: true });
  input?.scrollIntoView({ block: "nearest" });
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
  if (answerMode.value === "followup" && followups.length === 0) answerMode.value = "question";
});

watch(() => me.value?.id, () => {
  answerDrafts.value = {};
  answerError.value = "";
  answerNotice.value = "";
  selectedRootAnswerId.value = null;
  followupDrafts.value = {};
  followupError.value = "";
  followupNotice.value = "";
});

watch(answerDraftKey, () => {
  if (answerSubmitting.value) return;
  answerError.value = "";
  answerNotice.value = "";
});

watch([hrFollowups, () => route.query.followup], async ([followups, requested]) => {
  if (typeof requested !== "string" || requested === appliedFollowupLink
      || !detail.value || me.value?.role !== "hr") return;
  const id = Number(requested);
  if (!Number.isSafeInteger(id) || id <= 0) return;
  appliedFollowupLink = requested;
  if (openedFromHrMessages.value && followups.some(followup => followup.id === id)) {
    highlightedAnswerId.value = null;
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

// 消息可能同时携带回复和所属追问，优先定位仍可见的回复。
watch([detail, () => route.query.answer, () => route.query.followup], async ([current, requestedAnswer, requestedFollowup]) => {
  const publicEntry = studentReturnTo.value !== null || publicProfileReturnTo.value !== null || openedFromClinicDiscussion.value;
  const hrEntry = me.value?.role === "hr" && (openedFromHrProfile.value || openedFromHrMessages.value);
  if ((!publicEntry && !hrEntry) || !current || followupSubmitting.value || (hrEntry && answerSubmitting.value)) return;

  const answerId = typeof requestedAnswer === "string" ? Number(requestedAnswer) : NaN;
  if (Number.isSafeInteger(answerId) && answerId > 0) {
    const answer = current.answers.find(item => item.id === answerId
      && (publicEntry ? item.status === "published" : item.author.id === me.value?.id));
    if (answer) {
      highlightedFollowupId.value = null;
      highlightedAnswerId.value = answerId;
      if (answer.followupId != null) openFollowupThread(answer.followupId);
      await nextTick();
      document.getElementById(answer.followupId == null ? `answer-${answerId}` : `reply-${answerId}`)
        ?.scrollIntoView({ behavior: "smooth", block: "center" });
      return;
    }
    highlightedAnswerId.value = null;
    answerNotice.value = "这条回答已移除，详情仅展示仍然可见的讨论。";
  }

  if (!publicEntry || typeof requestedFollowup !== "string") return;
  const followup = current.followups.find(item => item.id === Number(requestedFollowup));
  if (!followup) return;
  highlightedAnswerId.value = null;
  highlightedFollowupId.value = followup.id;
  openFollowupThread(followup.id);
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
  if (answerSubmitting.value || (mode === "followup" && hrFollowups.value.length === 0)) return;
  answerMode.value = mode;
}

async function selectFollowup(followupId: number, focus: "form" | "followup" = "form") {
  if (answerSubmitting.value || !hrFollowups.value.some((followup) => followup.id === followupId)) return;
  answerMode.value = "followup";
  selectedFollowupId.value = followupId;
  highlightedAnswerId.value = null;
  highlightedFollowupId.value = followupId;
  openFollowupThread(followupId);
  await nextTick();
  const target = focus === "followup" ? document.getElementById(`followup-${followupId}`) : answerFormRef.value;
  target?.scrollIntoView({ behavior: "smooth", block: focus === "followup" ? "start" : "center" });
}

function changeFollowupTarget(event: Event) {
  const target = event.target as HTMLSelectElement;
  const followupId = Number(target.value);
  if (answerSubmitting.value || !hrFollowups.value.some((followup) => followup.id === followupId)) return;
  selectedFollowupId.value = followupId;
}

function returnToPreviousPage() {
  const previousRoute = window.history.state?.back;
  if (publicProfileReturnTo.value) {
    if (previousRoute === publicProfileReturnTo.value) router.back();
    else void router.push(publicProfileReturnTo.value);
    return;
  }
  if (openedFromClinic.value) {
    if (previousRoute === feedReturnPath.value) router.back();
    else void router.push(feedReturnPath.value);
    return;
  }
  if (openedFromClinicDiscussion.value) {
    if (typeof previousRoute === "string" && (previousRoute === "/" || previousRoute.startsWith("/#"))) router.back();
    else void router.push({ name: "clinic-home", hash: "#hr-discussion" });
    return;
  }
  if (openedFromDiscussionBallot.value) {
    if (typeof previousRoute === "string" && /^\/hr-discussions(?:\?|$)/.test(previousRoute)) router.back();
    else void router.push({ name: "hr-discussions" });
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
      message.warning("原回答当前不可追问，请重新选择已发布的回答。");
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
  if (answerSubmitting.value || me.value?.role !== "hr") return;
  answerError.value = "";
  answerNotice.value = "";
  const ownerId = me.value.id;
  const isFollowupReply = answerMode.value === "followup";
  const followupId = isFollowupReply ? selectedFollowup.value?.id : undefined;
  const draftKey = answerDraftKey.value;
  const body = answerBody.value.trim();
  if (!body) {
    answerError.value = isFollowupReply ? "请先填写追问回复内容。" : "请先填写回答内容。";
    return;
  }
  if (isFollowupReply && followupId == null) {
    answerError.value = "请选择发给你的、可以回复的学生追问。";
    return;
  }
  answerSubmitting.value = true;
  const label = isFollowupReply ? "追问回复" : "主问题回答";
  try {
    const submittedAnswer = followupId == null
      ? await api.answer(questionId, body)
      : await api.answerFollowup(questionId, followupId, body);
    if (!active || me.value?.id !== ownerId) return;
    if (submittedAnswer.status === "rejected") {
      answerError.value = `${label}未通过审核，内容已保留在输入框中。`;
    } else {
      if (draftKey != null) delete answerDrafts.value[draftKey];
      answerNotice.value = submittedAnswer.status === "published"
        ? `${label}已发布。` : `${label}已提交，正在等待审核。通过后会公开展示。`;
    }
    await load();
    if (!active || me.value?.id !== ownerId || submittedAnswer.status !== "published") return;

    message.success(`${label}已发布。`);
    if (followupId != null) openFollowupThread(followupId);
    await nextTick();
    const target = document.getElementById(`${followupId == null ? "answer" : "reply"}-${submittedAnswer.id}`);
    if (!active || me.value?.id !== ownerId || !target) return;
    highlightedFollowupId.value = null;
    highlightedAnswerId.value = submittedAnswer.id;
    target.scrollIntoView({
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
      block: "start",
    });
    clearTimeout(answerHighlightTimer);
    answerHighlightTimer = setTimeout(() => {
      if (highlightedAnswerId.value === submittedAnswer.id) highlightedAnswerId.value = null;
    }, 4000);
  } catch (cause) {
    if (active && me.value?.id === ownerId) {
      answerError.value = cause instanceof Error ? cause.message : `${label}提交失败，请重试。`;
    }
  } finally {
    answerSubmitting.value = false;
  }
}

async function submitFollowup() {
  const owner = me.value;
  const target = selectedRootAnswer.value;
  if (followupSubmitting.value || !canStudentFollowup.value || !owner || !target) return;
  followupError.value = "";
  followupNotice.value = "";
  const body = followupBody.value.trim();
  if (!body) {
    followupError.value = "请先填写追问内容。";
    return;
  }
  followupSubmitting.value = true;
  try {
    const followup = await api.followup(questionId, body, target.author.id, target.id);
    if (!active || me.value?.id !== owner.id) return;
    if (followup.status === "rejected") {
      followupError.value = "追问未通过审核，请修改内容后重新提交。";
    } else {
      delete followupDrafts.value[target.id];
      followupNotice.value = followup.status === "published" ? "追问已发布。" : "追问已提交，正在等待审核；通过后才会公开。";
    }
    await load();
    if (active && me.value?.id === owner.id) openFollowupThread(followup.id);
  } catch (cause) {
    if (active && me.value?.id === owner.id) {
      followupError.value = cause instanceof Error ? cause.message : "追问提交失败，请重试。";
    }
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

onMounted(async () => {
  const savedScroll = window.history.state?.scroll;
  await load();
  if (active && savedScroll && Number.isFinite(savedScroll.top)) {
    await nextTick();
    window.scrollTo({ top: savedScroll.top, left: savedScroll.left ?? 0 });
    return;
  }
  if (!active || (!openedFromHr.value && !openedFromClinicDiscussion.value) || route.query.focus !== "answer"
      || me.value?.role !== "hr" || detail.value?.question.status !== "published") return;

  await nextTick();
  answerFormRef.value?.scrollIntoView({ block: "start" });
  answerInputRef.value?.focus({ preventScroll: true });
});
</script>

<template>
  <div class="question-detail-page">
  <div class="detail-navigation">
    <button type="button" class="back-link" @click="returnToPreviousPage">
      <span aria-hidden="true"><</span> {{ returnLabel }}
    </button>
  </div>
  <p v-if="loading && !detail" class="card detail-loading" role="status">正在加载问答…</p>
  <p v-if="error" class="error">{{ error }}</p>

  <section v-if="detail" class="card question-hero detail-enter">
    <div class="question-tags">
      <span class="question-status" :class="questionDisplayStatus">{{ reviewStatus[questionDisplayStatus] ?? questionDisplayStatus }}</span>
      <ClinicRoomLabels :tags="detail.question.tags" />
      <span v-for="tag in supplementalClinicTags(detail.question.tags)" :key="tag" class="tag">{{ tag }}</span>
    </div>
    <h1 class="detail-title">{{ detail.question.title }}</h1>
    <div class="question-author">
      <PublicAuthorLink :user-id="detail.question.author.id" :nickname="detail.question.author.nickname" :avatar-url="detail.question.author.avatarUrl" class="question-author-name" />
      <time class="question-author-time" :datetime="detail.question.createdAt">提问于 {{ detail.question.createdAt.replace('T', ' ').slice(0, 16) }}</time>
    </div>
    <p class="question-body question-content">{{ detail.question.body }}</p>
    <div class="question-summary">
      <span>{{ publicMainAnswers.length }} 条 HR 主回答</span>
      <span>{{ detail.question.followupCount }} 条学生追问</span>
      <router-link v-if="me?.role === 'student' && detail.question.status === 'published'" class="editorial-button"
        :to="{ name: 'hr-discussions', query: { nominate: String(questionId) } }">推荐给下期共议</router-link>
    </div>
  </section>

  <div v-if="detail" class="discussion-layout">
  <section class="answer-floors detail-enter" aria-label="HR 回答与追问">
    <div class="answers-heading">
      <div><p class="section-kicker">HR 经验分享</p><h2>回答与追问</h2></div>
      <span class="answer-count">{{ publicMainAnswers.length }} 条主回答 · 按时间排序</span>
    </div>
    <div v-if="conversations.length === 0" class="answer-empty">还没有 HR 回答，问题正在等待有经验的人。</div>
    <article v-for="conversation in conversations" :id="`answer-${conversation.rootAnswerId}`" :key="conversation.key" class="card answer-floor" tabindex="-1" :class="{ 'is-highlighted': highlightedAnswerId === conversation.rootAnswerId }">
      <template v-if="conversation.rootAnswer">
        <div class="answer-head">
          <div class="answer-author">
            <div class="answer-author-line">
              <PublicAuthorLink :user-id="conversation.hrId" :nickname="conversation.nickname" :avatar-url="conversation.rootAnswer.author.avatarUrl" :size="36" class="answer-author-link" />
              <span class="hr-badge">HR</span>
              <span v-if="me?.id === conversation.hrId" class="me-badge">我</span>
            </div>
            <p v-if="conversation.company || conversation.position" class="answer-role">{{ [conversation.company, conversation.position].filter(Boolean).join(' · ') }}</p>
          </div>
          <time class="answer-time" :datetime="conversation.rootAnswer.createdAt">{{ conversation.rootAnswer.createdAt.replace('T', ' ').slice(0, 16) }}</time>
        </div>
        <p v-if="conversation.rootAnswer.status === 'removed'" class="muted answer-content">{{ removedAnswerText(conversation.rootAnswer) }}相关追问与回复保留。</p>
        <p v-else class="question-body answer-content">{{ conversation.rootAnswer.body }}</p>
        <span v-if="conversation.rootAnswer.status !== 'published'" class="tag answer-review-tag">{{ reviewStatus[conversation.displayStatus] ?? conversation.displayStatus }}</span>
        <div class="answer-actions">
          <button v-if="me && conversation.rootAnswer.status === 'published'" type="button" class="answer-action" @click="markUseful(conversation.rootAnswer.id)">
            有用 <span class="answer-action-count">{{ detail.answers.find((answer) => answer.id === conversation.rootAnswerId)?.usefulCount ?? 0 }}</span>
          </button>
          <template v-if="conversation.rootAnswer.status === 'published'">
            <button type="button" class="answer-action" :aria-pressed="conversation.rootAnswer.liked" :disabled="likingAnswerId !== null" @click="toggleAnswerLike(conversation.rootAnswer.id)">
              <FeedIcon name="like" :size="15" :filled="conversation.rootAnswer.liked" />
              {{ conversation.rootAnswer.liked ? '已点赞' : '点赞' }} <span class="answer-action-count">{{ conversation.rootAnswer.likeCount }}</span>
            </button>
            <button v-if="me?.id !== conversation.rootAnswer.author.id" type="button" class="answer-action" @click="reportAnswer(conversation.rootAnswer.id)">举报</button>
          </template>
          <button
            v-if="canStudentFollowup && conversation.rootAnswer.status === 'published'"
            :id="`followup-trigger-${conversation.rootAnswer.id}`"
            type="button"
            class="answer-action answer-action--primary answer-action--followup"
            :aria-expanded="selectedRootAnswerId === conversation.rootAnswer.id"
            :aria-controls="selectedRootAnswerId === conversation.rootAnswer.id ? `followup-composer-${conversation.rootAnswer.id}` : undefined"
            :disabled="followupSubmitting"
            @click="askAboutAnswer(conversation.rootAnswer.id)"
          >
            <FeedIcon name="comment" :size="15" /> 追问这条回答
          </button>
          <button v-if="me?.role === 'hr' && me.id === conversation.rootAnswer.author.id && canWithdrawAnswer(conversation.rootAnswer)" type="button" class="answer-action answer-action--danger" :disabled="withdrawingAnswerId !== null" @click="requestWithdrawal(conversation.rootAnswer)">
            {{ withdrawingAnswerId === conversation.rootAnswer.id ? '撤销中…' : '撤销这条回答' }}
          </button>
          <button
            v-if="conversation.followups.length"
            type="button"
            class="answer-action answer-action--primary followup-toggle"
            :aria-expanded="openFollowupKeys.includes(conversation.key)"
            @click="toggleFollowups(conversation.key)"
          >
            {{ openFollowupKeys.includes(conversation.key) ? '收起追问' : '展开追问' }}
            <span class="answer-action-count">{{ conversation.followups.length }}</span>
            <FeedIcon name="chevron" :size="14" />
          </button>
        </div>
        <form
          v-if="canStudentFollowup && conversation.rootAnswer.status === 'published' && selectedRootAnswerId === conversation.rootAnswer.id"
          :id="`followup-composer-${conversation.rootAnswer.id}`"
          class="followup-composer"
          @submit.prevent="submitFollowup"
        >
          <label :for="`followup-input-${conversation.rootAnswer.id}`">追问 {{ conversation.nickname }}</label>
          <textarea
            :id="`followup-input-${conversation.rootAnswer.id}`"
            v-model="followupBody"
            rows="3"
            :readonly="followupSubmitting"
            placeholder="针对这条回答继续追问…"
          />
          <p v-if="followupError" class="answer-error" role="alert">{{ followupError }}</p>
          <p v-if="followupNotice" class="answer-notice" role="status">{{ followupNotice }}</p>
          <div class="followup-composer-actions">
            <button type="button" :disabled="followupSubmitting" @click="askAboutAnswer(conversation.rootAnswer.id)">收起</button>
            <button type="submit" class="primary" :disabled="followupSubmitting">
              {{ followupSubmitting ? '发送中…' : '发送追问' }}
            </button>
          </div>
        </form>
      </template>
      <div v-if="conversation.followups.length && openFollowupKeys.includes(conversation.key)" class="floor-followups">
        <FollowupDiscussion
          v-for="followup in conversation.followups" :key="followup.id"
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

  <aside v-if="publicAnswerAuthors.length" class="card discussion-authors" aria-label="本题答主">
    <h2>本题答主 <span>{{ publicAnswerAuthors.length }} 位</span></h2>
    <button v-for="answer in visibleAuthors" :key="answer.author.id" type="button" @click="jumpToRootAnswer(answer.id)">
      <img v-if="answer.author.avatarUrl" :src="answer.author.avatarUrl" alt="" />
      <span v-else class="author-initial" aria-hidden="true">{{ Array.from(answer.author.nickname || 'H')[0] }}</span>
      <span class="author-jump-name">{{ answer.author.nickname }}</span><span aria-hidden="true">↓</span>
    </button>
    <button v-if="publicAnswerAuthors.length > initialAuthorCount" type="button" class="author-show-more"
      :aria-expanded="showAllAuthors" @click="showAllAuthors = !showAllAuthors">
      {{ showAllAuthors ? "收起其他答主" : "展开其他 " + (publicAnswerAuthors.length - initialAuthorCount) + " 位答主" }}
    </button>
  </aside>
  </div>

  <div v-if="detail && me?.role === 'hr' && detail.question.scope === 'public'" ref="answerFormRef" class="card composer-card hr-answer-composer">
    <h3>以 HR 身份回答</h3>
    <div class="answer-modes" role="group" aria-label="选择回答对象">
      <button
        type="button"
        class="answer-mode-button"
        :class="{ active: answerMode === 'question' }"
        :aria-pressed="answerMode === 'question'"
        :disabled="answerSubmitting"
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
        :disabled="answerSubmitting"
        @click="setAnswerMode('followup')"
      >
        回复追问
      </button>
    </div>
    <label v-if="answerMode === 'followup'" class="field followup-target">
      <span>选择要回复的学生追问</span>
      <select :value="selectedFollowupId ?? undefined" :disabled="answerSubmitting" aria-label="选择要回复的追问" @change="changeFollowupTarget">
        <option v-for="followup in hrFollowups" :key="followup.id" :value="followup.id" :title="followup.body">
          {{ followupOptionLabel(followup.body) }}
        </option>
      </select>
    </label>
    <div v-if="answerMode === 'followup' && selectedFollowup" id="hr-reply-preview" class="selected-answer-preview">
      <div class="followup-preview-heading">
        <strong>正在回复学生追问</strong>
        <button
          type="button"
          class="link"
          :disabled="answerSubmitting"
          @click="selectFollowup(selectedFollowup.id, 'followup')"
        >查看原追问</button>
      </div>
      <p class="followup-preview-copy">{{ selectedFollowup.body }}</p>
    </div>
    <div class="field">
      <textarea
        ref="answerInputRef"
        v-model="answerBody"
        :disabled="answerSubmitting"
        :aria-label="answerMode === 'question' ? '主问题回答内容' : '追问回复内容'"
        :aria-describedby="answerMode === 'followup' && selectedFollowup ? 'hr-reply-preview' : undefined"
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
.discussion-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 260px); gap: 24px; align-items: start; }
.discussion-layout:not(:has(.discussion-authors)) { grid-template-columns: minmax(0, 1fr); }
.answer-floors { min-width: 0; scroll-margin-top: calc(var(--topbar-height, 57px) + 14px); }
.discussion-authors { margin: 0; padding: 15px; }
.discussion-authors h2 { margin: 0 0 6px; font-size: 16px; }
.discussion-authors h2 span { margin-left: 4px; color: var(--color-text-muted); font-size: 12px; font-weight: 400; }
.discussion-authors button { display: flex; align-items: center; gap: 8px; width: 100%; min-width: 0; padding: 7px 0; border: 0; background: transparent; color: var(--color-text-primary); text-align: left; font: inherit; font-size: 13px; cursor: pointer; }
.discussion-authors .author-show-more { color: var(--color-brand); font-weight: 700; }
.discussion-authors button:hover { color: var(--color-brand); }
.discussion-authors button > :last-child { margin-inline-start: auto; color: var(--color-brand); }
.author-jump-name { min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.discussion-authors img, .author-initial { width: 24px; height: 24px; flex: 0 0 24px; border-radius: 50%; object-fit: cover; }
.author-initial { display: grid; place-items: center; background: var(--color-brand-soft); color: var(--color-brand); font-weight: 700; }
@media (max-width: 720px) {
  .discussion-layout { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .discussion-authors { order: -1; display: flex; flex-wrap: wrap; gap: 6px 12px; padding: 10px 12px; }
  .discussion-authors h2 { width: 100%; margin: 0; }
  .discussion-authors button { width: auto; max-width: 100%; padding: 3px 0; }
}

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
  border: 1px solid var(--color-border-default);
  border-radius: 16px;
  background: var(--color-bg-surface);
  padding: 24px 28px;
  box-shadow: none;
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
  margin: 9px 0 13px;
  color: var(--color-text-primary);
  font-size: clamp(26px, 4vw, 41px);
  font-weight: 800;
  letter-spacing: -.035em;
  line-height: 1.3;
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

.question-status.PUBLISHED,
.question-status.published {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.question-status.REVIEWING,
.question-status.pending_machine,
.question-status.pending_human {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.question-status.REJECTED,
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
  border-top: 1px solid var(--color-border-subtle);
  padding-top: 19px;
  color: var(--color-text-regular);
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
  border-top: 1px solid var(--color-border-subtle);
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
  scroll-margin-top: calc(var(--topbar-height, 57px) + 16px);
  border: 1px solid var(--color-border-default);
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
  border-color: var(--color-brand-border);
  background: var(--color-bg-feed);
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

.answer-author-link {
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
  color: var(--color-text-regular);
  font-size: 16px;
  font-weight: 400;
  line-height: 30px;
  overflow-wrap: anywhere;
}

.answer-review-tag {
  display: inline-block;
  margin-top: 10px;
}

.answer-actions,
.question-detail-page :deep(.reply-actions) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.answer-actions {
  margin-top: 17px;
  border-top: 1px solid var(--color-border-subtle);
  padding-top: 11px;
}

.question-detail-page :deep(.answer-action) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 6px 11px;
  border: 1px solid var(--color-border-default);
  border-radius: 8px;
  background: var(--color-bg-surface);
  color: var(--color-text-regular);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}

.question-detail-page :deep(.answer-action:hover:not(:disabled)) {
  border-color: #b9c9dd;
  background: var(--color-bg-hover);
}

.question-detail-page :deep(.answer-action:disabled) {
  opacity: 0.55;
}

.question-detail-page :deep(.answer-action--primary),
.question-detail-page :deep(.answer-action[aria-pressed="true"]) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand-dark);
}

.question-detail-page :deep(.answer-action--primary:hover:not(:disabled)),
.question-detail-page :deep(.answer-action[aria-pressed="true"]:hover:not(:disabled)) {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.question-detail-page :deep(.answer-action--danger) {
  border-color: #efd3d3;
  color: var(--color-danger);
}

.question-detail-page :deep(.answer-action--danger:hover:not(:disabled)) {
  border-color: #e5b6b6;
  background: var(--color-danger-soft);
}

.question-detail-page :deep(.answer-action-count) {
  min-width: 20px;
  padding: 0 5px;
  border-radius: 10px;
  background: var(--color-bg-hover);
  font-variant-numeric: tabular-nums;
  line-height: 18px;
  text-align: center;
}

.question-detail-page :deep(.answer-action--primary .answer-action-count),
.question-detail-page :deep(.answer-action[aria-pressed="true"] .answer-action-count) {
  background: var(--color-brand-border);
}

.answer-action--followup:last-child,
.followup-toggle {
  margin-inline-start: auto;
}

.followup-toggle[aria-expanded="true"] :deep(svg) {
  transform: rotate(180deg);
}

.followup-composer {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  border: 1px solid var(--color-brand-border);
  border-radius: 10px;
  background: var(--color-bg-feed);
  padding: 12px;
}

.followup-composer label {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.followup-composer :is(.answer-error, .answer-notice) {
  margin: 0;
}

.followup-composer-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.floor-followups {
  margin-top: 9px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 11px;
  background: var(--color-bg-feed);
  padding: 14px;
  animation: detail-rise 160ms ease-out;
}

.composer-card {
  border: 1px solid var(--color-border-default);
  border-radius: 14px;
  background: var(--color-bg-surface);
  padding: 23px 24px;
}

.hr-answer-composer,
.question-detail-page :deep(.followup-answer) {
  scroll-margin-top: calc(var(--topbar-height, 57px) + 16px);
}

.composer-card h3 {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 650;
  line-height: 28px;
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
  background: var(--color-bg-hover);
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
  background: var(--color-bg-surface);
  color: var(--color-brand);
  box-shadow: 0 1px 3px rgba(17, 24, 39, 0.08);
}

.selected-answer-preview {
  margin: 10px 0;
  border-left: 3px solid var(--color-brand-border);
  border-radius: 0 8px 8px 0;
  background: var(--color-bg-feed);
  padding: 11px 13px;
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 23px;
  overflow-wrap: anywhere;
}

.followup-preview-heading {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 4px 16px;
}

.followup-preview-heading .link {
  color: var(--color-brand-dark);
  font-size: 13px;
}

.followup-preview-copy {
  display: -webkit-box;
  margin: 3px 0 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  white-space: pre-wrap;
}

.followup-target {
  margin: 0 0 12px;
}

.followup-target select {
  width: 100%;
  min-height: 40px;
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  background: var(--color-bg-surface);
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

.hr-answer-composer .answer-notice {
  padding: 9px 12px;
  border-radius: 7px;
  background: var(--color-brand-soft);
  color: var(--color-brand-dark);
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

  .answer-action--followup:last-child {
    margin-inline-start: 0;
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
