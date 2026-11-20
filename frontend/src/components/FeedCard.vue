<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useMessage } from "naive-ui";
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

const props = defineProps<{
  item: QuestionFeedItem;
  /** 我的问答列表：问题状态，用于标题右侧状态标签 */
  status?: string;
  /** 我的问答列表：作者视角，隐藏编辑器并将提问时间常驻 */
  ownerMode?: boolean;
}>();

const emit = defineEmits<{ (e: "dismiss", id: number): void }>();

const route = useRoute();
const router = useRouter();
const message = useMessage();
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
const composerText = ref("");
const composerFocused = ref(false);
const topComposerRef = ref<HTMLTextAreaElement | null>(null);
// v-for 内的 template ref 会被 Vue 收集成数组；同一时刻只渲染一个回复框
const replyComposerRef = ref<HTMLTextAreaElement[] | null>(null);
const replyTargetKey = ref<string | null>(null);
const draftBeforeReply = ref("");
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
  if (counts.replyCount > 0) return `${counts.replyCount} 条回答`;
  return props.ownerMode ? "暂无评论" : "我来回答";
});

const STATUS_META: Record<string, { text: string; color: string }> = {
  published: { text: "已发表", color: "#34C759" },
  public_archived: { text: "已发表", color: "#34C759" },
  pending_machine: { text: "待审核", color: "#FF383C" },
  pending_human: { text: "待审核", color: "#FF383C" },
  public_pending: { text: "待审核", color: "#FF383C" },
  rejected: { text: "未通过", color: "var(--color-danger)" },
  public_rejected: { text: "未通过", color: "var(--color-danger)" },
  open: { text: "待认领", color: "var(--color-text-muted)" },
  assigned: { text: "进行中", color: "var(--color-brand)" },
  closed: { text: "已结单", color: "var(--color-text-muted)" },
};

const statusMeta = computed(() => {
  if (!props.status || props.status === "removed") return null;
  return STATUS_META[props.status] ?? { text: props.status, color: "var(--color-text-muted)" };
});

// 审核中/未通过的问题不能点赞收藏，按作者视角展示计数但禁用操作
const interactive = computed(
  () =>
    !props.ownerMode ||
    !["pending_machine", "pending_human", "public_pending", "rejected", "removed"].includes(props.status ?? ""),
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
      // 作者视角走 detail：审核中/私密问题也能拿到正文与回答
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
      if (!detail.value) {
        await ensureContent();
      }
      if (!detail.value) return;
      const data = detail.value;
      const items: ThreadItem[] = [
        ...data.answers.map((answer) => ({
          key: `${answer.followupId == null ? "answer" : "followup-answer"}-${answer.id}`,
          kind: answer.followupId == null ? "hr_answer" as const : "followup_answer" as const,
          id: answer.id,
          followupId: answer.followupId,
          body: answer.body,
          author: answer.author,
          hr: true,
          status: answer.status,
          likeCount: answer.likeCount,
          liked: answer.liked,
          createdAt: answer.createdAt,
        })),
        ...data.followups.map((followup) => ({
          key: `followup-${followup.id}`,
          kind: "followup" as const,
          id: followup.id,
          followupId: null,
          body: followup.body,
          author: followup.author,
          hr: followup.hr,
          status: followup.status,
          likeCount: 0,
          liked: false,
          createdAt: followup.createdAt,
        })),
      ].sort((a, b) => {
        if (a.createdAt === b.createdAt) {
          const order = { followup: 0, hr_answer: 1, followup_answer: 2 };
          return order[a.kind] - order[b.kind];
        }
        return a.createdAt < b.createdAt ? -1 : 1;
      });
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
    replyTargetKey.value = null;
    draftBeforeReply.value = "";
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

async function replyTo(entry: ThreadItem) {
  if (!requireLogin()) return;
  threadOpen.value = true;
  if (replyTargetKey.value === null) draftBeforeReply.value = composerText.value;
  replyTargetKey.value = entry.key;
  composerText.value = `@${entry.author.nickname} `;
  await nextTick();
  composerFocused.value = true;
  const replyInput = replyComposerRef.value?.[0];
  replyInput?.focus();
  replyInput?.scrollIntoView({ behavior: "smooth", block: "center" });
}

async function cancelReply() {
  replyTargetKey.value = null;
  composerText.value = draftBeforeReply.value;
  draftBeforeReply.value = "";
  await nextTick();
  composerFocused.value = true;
  topComposerRef.value?.focus();
}

async function submit() {
  const body = composerText.value.trim();
  if (!body || submitting.value) return;
  if (!requireLogin()) return;
  submitting.value = true;
  error.value = "";
  try {
    if (user.value?.role === "hr") {
      await api.answer(props.item.id, body);
    } else {
      await api.followup(props.item.id, body);
    }
    composerText.value = "";
    replyTargetKey.value = null;
    draftBeforeReply.value = "";
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

function onComposerFocus() {
  if (blurTimer) {
    clearTimeout(blurTimer);
    blurTimer = undefined;
  }
  composerFocused.value = true;
}

function onComposerBlur() {
  if (blurTimer) clearTimeout(blurTimer);
  // 立即隐藏会让下方内容上移，导致点击按钮时 mouseup 落在别的元素上
  blurTimer = setTimeout(() => {
    blurTimer = undefined;
    const active = document.activeElement;
    if (!(active instanceof HTMLTextAreaElement && active.classList.contains("composer-input"))) {
      composerFocused.value = false;
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
  <article class="flex flex-col gap-4 rounded-card-lg border border-solid border-line bg-surface py-4">
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
            class="shrink-0 whitespace-nowrap rounded-[4px] bg-[#e6f0ff] px-1 py-0.5 text-[12px] leading-[18px] text-[#3874d0]"
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

    <!-- 评论/回答区（Wrapper）：上移 8px 叠在问题卡下方，展开时从卡片底下抽出 -->
    <div v-if="threadOpen" class="drawer-panel relative z-0 -mt-6 flex flex-col rounded-card-lg bg-surface pt-2">
      <!-- 我的回答输入框（MyComment）：padding 16、内部 gap 4 -->
      <div v-if="!ownerMode && replyTargetKey === null" class="flex flex-col gap-1 p-4">
        <!-- <div class="flex items-center gap-1.5">
          <span class="text-14 leading-[22px] text-regular">{{ user?.nickname ?? "我" }}</span>
        </div> -->
        <template v-if="user">
          <textarea
            ref="topComposerRef"
            v-model="composerText"
            rows="1"
            class="composer-input"
            :placeholder="user.role === 'hr' ? '以 HR 身份回答…' : '回答，或者观望'"
            @focus="onComposerFocus"
            @blur="onComposerBlur"
          />
          <div v-if="composerFocused" class="mt-2 flex justify-end">
            <button
              class="primary-btn"
              :disabled="!composerText.trim() || submitting"
              @mousedown.prevent
              @click="submit"
            >
              {{ submitting ? "提交中…" : user.role === "hr" ? "发布回答" : "发送" }}
            </button>
          </div>
        </template>
        <div v-else class="composer-guest">
          <router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">登录</router-link>
          后参与回答和追问
        </div>
      </div>

      <!-- 评论列表（CommentBlock）：上下 24、左右 16；评论流 gap 16 -->
      <div class="flex flex-col px-4 pb-6 pt-6">
        <p v-if="loadingThread" class="m-0 text-14 text-muted">正在加载回答…</p>
        <p v-else-if="thread && thread.items.length === 0" class="m-0 text-14 text-muted">
          {{ ownerMode ? "还没有回答。" : "还没有回答，来写下第一条经验吧。" }}
        </p>

        <div v-else-if="thread" class="flex flex-col gap-4">
          <div v-for="entry in thread.items" :key="entry.key">
            <div class="flex flex-wrap items-center gap-1.5">
              <span class="text-14 leading-[22px] text-regular">{{ entry.author.nickname }}</span>
              <span v-if="entry.hr" class="hr-tag">[HR]</span>
            </div>

            <p class="m-0 mt-2 whitespace-pre-wrap text-16 leading-6 text-ink">{{ entry.body }}</p>

            <div class="mt-2 flex flex-wrap items-center justify-between gap-4">
              <span class="text-14 text-muted">
                {{ entry.kind === "hr_answer" ? "回答于" : entry.kind === "followup_answer" ? "回复追问于" : "追问于" }} {{ formatTime(entry.createdAt) }}
              </span>
              <div class="flex shrink-0 items-center gap-4">
                <button v-if="!ownerMode && user?.role !== 'hr' && entry.kind !== 'followup_answer'" class="feed-action" aria-label="回复" @click="replyTo(entry)">
                  <FeedIcon name="comment" />
                  <span>回复</span>
                </button>
                <button
                  v-if="entry.kind !== 'followup'"
                  class="feed-action"
                  :class="{ 'is-active': entry.liked }"
                  aria-label="点赞"
                  @click="toggleAnswerLike(entry)"
                >
                  <FeedIcon name="like" :filled="entry.liked" />
                  <span>{{ entry.likeCount }}</span>
                </button>
                <div v-if="entry.kind !== 'followup' && !ownerMode" class="relative flex items-center">
                  <button class="feed-action" aria-label="更多" @click.stop="toggleMenu(entry.key)">
                    <FeedIcon name="dots" />
                  </button>
                  <div v-if="menuOpen === entry.key" class="more-menu" @click.stop>
                    <button class="more-item" @click="report('answer', entry.id)">举报</button>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="!ownerMode && replyTargetKey === entry.key && user" class="mt-4">
              <div class="flex items-center justify-between gap-3">
                <span class="text-14 leading-[22px] text-regular">回复 {{ entry.author.nickname }}</span>
                <button class="aux-btn" @click="cancelReply">取消</button>
              </div>
              <textarea
                ref="replyComposerRef"
                v-model="composerText"
                rows="1"
                class="composer-input mt-1.5"
                :placeholder="user.role === 'hr' ? '以 HR 身份回答…' : '回答，或者观望'"
                @focus="onComposerFocus"
                @blur="onComposerBlur"
              />
              <div v-if="composerFocused" class="mt-2 flex justify-end">
                <button
                  class="primary-btn"
                  :disabled="!composerText.trim() || submitting"
                  @mousedown.prevent
                  @click="submit"
                >
                  {{ submitting ? "提交中…" : user.role === "hr" ? "发布回答" : "发送" }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </article>
</template>
