<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useRoute } from "vue-router";
import { useDialog, useMessage } from "naive-ui";
import { api, type MeStats, type Profile, type Question, type QuestionFeedItem } from "@/api";
import { useUserStore } from "@/stores/user";
import { clearViewHistory, getViewHistory, removeViewHistoryEntry, type ViewHistoryEntry } from "@/utils/history";
import FeedCard from "@/components/FeedCard.vue";
import FeedIcon from "@/components/FeedIcon.vue";

const userStore = useUserStore();
const { user } = storeToRefs(userStore);
const route = useRoute();
const message = useMessage();
const dialog = useDialog();

const profile = ref<Profile | null>(null);
const stats = ref<MeStats>({
  questionCount: 0,
  receivedAnswerCount: 0,
  authoredAnswerCount: 0,
  likeCount: 0,
});
const questions = ref<Question[]>([]);
const cursor = ref<number | null>(null);
const loading = ref(false);
const error = ref("");

// 「我的提问 / 历史浏览」两个标签页；历史浏览暂用本地存储，后端接口后续再接
type MyQaTab = "questions" | "history";
const activeTab = ref<MyQaTab>("questions");
const historyEntries = ref<ViewHistoryEntry[]>(getViewHistory());
const tabRowRef = ref<HTMLElement | null>(null);

function switchTab(tab: MyQaTab) {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  if (tab === "history") {
    historyEntries.value = getViewHistory();
  }
  // 若已经滚动到标签栏下方，切页时平滑回到标签栏，避免高度差造成的跳动
  void nextTick(() => {
    const row = tabRowRef.value;
    if (!row) return;
    const top = row.getBoundingClientRect().top;
    if (top < 0) {
      row.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });
}

function clearHistory() {
  clearViewHistory();
  historyEntries.value = [];
}

function deleteHistory(id: number) {
  removeViewHistoryEntry(id);
  historyEntries.value = getViewHistory();
}

function formatVisitedAt(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString("zh-CN", { hour12: false });
}

const displayName = computed(() => profile.value?.nickname ?? user.value?.nickname ?? "同学");

// 头像：优先取个人资料里的 avatarUrl，未上传时用昵称首字兜底
const avatarUrl = computed(() => profile.value?.avatarUrl ?? user.value?.avatarUrl ?? "");
const avatarInitial = computed(() => (displayName.value || "学").trim().slice(0, 1));
const avatarInputRef = ref<HTMLInputElement | null>(null);
const uploadingAvatar = ref(false);

function pickAvatar() {
  if (uploadingAvatar.value) return;
  avatarInputRef.value?.click();
}

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 清空 value，保证同一文件再次选择也能触发 change
  input.value = "";
  if (!file) return;
  if (!/^image\/(png|jpeg|webp)$/.test(file.type)) {
    message.warning("仅支持 PNG / JPEG / WEBP 图片");
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    message.warning("头像大小不能超过 2MB");
    return;
  }
  uploadingAvatar.value = true;
  try {
    profile.value = await api.uploadAvatar(file);
    // 同步顶栏头像
    await userStore.fetchUserInfo(true);
    message.success("头像已更新");
  } catch (e) {
    message.error(errorText(e, "头像上传失败"));
  } finally {
    uploadingAvatar.value = false;
  }
}

const editingName = ref(false);
const nameDraft = ref("");
const savingName = ref(false);
const nameInputRef = ref<HTMLInputElement | null>(null);

const showProfileModal = ref(false);
const profileDraft = reactive({ school: "", major: "", grade: "" });
const savingProfile = ref(false);

function toFeedItem(question: Question): QuestionFeedItem {
  return {
    id: question.id,
    title: question.title,
    excerpt: question.body,
    tags: question.tags,
    answerCount: question.answerCount,
    followupCount: question.followupCount,
    replyCount: question.answerCount + question.followupCount,
    likeCount: question.likeCount,
    favoriteCount: question.favoriteCount,
    createdAt: question.createdAt,
  };
}

const feedEntries = computed(() =>
  questions.value.map((question) => ({ question, item: toFeedItem(question) })),
);

// 我的问答默认只展示前 3 条，其余折叠；展开时自动加载剩余分页
const COLLAPSED_LIMIT = 3;
const expanded = ref(false);
const expanding = ref(false);
const visibleEntries = computed(() =>
  expanded.value ? feedEntries.value : feedEntries.value.slice(0, COLLAPSED_LIMIT),
);
const hiddenCount = computed(() => Math.max(feedEntries.value.length - COLLAPSED_LIMIT, 0));

async function expandAll() {
  expanded.value = true;
  if (expanding.value) return;
  expanding.value = true;
  try {
    while (cursor.value) {
      await loadQuestions(false);
    }
  } finally {
    expanding.value = false;
  }
}

function collapseAll() {
  expanded.value = false;
}

function errorText(e: unknown, fallback: string): string {
  return e instanceof Error ? e.message : fallback;
}

async function loadProfile() {
  try {
    profile.value = await api.getProfile();
  } catch (e) {
    error.value = errorText(e, "资料加载失败");
  }
}

async function loadStats() {
  try {
    stats.value = await api.myStats();
  } catch (e) {
    error.value = errorText(e, "统计加载失败");
  }
}

function canWithdraw(question: Question): boolean {
  return (
    (question.answerCount ?? 0) === 0 &&
    ["pending_machine", "pending_human", "published"].includes(question.status)
  );
}

function confirmWithdraw(question: Question) {
  dialog.warning({
    title: "撤回提问",
    content: "撤回后该问题将从公开问答中移除，且无法恢复。确定要撤回吗？",
    positiveText: "确定撤回",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await api.withdrawQuestion(question.id);
        message.success("已撤回");
        await loadQuestions(true);
        await loadStats();
      } catch (e) {
        message.error(errorText(e, "撤回失败"));
      }
    },
  });
}

async function loadQuestions(reset = false) {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const page = await api.myQuestions(reset ? null : cursor.value);
    questions.value = reset ? page.items : [...questions.value, ...page.items];
    cursor.value = page.nextCursor;
  } catch (e) {
    error.value = errorText(e, "提问列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function startEditName() {
  nameDraft.value = displayName.value;
  editingName.value = true;
  await nextTick();
  nameInputRef.value?.focus();
  nameInputRef.value?.select();
}

function cancelEditName() {
  editingName.value = false;
}

async function saveName() {
  const nickname = nameDraft.value.trim();
  if (!nickname) {
    message.warning("昵称不能为空");
    return;
  }
  if (savingName.value) return;
  savingName.value = true;
  try {
    profile.value = await api.patchProfile({ nickname });
    await userStore.fetchUserInfo(true);
    editingName.value = false;
    message.success("昵称已更新");
  } catch (e) {
    message.error(errorText(e, "昵称更新失败"));
  } finally {
    savingName.value = false;
  }
}

function openProfileEditor() {
  profileDraft.school = profile.value?.school ?? "";
  profileDraft.major = profile.value?.major ?? "";
  profileDraft.grade = profile.value?.grade ?? "";
  showProfileModal.value = true;
}

async function saveProfile() {
  if (savingProfile.value) return;
  savingProfile.value = true;
  try {
    profile.value = await api.patchProfile({
      school: profileDraft.school,
      major: profileDraft.major,
      grade: profileDraft.grade,
    });
    await userStore.fetchUserInfo(true);
    showProfileModal.value = false;
    message.success("资料已更新");
  } catch (e) {
    message.error(errorText(e, "资料更新失败"));
  } finally {
    savingProfile.value = false;
  }
}

onMounted(() => {
  // 从个人信息弹窗点「历史浏览」会带 ?tab=history，进来直接切到历史标签
  if (route.query.tab === "history") {
    activeTab.value = "history";
    historyEntries.value = getViewHistory();
  }
  void loadProfile();
  void loadStats();
  void loadQuestions(true);
});

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === "history") {
      activeTab.value = "history";
      historyEntries.value = getViewHistory();
    }
  },
);
</script>

<template>
  <section class="myqa-banner">
    <div class="mx-auto flex w-full max-w-[1000px] flex-col gap-2 px-4 pb-6 pt-7">
      <div v-if="editingName" class="flex flex-wrap items-center gap-2">
        <input
          ref="nameInputRef"
          v-model="nameDraft"
          class="myqa-name-input"
          maxlength="64"
          aria-label="昵称"
          @keyup.enter="saveName"
          @keyup.esc="cancelEditName"
        />
        <button class="primary-btn" :disabled="savingName" @click="saveName">
          {{ savingName ? "保存中…" : "保存" }}
        </button>
        <button class="aux-btn" @click="cancelEditName">取消</button>
      </div>
      <div v-else class="flex items-center gap-3">
        <button
          class="myqa-avatar"
          type="button"
          :disabled="uploadingAvatar"
          aria-label="更换头像"
          @click="pickAvatar"
        >
          <img v-if="avatarUrl" :src="avatarUrl" alt="" />
          <template v-else>{{ avatarInitial }}</template>
          <span class="myqa-avatar-mask">{{ uploadingAvatar ? "上传中" : "更换" }}</span>
        </button>
        <div class="flex items-center gap-2">
          <h1 class="m-0 text-24 font-bold leading-8 text-ink">{{ displayName }}</h1>
          <button class="icon-btn" aria-label="编辑昵称" @click="startEditName">
            <FeedIcon name="edit" :size="18" />
          </button>
        </div>
      </div>
      <input
        ref="avatarInputRef"
        class="hidden"
        type="file"
        accept="image/png,image/jpeg,image/webp"
        @change="onAvatarChange"
      />
      <p class="m-0 text-14 text-muted">占位</p>
    </div>
  </section>

  <div class="mx-auto w-full max-w-[1000px] px-4 pb-16 pt-6">
    <div ref="tabRowRef" class="myqa-tabs flex flex-wrap items-end justify-between gap-3 border-b border-line">
      <div class="flex items-center gap-6">
        <button
          class="myqa-tab"
          :class="{ 'is-active': activeTab === 'questions' }"
          type="button"
          @click="switchTab('questions')"
        >
          <FeedIcon name="clock" :size="18" />
          <span>我的提问</span>
        </button>
        <button
          class="myqa-tab"
          :class="{ 'is-active': activeTab === 'history' }"
          type="button"
          @click="switchTab('history')"
        >
          <FeedIcon name="clock" :size="18" />
          <span>历史浏览</span>
        </button>
      </div>
      <div class="flex items-center gap-7 pb-2.5">
        <div class="text-center">
          <p class="m-0 text-14 text-muted">获赞数</p>
          <p class="m-0 text-14 font-medium text-ink">{{ stats.likeCount }}</p>
        </div>
        <div class="text-center">
          <p class="m-0 text-14 text-muted">回答数</p>
          <p class="m-0 text-14 font-medium text-ink">{{ stats.receivedAnswerCount }}</p>
        </div>
      </div>
    </div>

    <div class="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_247px] lg:gap-2.5">
      <div class="flex flex-col gap-5" style="min-height: 480px">
        <template v-if="activeTab === 'questions'">
          <p v-if="error" class="m-0 text-14 text-danger">{{ error }}</p>

          <FeedCard
            v-for="entry in visibleEntries"
            :key="entry.question.id"
            :item="entry.item"
            :status="entry.question.status"
            :withdrawable="canWithdraw(entry.question)"
            owner-mode
            @withdraw="confirmWithdraw(entry.question)"
          />

          <p v-if="!loading && feedEntries.length === 0" class="m-0 py-16 text-center text-14 text-muted">
            还没有提问，去提出第一个问题吧。
          </p>

          <div v-if="hiddenCount > 0 || expanded" class="text-center">
            <button v-if="!expanded" class="aux-btn" :disabled="expanding" @click="expandAll">
              {{ expanding ? "加载中…" : `展开全部（共 ${feedEntries.length} 条）` }}
            </button>
            <button v-else class="aux-btn" @click="collapseAll">收起</button>
          </div>
        </template>

        <!-- 历史浏览：暂存本地，后端接口后续接入 -->
        <template v-else>
          <p class="m-0 text-14 text-muted">最近浏览过的问题（保存在本机，最多 50 条）。</p>

          <p v-if="historyEntries.length === 0" class="m-0 py-16 text-center text-14 text-muted">
            还没有浏览记录，去公开问答看看。
          </p>

          <div v-else class="flex flex-col gap-5">
            <div
              v-for="entry in historyEntries"
              :key="entry.id"
              class="flex items-center justify-between gap-3 rounded-card-lg border border-line bg-surface px-4 py-4"
            >
              <router-link
                :to="`/questions/${entry.id}`"
                class="min-w-0 truncate text-16 font-medium text-ink hover:text-brand"
              >
                {{ entry.title }}
              </router-link>
              <div class="flex shrink-0 items-center gap-2">
                <span class="text-12 text-muted">{{ formatVisitedAt(entry.visitedAt) }}</span>
                <button
                  class="history-delete"
                  type="button"
                  aria-label="删除这条浏览记录"
                  @click="deleteHistory(entry.id)"
                >
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M3 6h18" />
                    <path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2" />
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                    <path d="M10 11v6" />
                    <path d="M14 11v6" />
                  </svg>
                </button>
              </div>
            </div>
            <div class="mt-2 text-center">
              <button class="aux-btn" type="button" @click="clearHistory">清空浏览记录</button>
            </div>
          </div>
        </template>
      </div>

      <aside class="profile-aside order-first lg:order-none">
        <section class="rounded-card border border-line bg-surface p-4">
          <div class="flex items-center justify-between">
            <h2 class="m-0 text-16 font-semibold text-ink">个人资料</h2>
            <button class="aux-btn text-12" @click="openProfileEditor">编辑 &gt;</button>
          </div>
          <div class="mt-3 flex items-center gap-3">
            <span class="myqa-avatar myqa-avatar--sm" aria-hidden="true">
              <img v-if="avatarUrl" :src="avatarUrl" alt="" />
              <template v-else>{{ avatarInitial }}</template>
            </span>
            <div class="min-w-0">
              <p class="m-0 truncate text-14 font-medium text-ink">{{ displayName }}</p>
              <button
                class="aux-btn text-12"
                type="button"
                :disabled="uploadingAvatar"
                @click="pickAvatar"
              >
                {{ uploadingAvatar ? "上传中…" : "更换头像" }}
              </button>
            </div>
          </div>
          <dl class="m-0 mt-3 flex flex-col gap-2.5">
            <div class="flex items-center gap-2">
              <FeedIcon name="school" :size="24" class="shrink-0 text-regular" />
              <dd class="m-0 truncate text-14 text-ink">{{ profile?.school || "学校待完善" }}</dd>
            </div>
            <div class="flex items-center gap-2">
              <FeedIcon name="book" :size="24" class="shrink-0 text-regular" />
              <dd class="m-0 truncate text-14 text-ink">{{ profile?.major || "专业待完善" }}</dd>
            </div>
            <div class="flex items-center gap-2">
              <FeedIcon name="calendar" :size="24" class="shrink-0 text-regular" />
              <dd class="m-0 truncate text-14 text-ink">{{ profile?.grade || "年级待完善" }}</dd>
            </div>
          </dl>
        </section>
      </aside>
    </div>
  </div>

  <n-modal
    v-model:show="showProfileModal"
    preset="card"
    title="编辑个人资料"
    style="width: 420px; max-width: calc(100vw - 32px)"
  >
    <div class="flex flex-col gap-3">
      <label class="flex flex-col gap-1.5 text-14 text-regular">
        学校
        <input v-model="profileDraft.school" maxlength="128" placeholder="你的学校" />
      </label>
      <label class="flex flex-col gap-1.5 text-14 text-regular">
        专业
        <input v-model="profileDraft.major" maxlength="128" placeholder="你的专业" />
      </label>
      <label class="flex flex-col gap-1.5 text-14 text-regular">
        年级
        <input v-model="profileDraft.grade" maxlength="32" placeholder="如：大三" />
      </label>
      <div class="mt-2 flex justify-end gap-2">
        <button @click="showProfileModal = false">取消</button>
        <button class="primary-btn" :disabled="savingProfile" @click="saveProfile">
          {{ savingProfile ? "保存中…" : "保存" }}
        </button>
      </div>
    </div>
  </n-modal>
</template>
