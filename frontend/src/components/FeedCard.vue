<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useDialog, useMessage } from "naive-ui";
import {
  api,
  type QuestionContent,
  type QuestionDetail,
  type QuestionFeedItem,
  type Thread,
  type ThreadItem,
  type ViewerState,
} from "@/api";
import { useUserStore } from "@/stores/user";
import FeedIcon from "./FeedIcon.vue";
import ReportDialog from "./ReportDialog.vue";
import { buildAnswerThreads, detailThreadItems, type AnswerThread as Conversation } from "@/utils/answerThreads";
import { removedAnswerText } from "@/utils/answerRemoval";

const props = defineProps<{
  item: QuestionFeedItem;
  /** 学生个人中心提问列表：问题状态，用于标题右侧状态标签 */
  status?: string;
  /** 学生个人中心提问列表：作者视角，隐藏编辑器并将提问时间常驻 */
  ownerMode?: boolean;
  /** 学生个人中心提问列表：是否显示「撤回」操作 */
  withdrawable?: boolean;
}>();

const emit = defineEmits<{
  dismiss: [id: number];
  withdraw: [];
}>();
const route = useRoute();
const router = useRouter();
const message = useMessage();
const dialog = useDialog();
const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const expanded = ref(false);
const excerptBlockRef = ref<HTMLElement | null>(null);
const excerptRef = ref<HTMLElement | null>(null);
const excerptOverflows = ref(false);
let excerptObserver: ResizeObserver | undefined;
let excerptMeasureFrame: number | undefined;
const threadOpen = ref(false);
const loadingContent = ref(false);
const loadingThread = ref(false);
const submitting = ref(false);
const error = ref("");
const content = ref<QuestionContent | null>(null);
const detail = ref<QuestionDetail | null>(null);
const thread = ref<Thread | null>(null);
const openThreadKeys = ref<string[]>([]);
const selectedFollowups = reactive<Record<string, number>>({});
const rootDraft = ref("");
const threadDrafts = reactive<Record<string, string>>({});
const focusedComposer = ref<string | null>(null);
const menuOpen = ref<string | null>(null);
const reportOpen = ref(false);
const reportTarget = ref<{ targetType: "question" | "answer"; targetId: number }>({ targetType: "question", targetId: 0 });

const counts = reactive({
  likeCount: props.item.likeCount,
  favoriteCount: props.item.favoriteCount,
  replyCount: props.item.replyCount,
  answerCount: props.item.answerCount,
  followupCount: props.item.followupCount,
});
const liked = ref(props.item.viewer?.liked ?? false);
const favorited = ref(props.item.viewer?.favorited ?? false);
const interacting = ref(false);
const pendingInteraction = ref<"like" | "favorite" | null>(null);
let interactionVersion = 0;
let contentRequest: Promise<void> | null = null;
let replyCountsRequestVersion = 0;
let appliedReplyCountsVersion = 0;

function applyReplyCounts(data: Pick<Thread, "answerCount" | "followupCount">, version: number) {
  // 全文与回答可能同时请求，较早请求的迟到响应不能覆盖新数量。
  if (version < appliedReplyCountsVersion) return;
  appliedReplyCountsVersion = version;
  counts.answerCount = data.answerCount;
  counts.followupCount = data.followupCount;
  counts.replyCount = data.answerCount + data.followupCount;
}

function applyViewer(viewer: ViewerState, version: number) {
  if (version !== interactionVersion) return;
  liked.value = viewer.liked;
  favorited.value = viewer.favorited;
}

watch(
  () => props.item,
  (next) => {
    counts.likeCount = next.likeCount;
    counts.favoriteCount = next.favoriteCount;
    applyReplyCounts(next, ++replyCountsRequestVersion);
    if (next.viewer && !interacting.value) applyViewer(next.viewer, interactionVersion);
  },
);

const authorName = computed(() => content.value?.question.author.nickname ?? "");
const createdAt = computed(() => content.value?.question.createdAt ?? props.item.createdAt);
const detailLink = computed(() => ({
  path: `/questions/${props.item.id}`,
  query: props.ownerMode ? undefined : { from: "feed" },
}));
const replyLabel = computed(() => {
  if (threadOpen.value) return "收起回答";
  const { answerCount, followupCount } = counts;
  if (answerCount > 0) {
    return followupCount > 0
      ? `${answerCount} 个回答 · ${followupCount} 条追问`
      : `${answerCount} 个回答`;
  }
  if (followupCount > 0) return `${followupCount} 条追问`;
  if (props.ownerMode) return "暂无回答";
  return user.value?.role === "hr" ? "我来回答" : "查看问题";
});
const questionAuthorId = computed(() => content.value?.question.author.id ?? null);

const conversations = computed(() => buildAnswerThreads(thread.value?.items ?? []));
const publishedRootCount = computed(() => conversations.value.filter((item) => item.rootAnswer?.status === "published").length);
const participatingHrCount = computed(() => new Set(conversations.value.filter((item) => item.rootAnswer?.status === "published").map((item) => item.hrId)).size);

const STATUS_META: Record<string, { text: string; tone: string }> = {
  published: { text: "已发表", tone: "success" },
  pending_machine: { text: "待审核", tone: "warning" },
  pending_human: { text: "待审核", tone: "warning" },
  rejected: { text: "未通过", tone: "danger" },
  removed: { text: "已撤回", tone: "neutral" },
};

const statusMeta = computed(() => {
  if (!props.status) return null;
  return STATUS_META[props.status] ?? { text: props.status, tone: "neutral" };
});

function formatRelativeTime(value: string): string {
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return "";
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60_000));
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} 天前`;
  return value.slice(0, 10);
}

// 审核中/未通过的问题不能点赞收藏，按作者视角展示计数但禁用操作
const interactive = computed(
  () =>
    !props.ownerMode ||
    !["pending_machine", "pending_human", "rejected", "removed"].includes(props.status ?? ""),
);

function formatTime(value: string): string {
  return value.replace("T", " ").slice(0, 16);
}

function requireLogin(): boolean {
  if (user.value) return true;
  router.push({ path: "/login", query: { redirect: route.fullPath } });
  return false;
}

function reportError(e: unknown, fallback: string) {
  const text = e instanceof Error ? e.message : fallback;
  error.value = text;
  message.error(text);
}

async function ensureContent() {
  if (content.value) return;
  if (contentRequest) return contentRequest;
  const viewerId = user.value?.id;
  const version = interactionVersion;
  const countsVersion = ++replyCountsRequestVersion;
  loadingContent.value = true;
  error.value = "";
  contentRequest = (async () => {
    try {
      // 作者视角走 detail，允许查看自己审核中的问题正文与回答。
      const ownDetail = props.ownerMode ? await api.getQuestion(props.item.id) : null;
      const data = ownDetail ?? await api.getQuestionContent(props.item.id);
      if (viewerId !== user.value?.id) return;
      if (ownDetail) detail.value = ownDetail;
      content.value = { question: data.question, viewer: data.viewer };
      applyViewer(data.viewer, version);
      counts.likeCount = data.question.likeCount;
      counts.favoriteCount = data.question.favoriteCount;
      applyReplyCounts(data.question, countsVersion);
    } catch (e) {
      reportError(e, "加载全文失败");
    } finally {
      loadingContent.value = false;
      contentRequest = null;
    }
  })();
  return contentRequest;
}

async function loadThread() {
  if (loadingThread.value) return;
  loadingThread.value = true;
  error.value = "";
  const viewerId = user.value?.id;
  const version = interactionVersion;
  try {
    if (props.ownerMode) {
      await ensureContent();
      if (!detail.value) return;
      const data = detail.value;
      const items = detailThreadItems(data);
      thread.value = {
        items,
        viewer: data.viewer,
        answerCount: data.question.answerCount,
        followupCount: data.question.followupCount,
        replyCount: data.question.answerCount + data.question.followupCount,
      };
      return;
    }
    const countsVersion = ++replyCountsRequestVersion;
    const data = await api.getQuestionThread(props.item.id);
    if (viewerId !== user.value?.id) return;
    thread.value = data;
    applyViewer(data.viewer, version);
    applyReplyCounts(data, countsVersion);
  } catch (e) {
    reportError(e, "加载回答失败");
  } finally {
    loadingThread.value = false;
  }
}

async function measureExcerpt() {
  await nextTick();
  const element = excerptRef.value;
  if (!element || expanded.value) return;
  excerptOverflows.value = element.scrollHeight > element.clientHeight + 1;
}

function scheduleExcerptMeasurement() {
  if (excerptMeasureFrame !== undefined) return;
  excerptMeasureFrame = requestAnimationFrame(() => {
    excerptMeasureFrame = undefined;
    void measureExcerpt();
  });
}

watch(() => props.item.excerpt, () => {
  if (!expanded.value) void measureExcerpt();
});
watch(expanded, value => {
  if (!value) void measureExcerpt();
});

// 只切换正文折叠，卡片其余结构（作者/时间/操作行）保持同一套 DOM
async function toggleExpand() {
  if (expanded.value) {
    expanded.value = false;
    return;
  }
  expanded.value = true;
  if (!props.ownerMode) {
    await ensureContent();
  }
}

async function toggleThread() {
  if (threadOpen.value) {
    threadOpen.value = false;
    focusedComposer.value = null;
    return;
  }
  threadOpen.value = true;
  // 作者信息随回答区一起加载，不额外发请求；加载完成只改动底部信息行内容
  if (!props.ownerMode) {
    void ensureContent();
  }
  if (!thread.value) {
    await loadThread();
  }
}

async function toggleLike() {
  await toggleQuestionInteraction("like");
}

async function toggleFavorite() {
  await toggleQuestionInteraction("favorite");
}

async function toggleQuestionInteraction(type: "like" | "favorite") {
  if (!interactive.value || interacting.value || !requireLogin()) return;
  interacting.value = true;
  pendingInteraction.value = type;
  ++interactionVersion;
  try {
    await ensureContent();
    if (!content.value) return;
    const next = !(type === "like" ? liked.value : favorited.value);
    if (next) {
      await api.interact("question", props.item.id, type);
    } else {
      await api.uninteract("question", props.item.id, type);
    }
    // 早于本次写入发起的全文/讨论请求不能覆盖刚更新的选中状态。
    ++interactionVersion;
    if (type === "like") {
      liked.value = next;
      counts.likeCount = Math.max(0, counts.likeCount + (next ? 1 : -1));
      content.value.question.likeCount = counts.likeCount;
      content.value.viewer.liked = next;
    } else {
      favorited.value = next;
      counts.favoriteCount = Math.max(0, counts.favoriteCount + (next ? 1 : -1));
      content.value.question.favoriteCount = counts.favoriteCount;
      content.value.viewer.favorited = next;
    }
  } catch (e) {
    reportError(e, "操作失败");
  } finally {
    interacting.value = false;
    pendingInteraction.value = null;
  }
}

function withdrawEntry(entry: ThreadItem) {
  dialog.warning({
    title: entry.kind === "followup" ? "撤销追问" : "撤销回答",
    content: "撤销后该内容将不再展示，确定要撤销吗？",
    positiveText: "确定撤销",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        if (entry.kind === "followup") {
          await api.withdrawFollowup(props.item.id, entry.id);
        } else {
          await api.withdrawAnswer(entry.id);
        }
        message.success("已撤销");
        thread.value = null;
        counts.replyCount = Math.max(counts.replyCount - 1, 0);
        await loadThread();
      } catch (e) {
        reportError(e, "撤销失败");
      }
    },
  });
}

async function toggleAnswerLike(entry: ThreadItem) {
  if (!interactive.value || !requireLogin()) return;
  const next = !entry.liked;
  try {
    if (next) {
      await api.interact("answer", entry.id, "like");
    } else {
      await api.uninteract("answer", entry.id, "like");
    }
    entry.liked = next;
    entry.likeCount = Math.max(0, entry.likeCount + (next ? 1 : -1));
  } catch (e) {
    reportError(e, "操作失败");
  }
}

function report(targetType: "question" | "answer", targetId: number) {
  menuOpen.value = null;
  if (!requireLogin()) return;
  reportTarget.value = { targetType, targetId };
  reportOpen.value = true;
}

async function dislikeQuestion() {
  menuOpen.value = null;
  if (!requireLogin()) return;
  try {
    await api.interact("question", props.item.id, "dislike");
    message.success("已减少此类内容");
    emit("dismiss", props.item.id);
  } catch (e) {
    reportError(e, "操作失败");
  }
}

function toggleMenu(key: string) {
  menuOpen.value = menuOpen.value === key ? null : key;
}

function isThreadOpen(key: string): boolean {
  return openThreadKeys.value.includes(key);
}

function toggleThreadDetails(key: string) {
  openThreadKeys.value = isThreadOpen(key)
    ? openThreadKeys.value.filter((item) => item !== key) : [...openThreadKeys.value, key];
}

function openThread(key: string) {
  if (!isThreadOpen(key)) openThreadKeys.value = [...openThreadKeys.value, key];
}

function replyableFollowups(conversation: Conversation): ThreadItem[] {
  return conversation.messages.filter((item) => item.kind === "followup" && item.status === "published");
}

function replyTargetId(conversation: Conversation): number | null {
  const followups = replyableFollowups(conversation);
  const selected = selectedFollowups[conversation.key];
  if (selected !== undefined) return followups.some((item) => item.id === selected) ? selected : null;
  return followups.at(-1)?.id ?? null;
}

function selectReply(conversation: Conversation, followupId: number) {
  if (!replyableFollowups(conversation).some((item) => item.id === followupId)) return;
  if (replyTargetId(conversation) !== followupId && (threadDrafts[conversation.key] ?? "").trim()) {
    message.warning("请先发送或清空当前草稿，再切换要回复的追问。");
    return;
  }
  selectedFollowups[conversation.key] = followupId;
  openThread(conversation.key);
}

function changeReplyTarget(conversation: Conversation, event: Event) {
  const select = event.target as HTMLSelectElement;
  selectReply(conversation, Number(select.value));
  select.value = String(replyTargetId(conversation) ?? "");
}

function focusConversation(conversation: Conversation) {
  const id = replyTargetId(conversation);
  if (user.value?.role === "hr" && id != null) selectedFollowups[conversation.key] = id;
  onComposerFocus(`thread-${conversation.key}`);
}

function canWriteConversation(conversation: Conversation): boolean {
  if (!user.value) return false;
  if (user.value.role === "student") {
    return user.value.id === questionAuthorId.value && conversation.rootAnswer?.status === "published";
  }
  return user.value.role === "hr" && user.value.id === conversation.hrId && replyableFollowups(conversation).length > 0;
}

function conversationPlaceholder(): string {
  return user.value?.role === "hr" ? "回复选中的学生追问…" : "针对这条回答继续追问…";
}

function showSubmission(status: string): boolean {
  if (status === "published") { message.success("回答已发布"); return true; }
  if (status === "rejected") { message.error("回答未通过审核，请修改后重新提交。"); return false; }
  message.info("回答已提交，正在等待审核。");
  return true;
}

async function submitRootAnswer() {
  const body = rootDraft.value.trim();
  if (!body || submitting.value) return;
  if (!requireLogin()) return;
  submitting.value = true;
  error.value = "";
  try {
    const answer = await api.answer(props.item.id, body);
    if (showSubmission(answer.status)) rootDraft.value = "";
    await loadThread();
  } catch (e) {
    reportError(e, "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function submitThread(conversation: Conversation) {
  const body = (threadDrafts[conversation.key] ?? "").trim();
  if (!body || submitting.value) return;
  if (!requireLogin()) return;
  submitting.value = true;
  error.value = "";
  try {
    if (user.value?.role === "hr") {
      const followupId = replyTargetId(conversation);
      if (followupId == null) { message.warning("原追问当前不可回复，请清空草稿后重新选择追问。"); return; }
      const answer = await api.answerFollowup(props.item.id, followupId, body);
      if (!showSubmission(answer.status)) { await loadThread(); return; }
    } else {
      if (conversation.hrId == null || conversation.rootAnswer?.status !== "published") return;
      const followup = await api.followup(props.item.id, body, conversation.hrId, conversation.rootAnswer.id);
      if (followup.status === "rejected") {
        message.error("追问未通过审核，请修改内容后重新提交。");
        openThread(conversation.key);
        await loadThread();
        return;
      }
      if (followup.status !== "published") message.info("追问已提交，正在等待审核；通过后才会公开。");
    }
    threadDrafts[conversation.key] = "";
    openThread(conversation.key);
    await loadThread();
  } catch (e) {
    reportError(e, "提交失败");
  } finally {
    submitting.value = false;
  }
}

function closeMenu() {
  menuOpen.value = null;
}

let blurTimer: ReturnType<typeof setTimeout> | undefined;

function onComposerFocus(which: string) {
  if (blurTimer) {
    clearTimeout(blurTimer);
    blurTimer = undefined;
  }
  focusedComposer.value = which;
}

function onComposerBlur() {
  if (blurTimer) clearTimeout(blurTimer);
  // 立即隐藏会让下方内容上移，导致点击按钮时 mouseup 落在别的元素上
  blurTimer = setTimeout(() => {
    blurTimer = undefined;
    const active = document.activeElement;
    if (!(active instanceof HTMLTextAreaElement && active.classList.contains("composer-input"))) {
      focusedComposer.value = null;
    }
  }, 150);
}

onMounted(() => {
  document.addEventListener("click", closeMenu);
  if (excerptBlockRef.value) {
    excerptObserver = new ResizeObserver(() => {
      if (!expanded.value) scheduleExcerptMeasurement();
    });
    excerptObserver.observe(excerptBlockRef.value);
  }
  void measureExcerpt();
  // 本人列表及旧摘要没有 viewer，显示时读取服务端状态。
  if (user.value && props.item.viewer == null && interactive.value) void ensureContent();
});
onBeforeUnmount(() => {
  document.removeEventListener("click", closeMenu);
  excerptObserver?.disconnect();
  if (excerptMeasureFrame !== undefined) cancelAnimationFrame(excerptMeasureFrame);
  if (blurTimer) clearTimeout(blurTimer);
});
</script>

<template>
  <!-- 菜单打开时整卡提升层级，避免下拉菜单被后续卡片覆盖 -->
  <article
    class="feed-card"
    :class="{ 'relative z-30': menuOpen !== null, 'is-thread-open': threadOpen, 'feed-card--summary': !ownerMode }"
  >
    <div class="question-card">
      <div class="card-meta">
        <div class="topic-tags" aria-label="问题标签">
          <span v-for="tag in !ownerMode || expanded ? item.tags : item.tags.slice(0, 3)" :key="tag" class="topic-tag">{{ tag }}</span>
          <button v-if="ownerMode && !expanded && item.tags.length > 3" type="button" class="topic-tag topic-tag-more" aria-label="展开全部问题标签和全文" @click="toggleExpand">+{{ item.tags.length - 3 }}</button>
          <span v-if="!item.tags.length" class="topic-tag">公开问答</span>
        </div>
        <time v-if="!ownerMode" class="card-time" :datetime="createdAt" :title="formatRelativeTime(createdAt)">{{ formatTime(createdAt) }}</time>
      </div>

      <div class="title-row">
        <h2 class="question-heading">
          <router-link :to="detailLink" class="question-title-link">{{ item.title }}</router-link>
        </h2>
        <span
          v-if="statusMeta"
          class="status-badge"
          :class="`status-badge--${statusMeta.tone}`"
        >{{ statusMeta.text }}</span>
      </div>

      <p v-if="!ownerMode && item.matchedTags.length" class="recommendation-reason">
        <span aria-hidden="true"></span>因为你关注「{{ item.matchedTags.join("、") }}」
      </p>

      <div ref="excerptBlockRef" class="excerpt-block">
        <p ref="excerptRef" class="question-excerpt" :class="expanded ? 'question-excerpt--full' : 'line-clamp-3'">{{ expanded ? content?.question.body ?? item.excerpt : item.excerpt }}</p>
        <div v-if="ownerMode || expanded || excerptOverflows" class="flex justify-end">
          <button type="button" class="aux-btn read-more" @click="toggleExpand">
            {{ expanded ? "收起问题 ↑" : ownerMode ? "阅读全文 ↓" : "展开问题 ↓" }}
          </button>
        </div>
      </div>

      <div v-if="ownerMode || (expanded && authorName)" class="author-row">
        <span v-if="!ownerMode && authorName" class="min-w-0 truncate">{{ authorName }}</span>
        <time v-if="ownerMode" class="shrink-0 whitespace-nowrap">提问于 {{ formatTime(createdAt) }}</time>
      </div>

      <div class="question-footer">
        <button v-if="ownerMode" type="button" class="discussion-toggle" :aria-expanded="threadOpen" aria-label="查看或参与回答" @click="toggleThread">
          <FeedIcon name="comment" :size="18" />
          <span>{{ replyLabel }}</span>
          <span class="discussion-arrow" aria-hidden="true">{{ threadOpen ? "↑" : "→" }}</span>
        </button>
        <router-link v-else :to="detailLink" class="discussion-toggle" aria-label="查看问答详情">
          <FeedIcon name="comment" :size="18" />
          <span v-if="counts.answerCount || counts.followupCount">{{ replyLabel }}</span>
          <span class="detail-entry-label">查看详情</span>
          <span class="discussion-arrow" aria-hidden="true">→</span>
        </router-link>

        <div class="question-actions">
          <button
            v-if="ownerMode && withdrawable"
            class="feed-action"
            aria-label="撤回提问"
            @click="emit('withdraw')"
          >
            <span>撤回</span>
          </button>
          <button
            class="feed-action"
            :class="{ 'is-active': liked, 'is-disabled': !interactive, 'is-pending': pendingInteraction === 'like' }"
            :disabled="!interactive || interacting"
            :aria-pressed="liked"
            :aria-busy="pendingInteraction === 'like'"
            aria-label="点赞"
            @click="toggleLike"
          >
            <FeedIcon name="like" :filled="liked" />
            <span>{{ counts.likeCount }}</span>
          </button>
          <button
            class="feed-action"
            :class="{ 'is-active': favorited, 'is-disabled': !interactive, 'is-pending': pendingInteraction === 'favorite' }"
            :disabled="!interactive || interacting"
            :aria-pressed="favorited"
            :aria-busy="pendingInteraction === 'favorite'"
            aria-label="收藏"
            @click="toggleFavorite"
          >
            <FeedIcon name="star" :filled="favorited" />
            <span>{{ counts.favoriteCount }}</span>
          </button>
          <div v-if="!ownerMode" class="relative flex items-center">
            <button class="feed-action more-trigger" aria-label="更多" @click.stop="toggleMenu('question')">
              <FeedIcon name="dots" />
            </button>
            <div v-if="menuOpen === 'question'" class="more-menu" @click.stop>
              <button class="more-item" @click="dislikeQuestion">不感兴趣</button>
              <button class="more-item" @click="report('question', item.id)">举报</button>
            </div>
          </div>
        </div>
      </div>

      <p v-if="error" class="m-0 mt-3 text-14 text-danger">{{ error }}</p>
    </div>

    <!-- 回答区与问题共用一张卡片，通过分割线和浅色背景区分层级 -->
    <div v-if="ownerMode && threadOpen" class="drawer-panel answer-panel">
      <!-- HR 主回答输入框 -->
      <div v-if="user?.role === 'hr'" class="flex flex-col gap-1 p-4">
        <textarea
          v-model="rootDraft"
          rows="1"
          class="composer-input"
          placeholder="以 HR 身份回答…"
          @focus="onComposerFocus('root')"
          @blur="onComposerBlur"
        />
        <div v-if="focusedComposer === 'root'" class="mt-2 flex justify-end">
          <button
            class="primary-btn"
            :disabled="!rootDraft.trim() || submitting"
            @mousedown.prevent
            @click="submitRootAnswer"
          >
            {{ submitting ? "提交中…" : "发布回答" }}
          </button>
        </div>
      </div>

      <!-- HR 列表 + 展开后的线程 -->
      <div class="flex flex-col px-4 pb-6 pt-6">
        <p v-if="loadingThread" class="m-0 text-14 text-muted">正在加载回答…</p>
        <p v-else-if="thread && conversations.length === 0" class="m-0 text-14 text-muted">
          还没有回答，来写下第一条经验吧。
        </p>

        <div v-else-if="thread" class="flex flex-col gap-2">
          <p class="m-0 text-14 leading-[22px] text-ink">{{ publishedRootCount }} 条主回答 · {{ participatingHrCount }} 位 HR 参与</p>

          <div class="flex flex-col gap-1">
            <div v-for="conversation in conversations" :key="conversation.key" class="answer-thread flex flex-col" :data-answer-id="conversation.rootAnswerId ?? undefined">
              <!-- HRClose / HROpen -->
              <button
                type="button"
                class="hr-row"
                :title="conversation.nickname"
                :aria-expanded="isThreadOpen(conversation.key)"
                @click="toggleThreadDetails(conversation.key)"
              >
                <span class="hr-avatar" aria-hidden="true">{{ conversation.nickname.trim().slice(0, 1) }}</span>
                <span class="hr-name">{{ conversation.nickname }}</span>
                <span class="hr-identity">HR</span>
                <span class="hr-chips">
                  <span v-if="conversation.company" class="hr-chip">{{ conversation.company }}</span>
                  <span v-if="conversation.position" class="hr-chip">{{ conversation.position }}</span>
                </span>
                <span
                  v-if="!isThreadOpen(conversation.key)"
                  class="hr-expand-label shrink-0 whitespace-nowrap"
                >展开追问（{{ conversation.messages.filter((item) => item.kind === 'followup').length }}）</span>
              </button>

              <!-- HRA&UserQ：首回复卡与追问气泡区之间 32px -->
              <div class="flex flex-col gap-4 pt-2">
                <!-- HR 对主问题的回答 -->
                <article class="flex flex-col">
                  <p v-if="conversation.rootAnswer.status === 'removed'" class="m-0 text-14 text-muted">{{ removedAnswerText(conversation.rootAnswer) }}相关追问与回复保留。</p>
                  <p v-else class="m-0 whitespace-pre-wrap text-16 leading-6 text-ink">{{ conversation.rootAnswer.body }}</p>
                  <span v-if="conversation.rootAnswer.status !== 'published' && conversation.rootAnswer.status !== 'removed'" class="text-14 text-muted">{{ STATUS_META[conversation.rootAnswer.status]?.text }}</span>
                  <div class="mt-2 flex items-center justify-between gap-4">
                    <span class="text-14 leading-[22px] text-muted">
                      回答于 {{ formatTime(conversation.rootAnswer.createdAt) }}
                    </span>
                    <div v-if="conversation.rootAnswer.status !== 'removed'" class="flex shrink-0 items-center gap-4">
                      <button
                        v-if="user && conversation.rootAnswer.author.id === user.id && conversation.rootAnswer.status !== 'rejected'"
                        class="feed-action"
                        aria-label="撤销"
                        @click="withdrawEntry(conversation.rootAnswer)"
                      >
                        <span>撤销</span>
                      </button>
                      <button
                        v-if="conversation.rootAnswer.status === 'published'"
                        class="feed-action"
                        :class="{ 'is-active': conversation.rootAnswer.liked }"
                        aria-label="点赞"
                        @click="toggleAnswerLike(conversation.rootAnswer)"
                      >
                        <FeedIcon name="like" :filled="conversation.rootAnswer.liked" />
                        <span>{{ conversation.rootAnswer.likeCount }}</span>
                      </button>
                      <div v-if="!ownerMode && conversation.rootAnswer.status === 'published'" class="relative flex items-center">
                        <button class="feed-action" aria-label="更多" @click.stop="toggleMenu(conversation.rootAnswer.key)">
                          <FeedIcon name="dots" />
                        </button>
                        <div v-if="menuOpen === conversation.rootAnswer.key" class="more-menu" @click.stop>
                          <button class="more-item" @click="report('answer', conversation.rootAnswer.id)">举报</button>
                        </div>
                      </div>
                    </div>
                  </div>
                </article>

                <template v-if="isThreadOpen(conversation.key)">
                  <!-- BubbleCard：追问气泡整体一块，内部保持 8px 间距 -->
                  <div v-if="conversation.messages.length" class="flex flex-col gap-2">
                    <!-- 追问气泡：题主在右、HR 在左；时间与点赞仅悬停显示，占位保持 22px -->
                    <div
                      v-for="entry in conversation.messages"
                      :key="entry.key"
                      class="group thread-entry"
                      :class="entry.kind === 'followup' ? 'thread-entry--followup' : 'thread-entry--answer'"
                    >
                      <span class="thread-author">
                        {{ entry.kind === 'followup' ? '学生追问' : 'HR 回复' }} · {{ entry.author.nickname }}
                      </span>
                      <span v-if="entry.kind === 'followup_answer'" class="reply-context">回复追问：{{ Array.from(conversation.messages.find((item) => item.kind === 'followup' && item.id === entry.followupId)?.body || '原追问已不可见').slice(0, 36).join('') }}</span>
                      <p class="thread-message">{{ entry.status === 'removed' ? '这条追问已撤销或下架。' : entry.body }}</p>
                      <span v-if="entry.status !== 'published' && entry.status !== 'removed'" class="text-14 text-muted">{{ STATUS_META[entry.status]?.text }}</span>
                      <button v-if="entry.kind === 'followup' && entry.status === 'published' && user?.role === 'hr' && user.id === conversation.hrId" type="button" class="feed-action thread-reply-action" @click="selectReply(conversation, entry.id)">回复这条追问</button>
                      <div class="thread-meta">
                        <span class="text-14 leading-[22px] text-muted">{{ formatTime(entry.createdAt) }}</span>
                        <button
                          v-if="user && entry.author.id === user.id && ['published', 'pending_machine', 'pending_human'].includes(entry.status)"
                          class="feed-action"
                          aria-label="撤销"
                          @click="withdrawEntry(entry)"
                        >
                          <span>撤销</span>
                        </button>
                        <template v-if="entry.kind === 'followup_answer' && entry.status === 'published'">
                          <button
                            class="feed-action"
                            :class="{ 'is-active': entry.liked }"
                            aria-label="点赞"
                            @click="toggleAnswerLike(entry)"
                          >
                            <FeedIcon name="like" :filled="entry.liked" />
                            <span>{{ entry.likeCount }}</span>
                          </button>
                          <div v-if="!ownerMode" class="relative flex items-center">
                            <button class="feed-action" aria-label="更多" @click.stop="toggleMenu(entry.key)">
                              <FeedIcon name="dots" />
                            </button>
                            <div v-if="menuOpen === entry.key" class="more-menu" @click.stop>
                              <button class="more-item" @click="report('answer', entry.id)">举报</button>
                            </div>
                          </div>
                        </template>
                      </div>
                    </div>
                  </div>

                  <!-- 线程输入框：题主追问；该 HR 回复最新一条追问 -->
                  <div v-if="canWriteConversation(conversation)" class="flex flex-col gap-1">
                    <label v-if="user?.role === 'hr'" class="text-14 text-muted">
                      回复对象
                      <select :value="replyTargetId(conversation) ?? ''" class="composer-input" aria-label="选择要回复的学生追问" @change="changeReplyTarget(conversation, $event)">
                        <option v-if="replyTargetId(conversation) == null" value="" disabled>原追问已不可回复，请重新选择</option>
                        <option v-for="followup in replyableFollowups(conversation)" :key="followup.id" :value="followup.id">{{ Array.from(followup.body).slice(0, 36).join('') }}{{ Array.from(followup.body).length > 36 ? '…' : '' }}</option>
                      </select>
                    </label>
                    <textarea
                      v-model="threadDrafts[conversation.key]"
                      rows="1"
                      class="composer-input"
                      :placeholder="conversationPlaceholder()"
                      @focus="focusConversation(conversation)"
                      @blur="onComposerBlur"
                    />
                    <div v-if="focusedComposer === `thread-${conversation.key}`" class="mt-2 flex justify-end">
                      <button
                        class="primary-btn"
                        :disabled="!(threadDrafts[conversation.key] ?? '').trim() || submitting"
                        @mousedown.prevent
                        @click="submitThread(conversation)"
                      >
                        {{ submitting ? "提交中…" : "发送" }}
                      </button>
                    </div>
                  </div>

                  <!-- 展开态末尾：收起该回答的追问（Figma Frame 38，右对齐 14/22） -->
                  <div class="flex h-[22px] items-center justify-end">
                    <button type="button" class="hr-collapse" @click="toggleThreadDetails(conversation.key)">收起追问</button>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <ReportDialog
      v-if="reportOpen"
      v-model:show="reportOpen"
      :target-type="reportTarget.targetType"
      :target-id="reportTarget.targetId"
    />
  </article>
</template>

<style scoped>
.feed-card {
  position: relative;
  border: 1px solid var(--color-border-default);
  border-radius: 12px;
  background: var(--color-bg-surface);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

@media (hover: hover) {
  .feed-card:hover {
    border-color: #d4dce8;
    box-shadow: 0 8px 24px -18px rgba(23, 32, 51, 0.45);
    transform: translateY(-1px);
  }
}

.question-card {
  padding: 20px 22px 12px;
  border-radius: 12px;
  background: var(--color-bg-surface);
}

.card-meta,
.title-row,
.question-footer,
.author-row,
.question-actions {
  display: flex;
  align-items: center;
}

.card-meta {
  min-width: 0;
  justify-content: space-between;
  gap: 12px;
}

.topic-tags {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}

.topic-tag {
  flex-shrink: 0;
  max-width: 140px;
  overflow: hidden;
  border-radius: 5px;
  background: var(--color-bg-hover);
  padding: 3px 8px;
  color: var(--color-text-regular);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-tag-more {
  border: 0;
  font-family: inherit;
  cursor: pointer;
}

.topic-tag-more:hover {
  color: var(--color-brand);
  background: var(--color-brand-soft);
}

.card-time {
  flex-shrink: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.title-row {
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.question-heading {
  min-width: 0;
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
  font-weight: 650;
  line-height: 30px;
  overflow-wrap: anywhere;
}

.question-title-link {
  color: inherit;
  text-decoration: none;
  transition: color 0.15s ease;
}

.question-title-link:hover {
  color: var(--color-brand);
}

.status-badge {
  flex-shrink: 0;
  margin-top: 3px;
  border-radius: 5px;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
}

.status-badge--success { background: var(--color-success-soft); color: var(--color-success); }
.status-badge--warning { background: var(--color-warning-soft); color: var(--color-warning); }
.status-badge--danger { background: var(--color-danger-soft); color: var(--color-danger); }
.status-badge--neutral { background: var(--color-bg-hover); color: var(--color-text-muted); }

.recommendation-reason {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 9px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.recommendation-reason > span {
  width: 5px;
  height: 5px;
  flex: 0 0 5px;
  border-radius: 50%;
  background: var(--color-brand);
}

.excerpt-block {
  margin-top: 10px;
}

.question-excerpt {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 15px;
  line-height: 26px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.question-excerpt--full {
  color: var(--color-text-primary);
  animation: excerpt-reveal 180ms ease-out;
}

@keyframes excerpt-reveal {
  from { opacity: 0.45; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.read-more {
  min-height: 32px;
  padding: 5px 0 0;
  color: var(--color-brand);
  font-size: 13px;
}

.read-more:hover {
  color: var(--color-brand-dark);
}

.author-row {
  min-width: 0;
  gap: 12px;
  margin-top: 10px;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 20px;
}

.question-footer {
  min-width: 0;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  border-top: 1px solid var(--color-border-subtle);
  padding-top: 10px;
}

.discussion-toggle {
  display: inline-flex;
  min-width: 0;
  min-height: 36px;
  align-items: center;
  gap: 7px;
  border: 0;
  border-radius: 7px;
  background: var(--color-bg-hover);
  padding: 7px 10px;
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 22px;
  text-align: left;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.feed-card--summary .discussion-toggle {
  flex-wrap: wrap;
  border: 1px solid var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-text-regular);
  text-decoration: none;
}

.feed-card--summary .discussion-toggle:hover {
  background: #e3edff;
  color: var(--color-brand);
}

.detail-entry-label {
  color: var(--color-brand);
  font-weight: 650;
  white-space: nowrap;
}

.feed-action.is-active svg {
  animation: action-pop 180ms ease-out;
}

.feed-action.is-pending {
  opacity: 0.65;
  cursor: progress;
}

.feed-action.is-pending svg {
  animation: action-pulse 700ms ease-in-out infinite;
}

@keyframes action-pop {
  0% { transform: scale(0.82); }
  70% { transform: scale(1.12); }
  100% { transform: scale(1); }
}

@keyframes action-pulse {
  0%, 100% { opacity: 0.5; transform: scale(0.92); }
  50% { opacity: 1; transform: scale(1); }
}

.discussion-toggle:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.discussion-arrow {
  color: var(--color-brand);
  font-size: 15px;
}

.question-actions {
  flex-shrink: 0;
  gap: 2px;
}

.more-trigger {
  min-width: 34px;
}

.answer-panel {
  position: relative;
  border-top: 1px solid var(--color-border-default);
  border-radius: 0 0 12px 12px;
  background: var(--color-bg-feed);
}

.answer-thread {
  margin-top: 10px;
  border-top: 1px solid var(--color-border-default);
  padding: 18px 0 8px;
}

.hr-row {
  width: 100%;
  min-width: 0;
  gap: 9px;
  border-radius: 8px;
  padding: 9px 10px;
}

.hr-row:hover {
  background: var(--color-bg-hover);
}

.hr-avatar {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 13px;
  font-weight: 650;
}

.hr-name {
  flex: 0 1 auto;
  min-width: 0;
  max-width: min(45%, 220px);
  overflow: hidden;
  color: var(--color-text-primary);
  font-weight: 600;
  text-overflow: ellipsis;
}

.hr-identity {
  flex-shrink: 0;
  border-radius: 4px;
  background: var(--color-brand-soft);
  padding: 1px 5px;
  color: var(--color-brand);
  font-size: 11px;
  line-height: 17px;
}

.hr-expand-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  border: 1px solid var(--color-brand-border);
  border-radius: 6px;
  background: var(--color-brand-soft);
  padding: 3px 9px;
  color: var(--color-brand);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.hr-expand-label::after {
  content: "↓";
  font-size: 14px;
}

.hr-row:hover .hr-expand-label {
  border-color: #bdd2f6;
  background: #e3edff;
}

.thread-entry {
  position: relative;
  border: 1px solid var(--color-border-subtle);
  border-left: 3px solid #cbd5e1;
  border-radius: 8px;
  background: var(--color-bg-surface);
  padding: 11px 13px;
}

.thread-entry--followup {
  background: #f7f8fa;
}

.thread-entry--answer {
  border-left-color: var(--color-brand);
}

.thread-author {
  color: var(--color-text-regular);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.reply-context {
  display: block;
  margin-top: 3px;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.thread-message {
  margin: 5px 0 0;
  color: var(--color-text-primary);
  font-size: 15px;
  line-height: 25px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.thread-meta {
  display: flex;
  min-height: 30px;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
}

.thread-meta .feed-action,
.thread-reply-action {
  min-height: 28px;
  padding: 3px 6px;
  font-size: 12px;
}

.thread-reply-action {
  margin-top: 4px;
  padding-left: 0;
  padding-right: 0;
}

@media (max-width: 640px) {
  .question-card {
    padding: 16px 16px 10px;
  }

  .question-heading {
    font-size: 19px;
    line-height: 28px;
  }

  .question-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .feed-card--summary .question-footer {
    align-items: center;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .feed-card--summary .question-actions {
    margin-left: auto;
  }

  .discussion-toggle {
    justify-content: flex-start;
  }

  .discussion-arrow {
    margin-left: auto;
  }

  .question-actions {
    justify-content: space-between;
  }

  .question-actions .feed-action {
    min-width: 44px;
    min-height: 40px;
  }

  .hr-row {
    flex-wrap: wrap;
  }

  .hr-expand-label {
    width: auto;
    margin-left: 41px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .feed-card {
    transition: none;
  }

  .feed-card:hover {
    transform: none;
  }

  .question-excerpt--full,
  .feed-action.is-active svg,
  .feed-action.is-pending svg {
    animation: none;
  }
}
</style>
