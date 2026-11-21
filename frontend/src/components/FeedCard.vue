<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
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
} from "@/api";
import { useUserStore } from "@/stores/user";
import FeedIcon from "./FeedIcon.vue";
import { buildAnswerThreads, detailThreadItems, type AnswerThread as Conversation } from "@/utils/answerThreads";
import { removedAnswerText } from "@/utils/answerRemoval";

const props = defineProps<{
  item: QuestionFeedItem;
  /** 我的问答列表：问题状态，用于标题右侧状态标签 */
  status?: string;
  /** 我的问答列表：作者视角，隐藏编辑器并将提问时间常驻 */
  ownerMode?: boolean;
  /** 我的问答列表：是否显示「撤回」操作 */
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

const counts = reactive({
  likeCount: props.item.likeCount,
  favoriteCount: props.item.favoriteCount,
  replyCount: props.item.replyCount,
});
const liked = ref(false);
const favorited = ref(false);

watch(
  () => props.item,
  (next) => {
    counts.likeCount = next.likeCount;
    counts.favoriteCount = next.favoriteCount;
    counts.replyCount = next.replyCount;
  },
);

const authorName = computed(() => content.value?.question.author.nickname ?? "");
const createdAt = computed(() => content.value?.question.createdAt ?? props.item.createdAt);
const replyLabel = computed(() => {
  if (counts.replyCount > 0) return `${counts.replyCount} 条问答互动`;
  return props.ownerMode ? "暂无评论" : "我来回答";
});
const questionAuthorId = computed(() => content.value?.question.author.id ?? null);

const conversations = computed(() => buildAnswerThreads(thread.value?.items ?? []));
const publishedRootCount = computed(() => conversations.value.filter((item) => item.rootAnswer?.status === "published").length);
const participatingHrCount = computed(() => new Set(conversations.value.filter((item) => item.rootAnswer?.status === "published").map((item) => item.hrId)).size);

const STATUS_META: Record<string, { text: string; color: string }> = {
  published: { text: "已发表", color: "#34C759" },
  pending_machine: { text: "待审核", color: "#FF383C" },
  pending_human: { text: "待审核", color: "#FF383C" },
  rejected: { text: "未通过", color: "var(--color-danger)" },
  removed: { text: "已撤回", color: "var(--color-text-muted)" },
};

const statusMeta = computed(() => {
  if (!props.status) return null;
  return STATUS_META[props.status] ?? { text: props.status, color: "var(--color-text-muted)" };
});

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
  if (content.value || loadingContent.value) return;
  loadingContent.value = true;
  error.value = "";
  try {
    if (props.ownerMode) {
      // 作者视角走 detail，允许查看自己审核中的问题正文与回答。
      const data = await api.getQuestion(props.item.id);
      detail.value = data;
      content.value = { question: data.question, viewer: data.viewer };
      liked.value = data.viewer.liked;
      favorited.value = data.viewer.favorited;
      counts.likeCount = data.question.likeCount;
      counts.favoriteCount = data.question.favoriteCount;
      counts.replyCount = data.question.answerCount + data.question.followupCount;
      return;
    }
    const data = await api.getQuestionContent(props.item.id);
    content.value = data;
    liked.value = data.viewer.liked;
    favorited.value = data.viewer.favorited;
    counts.likeCount = data.question.likeCount;
    counts.favoriteCount = data.question.favoriteCount;
    counts.replyCount = data.question.answerCount + data.question.followupCount;
  } catch (e) {
    reportError(e, "加载全文失败");
  } finally {
    loadingContent.value = false;
  }
}

async function loadThread() {
  if (loadingThread.value) return;
  loadingThread.value = true;
  error.value = "";
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
    const data = await api.getQuestionThread(props.item.id);
    thread.value = data;
    liked.value = data.viewer.liked;
    favorited.value = data.viewer.favorited;
    counts.replyCount = data.replyCount;
  } catch (e) {
    reportError(e, "加载回答失败");
  } finally {
    loadingThread.value = false;
  }
}

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
  if (!interactive.value || !requireLogin()) return;
  await ensureContent();
  if (!content.value) return;
  const next = !liked.value;
  try {
    if (next) {
      await api.interact("question", props.item.id, "like");
    } else {
      await api.uninteract("question", props.item.id, "like");
    }
    liked.value = next;
    counts.likeCount = Math.max(0, counts.likeCount + (next ? 1 : -1));
    content.value.question.likeCount = counts.likeCount;
  } catch (e) {
    reportError(e, "操作失败");
  }
}

async function toggleFavorite() {
  if (!interactive.value || !requireLogin()) return;
  await ensureContent();
  if (!content.value) return;
  const next = !favorited.value;
  try {
    if (next) {
      await api.interact("question", props.item.id, "favorite");
    } else {
      await api.uninteract("question", props.item.id, "favorite");
    }
    favorited.value = next;
    counts.favoriteCount = Math.max(0, counts.favoriteCount + (next ? 1 : -1));
    content.value.question.favoriteCount = counts.favoriteCount;
  } catch (e) {
    reportError(e, "操作失败");
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

async function report(targetType: "question" | "answer", targetId: number) {
  menuOpen.value = null;
  if (!requireLogin()) return;
  try {
    await api.report(targetType, targetId);
    message.success("举报已提交，我们会尽快处理");
  } catch (e) {
    reportError(e, "举报失败");
  }
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
      await api.followup(props.item.id, body, conversation.hrId, conversation.rootAnswer.id);
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

onMounted(() => document.addEventListener("click", closeMenu));
onBeforeUnmount(() => {
  document.removeEventListener("click", closeMenu);
  if (blurTimer) clearTimeout(blurTimer);
});
</script>

<template>
  <!-- 菜单打开时整卡提升层级，避免下拉菜单被后续卡片覆盖 -->
  <article
    class="flex flex-col gap-4 rounded-card-lg border border-solid border-line bg-surface py-4"
    :class="{ 'relative z-30': menuOpen !== null }"
  >
    <!-- 问题卡片（Right）：回答区展开时才抬起，向下投影落在回答卡上；clip-path 裁掉上方与左右，只留向下的阴影 -->
    <div
      class="relative z-10 flex flex-col gap-2 rounded-card-lg bg-surface px-4"
      :class="threadOpen ? 'border-b border-line pb-4 shadow-float [border-bottom-style:solid] [clip-path:inset(0_0_-200px_0)]' : ''"
    >
      <div class="flex items-start justify-between gap-4">
        <h2 class="m-0 text-[22px] font-bold leading-[30px] text-ink sm:text-24 sm:leading-8">{{ item.title }}</h2>
        <span
          v-if="statusMeta"
          class="shrink-0 pt-1 text-14 font-medium"
          :style="{ color: statusMeta.color }"
        >{{ statusMeta.text }}</span>
      </div>

      <p v-if="!ownerMode && item.matchedTags.length" class="m-0 text-12 text-muted">
        因为你关注「{{ item.matchedTags.join("、") }}」
      </p>

      <div class="flex flex-col gap-2.5">
        <p v-if="!expanded" class="m-0 line-clamp-4 whitespace-pre-wrap text-16 leading-6 text-ink">{{ item.excerpt }}</p>
        <p v-else class="m-0 whitespace-pre-wrap text-16 leading-6 text-ink">{{ content?.question.body ?? item.excerpt }}</p>
        <div class="flex justify-end">
          <button class="aux-btn" @click="toggleExpand">{{ expanded ? "收起 ↑" : "阅读全文 ↓" }}</button>
        </div>
        <!-- TagSet：仅在阅读全文时展示 -->
        <div v-if="expanded && item.tags.length" class="flex min-w-0 flex-nowrap items-center gap-2 overflow-hidden">
          <span
            v-for="tag in item.tags"
            :key="tag"
            class="shrink-0 whitespace-nowrap rounded-[4px] bg-[#f8f9fa] px-1 py-0.5 text-[12px] leading-[18px] text-[#4b5563]"
          >{{ tag }}</span>
        </div>
      </div>

      <!-- 底部信息行：折叠态只留操作按钮；展开全文后才出现作者与提问时间，行高始终 22px -->
      <div
        class="flex min-w-0 items-center gap-4"
        :class="expanded || ownerMode ? 'justify-between' : 'justify-end'"
      >
        <div v-if="expanded || ownerMode" class="flex min-w-0 flex-1 items-center gap-2.5 text-14">
          <span v-if="!ownerMode && authorName" class="min-w-0 truncate text-regular">{{ authorName }}</span>
          <time class="shrink-0 whitespace-nowrap text-muted">提问于 {{ formatTime(createdAt) }}</time>
        </div>

        <div class="flex shrink-0 items-center gap-4">
          <button
            v-if="ownerMode && withdrawable"
            class="feed-action"
            aria-label="撤回提问"
            @click="emit('withdraw')"
          >
            <span>撤回</span>
          </button>
          <button class="feed-action" aria-label="回答" @click="toggleThread">
            <FeedIcon name="comment" />
            <span>{{ threadOpen ? "收起回答" : replyLabel }}</span>
          </button>
          <button
            class="feed-action"
            :class="{ 'is-active': liked, 'is-disabled': !interactive }"
            :disabled="!interactive"
            aria-label="点赞"
            @click="toggleLike"
          >
            <FeedIcon name="like" :filled="liked" />
            <span>{{ counts.likeCount }}</span>
          </button>
          <button
            class="feed-action"
            :class="{ 'is-active': favorited, 'is-disabled': !interactive }"
            :disabled="!interactive"
            aria-label="收藏"
            @click="toggleFavorite"
          >
            <FeedIcon name="star" :filled="favorited" />
            <span>{{ counts.favoriteCount }}</span>
          </button>
          <div v-if="!ownerMode" class="relative flex items-center">
            <button class="feed-action" aria-label="更多" @click.stop="toggleMenu('question')">
              <FeedIcon name="dots" />
            </button>
            <div v-if="menuOpen === 'question'" class="more-menu" @click.stop>
              <button class="more-item" @click="dislikeQuestion">不感兴趣</button>
              <button class="more-item" @click="report('question', item.id)">举报</button>
            </div>
          </div>
        </div>
      </div>

      <p v-if="error" class="m-0 text-14 text-danger">{{ error }}</p>
    </div>

    <!-- 回答区（Wrapper）：上移 8px 叠在问题卡下方，展开时从卡片底下抽出 -->
    <div v-if="threadOpen" class="drawer-panel relative z-0 -mt-6 flex flex-col rounded-card-lg bg-surface pt-2">
      <!-- 游客提示 -->
      <div v-if="!user" class="p-4">
        <div class="composer-guest">
          <router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">登录</router-link>
          后参与回答和追问
        </div>
      </div>
      <!-- HR 主回答输入框 -->
      <div v-else-if="user.role === 'hr'" class="flex flex-col gap-1 p-4">
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
                <span class="hr-name">HR · {{ conversation.nickname }}</span>
                <span class="hr-chips">
                  <span v-if="conversation.company" class="hr-chip">{{ conversation.company }}</span>
                  <span v-if="conversation.position" class="hr-chip">{{ conversation.position }}</span>
                </span>
                <span
                  v-if="!isThreadOpen(conversation.key)"
                  class="shrink-0 whitespace-nowrap text-14 leading-[22px] text-muted"
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
                      class="group flex flex-col"
                      :class="entry.kind === 'followup' ? 'items-end' : 'items-start'"
                    >
                      <span class="text-14 leading-[22px] text-regular">
                        {{ entry.kind === 'followup' ? '学生' : 'HR' }} · {{ entry.author.nickname }}
                      </span>
                      <span v-if="entry.kind === 'followup_answer'" class="text-12 text-muted">回复追问：{{ Array.from(conversation.messages.find((item) => item.kind === 'followup' && item.id === entry.followupId)?.body || '原追问已不可见').slice(0, 36).join('') }}</span>
                      <p
                        class="m-0 mt-1 max-w-[500px] whitespace-pre-wrap rounded-card px-2 py-2 text-16 leading-6 text-ink"
                        :class="entry.kind === 'followup' ? 'bg-hover' : 'bg-[#e6f0ff]'"
                      >{{ entry.status === 'removed' ? '这条追问已撤销或下架。' : entry.body }}</p>
                      <span v-if="entry.status !== 'published' && entry.status !== 'removed'" class="text-14 text-muted">{{ STATUS_META[entry.status]?.text }}</span>
                      <button v-if="entry.kind === 'followup' && entry.status === 'published' && user?.role === 'hr' && user.id === conversation.hrId" type="button" class="feed-action" @click="selectReply(conversation, entry.id)">回复这条追问</button>
                      <div
                        class="mt-1 flex h-[22px] items-center gap-4 opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100"
                        :class="entry.kind === 'followup' ? 'justify-end' : 'justify-start'"
                      >
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
  </article>
</template>

<style scoped>
.answer-thread { margin-top: 12px; padding: 18px 0; border-top: 1px solid var(--color-border-default); }
</style>
