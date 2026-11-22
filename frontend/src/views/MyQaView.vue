<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useRoute } from "vue-router";
import { useDialog, useMessage } from "naive-ui";
import { api, type Favorite, type MeStats, type Profile, type Question } from "@/api";
import { useUserStore } from "@/stores/user";
import {
  clearViewHistory,
  getViewHistory,
  removeViewHistoryEntry,
  type ViewHistoryEntry,
} from "@/utils/history";
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

// 「我的提问 / 历史浏览」两个标签页；历史浏览暂用本地存储
type MyQaTab = "questions" | "history" | "favorites";
const activeTab = ref<MyQaTab>("questions");
const historyEntries = ref<ViewHistoryEntry[]>(getViewHistory());
const favorites = ref<Favorite[]>([]);
const favoritesCursor = ref<number | null>(null);
const favoritesLoading = ref(false);
const favoritesLoaded = ref(false);
const tabRowRef = ref<HTMLElement | null>(null);

function switchTab(tab: MyQaTab) {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  if (tab === "history") {
    historyEntries.value = getViewHistory();
  }
  if (tab === "favorites" && !favoritesLoaded.value) {
    void loadFavorites(true);
  }
  // 若已经滚动到标签栏下方，切页时平滑回到标签栏
  void nextTick(() => {
    const row = tabRowRef.value;
    if (!row) return;
    if (row.getBoundingClientRect().top < 0) {
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

function formatTime(value: string): string {
  return value.replace("T", " ").slice(0, 16);
}

const displayName = computed(() => profile.value?.nickname ?? user.value?.nickname ?? "同学");

// 头像：优先取个人资料里的 avatarUrl，未上传时用昵称首字兜底
const avatarUrl = computed(() => profile.value?.avatarUrl ?? user.value?.avatarUrl ?? "");
const avatarInitial = computed(() => (displayName.value || "学").trim().slice(0, 1));
const avatarInputRef = ref<HTMLInputElement | null>(null);
const uploadingAvatar = ref(false);

// 身份卡副标题：学校 · 专业 · 年级
const metaLine = computed(() => {
  const values = [profile.value?.school, profile.value?.major, profile.value?.grade]
    .filter((value): value is string => !!value && value.trim().length > 0)
    .map((value) => value.trim());
  return values.length ? values.join(" · ") : "完善学校 / 专业 / 年级，让回答更贴合你的背景";
});

// 数据统计：与 HR 个人中心的贡献统计保持同一套卡片样式
const statCards = computed(() => [
  { label: "我的提问", value: stats.value.questionCount },
  { label: "收到回答", value: stats.value.receivedAnswerCount },
  { label: "获得点赞", value: stats.value.likeCount },
  { label: "我的收藏", value: favorites.value.length },
]);

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

// 我的提问默认只展示前 3 条，其余折叠；展开时自动加载剩余分页
const COLLAPSED_LIMIT = 3;
const expanded = ref(false);
const expanding = ref(false);
const visibleQuestions = computed(() =>
  expanded.value ? questions.value : questions.value.slice(0, COLLAPSED_LIMIT),
);
const hiddenCount = computed(() => Math.max(questions.value.length - COLLAPSED_LIMIT, 0));

// 审核状态：让作者能立即知道内容处于什么状态
const QUESTION_STATUS: Record<string, { text: string; tone: string }> = {
  published: { text: "已发布", tone: "published" },
  public_archived: { text: "已公开", tone: "published" },
  pending_machine: { text: "待审核", tone: "pending" },
  pending_human: { text: "待审核", tone: "pending" },
  public_pending: { text: "待审核", tone: "pending" },
  rejected: { text: "未通过", tone: "rejected" },
  public_rejected: { text: "未通过", tone: "rejected" },
  removed: { text: "已撤回", tone: "removed" },
};

function statusInfo(status: string): { text: string; tone: string } {
  return QUESTION_STATUS[status] ?? { text: status, tone: "removed" };
}

const moderationNotice = computed(() => {
  const pending = questions.value.filter((q) =>
    ["pending_machine", "pending_human"].includes(q.status),
  ).length;
  const rejected = questions.value.filter((q) =>
    ["rejected", "public_rejected"].includes(q.status),
  ).length;
  if (!pending && !rejected) return "";
  const parts: string[] = [];
  if (pending) parts.push(`${pending} 条提问正在审核，审核通过后才会出现在公开问答`);
  if (rejected) parts.push(`${rejected} 条提问未通过审核`);
  return parts.join("；") + "，可在下方列表查看状态。";
});

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

async function loadFavorites(reset = false) {
  if (favoritesLoading.value) return;
  favoritesLoading.value = true;
  try {
    const page = await api.listFavorites(undefined, reset ? null : favoritesCursor.value);
    favorites.value = reset ? page.items : [...favorites.value, ...page.items];
    favoritesCursor.value = page.nextCursor;
    favoritesLoaded.value = true;
  } catch (e) {
    message.error(errorText(e, "收藏加载失败"));
  } finally {
    favoritesLoading.value = false;
  }
}

async function unfavorite(item: Favorite) {
  try {
    await api.uninteract(item.targetType, item.targetId, "favorite");
    favorites.value = favorites.value.filter(
      (entry) => !(entry.targetType === item.targetType && entry.targetId === item.targetId),
    );
    message.success("已取消收藏");
  } catch (e) {
    message.error(errorText(e, "取消收藏失败"));
  }
}

function favoriteKindLabel(type: string): string {
  if (type === "answer") return "回答";
  if (type === "post") return "公开帖";
  return "问题";
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
  void loadFavorites(true);
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
  <section class="student-personal">
    <div class="page-intro">
      <span>我的个人中心</span>
      <router-link to="/">去公开问答 <span aria-hidden="true">↗</span></router-link>
    </div>

    <!-- 身份卡：与 HR 个人中心保持同一套视觉 -->
    <header class="identity-card">
      <button
        class="profile-avatar"
        type="button"
        :disabled="uploadingAvatar"
        aria-label="更换头像"
        @click="pickAvatar"
      >
        <img v-if="avatarUrl" :src="avatarUrl" alt="" />
        <span v-else>{{ avatarInitial }}</span>
        <span class="avatar-edit">{{ uploadingAvatar ? "上传中" : "更换头像" }}</span>
      </button>
      <input
        ref="avatarInputRef"
        type="file"
        accept="image/png,image/jpeg,image/webp"
        hidden
        @change="onAvatarChange"
      />

      <div class="identity-copy">
        <div class="identity-title">
          <template v-if="editingName">
            <input
              ref="nameInputRef"
              v-model="nameDraft"
              class="name-input"
              maxlength="64"
              aria-label="昵称"
              @keyup.enter="saveName"
              @keyup.esc="cancelEditName"
            />
            <button class="name-save" type="button" :disabled="savingName" @click="saveName">
              {{ savingName ? "保存中…" : "保存" }}
            </button>
            <button class="name-cancel" type="button" @click="cancelEditName">取消</button>
          </template>
          <template v-else>
            <h1>{{ displayName }}</h1>
            <span class="student-badge">学生</span>
            <button class="name-edit" type="button" aria-label="编辑昵称" @click="startEditName">
              <FeedIcon name="edit" :size="16" />
            </button>
          </template>
        </div>
        <p class="career-line">{{ metaLine }}</p>
        <p class="profile-bio">管理你的提问与浏览记录，持续积累属于自己的求职经验。</p>
      </div>

      <button type="button" class="edit-profile" @click="openProfileEditor">编辑资料</button>
    </header>

    <p v-if="error" class="inline-error" role="alert">
      {{ error }} <button type="button" @click="loadQuestions(true)">重试</button>
    </p>

    <!-- 数据统计：与 HR 贡献统计一致的四宫格 -->
    <div class="contribution-stats" aria-label="我的数据统计">
      <div v-for="stat in statCards" :key="stat.label" class="stat-card">
        <strong>{{ stat.value ?? "—" }}</strong>
        <span>{{ stat.label }}</span>
      </div>
    </div>
    <p class="stats-caption">统计我的提问与互动数据，「浏览记录」仅保存在本机。</p>

    <div class="personal-layout">
      <!-- 左侧：我的提问 / 历史浏览 -->
      <section ref="tabRowRef" class="record-section">
        <div class="section-title">
          <div>
            <h2>
              {{ activeTab === "questions" ? "我的提问" : activeTab === "history" ? "历史浏览" : "我的收藏" }}
            </h2>
            <p v-if="activeTab === 'questions'">从第一条提问开始，积累你的求职经验。</p>
            <p v-else-if="activeTab === 'history'">最近浏览过的问题，保存在本机，最多 50 条。</p>
            <p v-else>收藏的问题、回答与公开帖会出现在这里。</p>
          </div>
          <span v-if="activeTab === 'questions'" class="small-label">每页 3 条</span>
        </div>

        <div class="record-tabs" role="group" aria-label="内容切换">
          <button
            type="button"
            :class="{ active: activeTab === 'questions' }"
            :aria-pressed="activeTab === 'questions'"
            @click="switchTab('questions')"
          >
            我的提问
          </button>
          <button
            type="button"
            :class="{ active: activeTab === 'history' }"
            :aria-pressed="activeTab === 'history'"
            @click="switchTab('history')"
          >
            历史浏览
          </button>
          <button
            type="button"
            :class="{ active: activeTab === 'favorites' }"
            :aria-pressed="activeTab === 'favorites'"
            @click="switchTab('favorites')"
          >
            我的收藏
          </button>
        </div>

        <template v-if="activeTab === 'questions'">
          <p v-if="moderationNotice" class="review-notice">{{ moderationNotice }}</p>

          <div class="record-list" :aria-busy="loading">
            <article v-for="question in visibleQuestions" :key="question.id" class="question-record">
              <div class="record-meta">
                <span class="record-status" :class="`state-${statusInfo(question.status).tone}`">
                  {{ statusInfo(question.status).text }}
                </span>
                <time>{{ formatTime(question.createdAt) }}</time>
              </div>
              <h3>
                <router-link :to="`/questions/${question.id}`">{{ question.title }}</router-link>
              </h3>
              <p class="record-body">{{ question.body }}</p>
              <div class="record-footer">
                <div class="record-feedback">
                  <span>{{ question.answerCount }} 回答</span>
                  <span>{{ question.followupCount }} 追问</span>
                </div>
                <div class="record-actions">
                  <router-link :to="`/questions/${question.id}`">查看详情</router-link>
                  <button
                    v-if="canWithdraw(question)"
                    class="withdraw-record"
                    type="button"
                    @click="confirmWithdraw(question)"
                  >
                    撤回
                  </button>
                </div>
              </div>
            </article>

            <div v-if="!loading && questions.length === 0" class="records-empty">
              <strong>还没有提过问题</strong>
              <p>去公开问答看看，或者直接提出你的第一个问题。</p>
              <router-link to="/ask">去提问 →</router-link>
            </div>

            <div v-if="hiddenCount > 0 || expanded" class="record-pagination">
              <button v-if="!expanded" type="button" :disabled="expanding" @click="expandAll">
                {{ expanding ? "加载中…" : `展开全部（共 ${questions.length} 条）` }}
              </button>
              <button v-else type="button" @click="collapseAll">收起</button>
            </div>
          </div>
        </template>

        <template v-else-if="activeTab === 'history'">
          <div class="record-list">
            <div v-if="historyEntries.length === 0" class="records-empty">
              <strong>还没有浏览记录</strong>
              <p>去公开问答看看，浏览过的问题会出现在这里。</p>
              <router-link to="/">去公开问答 →</router-link>
            </div>

            <template v-else>
              <div v-for="entry in historyEntries" :key="entry.id" class="history-record">
                <router-link :to="`/questions/${entry.id}`" class="history-title">
                  {{ entry.title }}
                </router-link>
                <div class="history-meta">
                  <time>{{ formatVisitedAt(entry.visitedAt) }}</time>
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
              <div class="record-pagination">
                <button type="button" @click="clearHistory">清空浏览记录</button>
              </div>
            </template>
          </div>
        </template>

        <template v-else>
          <div class="record-list" :aria-busy="favoritesLoading">
            <div v-if="favoritesLoading && !favorites.length" class="records-empty">
              正在加载收藏…
            </div>
            <div v-else-if="favorites.length === 0" class="records-empty">
              <strong>还没有收藏</strong>
              <p>在公开问答中点击收藏，感兴趣的问题会出现在这里。</p>
              <router-link to="/">去公开问答 →</router-link>
            </div>
            <template v-else>
              <article
                v-for="item in favorites"
                :key="`${item.targetType}-${item.targetId}`"
                class="favorite-record"
              >
                <div class="record-meta">
                  <span class="favorite-kind">{{ favoriteKindLabel(item.targetType) }}</span>
                  <time>{{ formatTime(item.createdAt) }}</time>
                </div>
                <h3>
                  <router-link
                    v-if="item.targetType === 'question'"
                    :to="`/questions/${item.targetId}`"
                  >
                    {{ item.title }}
                  </router-link>
                  <span v-else>{{ item.title }}</span>
                </h3>
                <p class="record-body">{{ item.excerpt }}</p>
                <div class="record-footer">
                  <span class="record-feedback">收藏于 {{ formatTime(item.createdAt) }}</span>
                  <div class="record-actions">
                    <button
                      v-if="item.targetType === 'question' || item.targetType === 'answer'"
                      type="button"
                      @click="unfavorite(item)"
                    >
                      取消收藏
                    </button>
                  </div>
                </div>
              </article>
            </template>
          </div>
        </template>
      </section>

      <!-- 右侧：个人资料 / 继续参与 -->
      <aside class="student-aside">
        <section class="aside-card">
          <h2>个人资料</h2>
          <dl>
            <div><dt>学校</dt><dd>{{ profile?.school || "待完善" }}</dd></div>
            <div><dt>专业</dt><dd>{{ profile?.major || "待完善" }}</dd></div>
            <div><dt>年级</dt><dd>{{ profile?.grade || "待完善" }}</dd></div>
            <div><dt>身份</dt><dd>学生</dd></div>
          </dl>
          <button class="complete-profile" type="button" @click="openProfileEditor">
            完善资料 →
          </button>
        </section>

        <section class="aside-link">
          <span>继续积累经验</span>
          <p>浏览公开问答，看看大家都在关心什么。</p>
          <router-link to="/">去公开问答 →</router-link>
        </section>
      </aside>
    </div>

    <n-modal
      v-model:show="showProfileModal"
      preset="card"
      title="编辑个人资料"
      style="width: 420px; max-width: calc(100vw - 32px)"
    >
      <div class="profile-form">
        <label>
          学校
          <input v-model="profileDraft.school" maxlength="128" placeholder="你的学校" />
        </label>
        <label>
          专业
          <input v-model="profileDraft.major" maxlength="128" placeholder="你的专业" />
        </label>
        <label>
          年级
          <input v-model="profileDraft.grade" maxlength="32" placeholder="如：大三" />
        </label>
        <div class="form-actions">
          <button type="button" @click="showProfileModal = false">取消</button>
          <button class="save-profile" type="button" :disabled="savingProfile" @click="saveProfile">
            {{ savingProfile ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </n-modal>
  </section>
</template>

<style scoped>
.student-personal {
  max-width: 1120px;
  margin: 0 auto;
  padding: 30px 28px 64px;
  color: var(--color-text-primary);
}

.page-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.page-intro a {
  color: var(--color-brand);
  text-decoration: none;
}

/* ---------- 身份卡 ---------- */
.identity-card {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 30px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border-default);
  border-radius: 14px;
}

.profile-avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 82px;
  height: 82px;
  flex: 0 0 82px;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-radius: 50%;
  background: var(--color-brand);
  color: #fff;
  font-size: 32px;
  font-weight: 700;
  cursor: pointer;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar .avatar-edit {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  padding: 3px 0 5px;
  font-size: 10px;
  font-weight: 400;
  background: #163d8b99;
}

.identity-copy {
  flex: 1;
  min-width: 0;
}

.identity-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.identity-title h1 {
  margin: 0;
  font-size: 26px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.student-badge {
  padding: 4px 9px;
  border-radius: 5px;
  background: #edf3ff;
  color: var(--color-brand);
  font-size: 11px;
  font-weight: 600;
}

.name-edit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--color-text-muted);
  cursor: pointer;
}

.name-edit:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.name-input {
  width: min(260px, 60vw);
  padding: 6px 10px;
  font-size: 22px;
  font-weight: 700;
}

.name-save,
.name-cancel {
  padding: 7px 14px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.name-save {
  border: 1px solid var(--color-brand);
  background: var(--color-brand);
  color: #fff;
}

.name-save:disabled {
  opacity: 0.6;
  cursor: default;
}

.name-cancel {
  border: 1px solid var(--color-border-default);
  background: var(--color-bg-surface);
  color: var(--color-text-regular);
}

.career-line {
  margin: 9px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-regular);
  overflow-wrap: anywhere;
}

.profile-bio {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-muted);
}

.edit-profile {
  align-self: flex-start;
  margin-top: 7px;
  padding: 9px 16px;
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  background: var(--color-bg-surface);
  color: var(--color-text-regular);
  white-space: nowrap;
  font-size: 13px;
  cursor: pointer;
}

.edit-profile:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

/* ---------- 数据统计 ---------- */
.contribution-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 20px;
  border: 1px solid var(--color-border-default);
  border-radius: 12px;
  background: var(--color-bg-surface);
  padding: 21px 0;
}

.stat-card {
  display: grid;
  gap: 7px;
  text-align: center;
  border-right: 1px solid #edf0f5;
}

.stat-card:last-child {
  border: 0;
}

.stat-card strong {
  font-size: 25px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.stat-card span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.stats-caption {
  margin: 8px 2px 25px;
  font-size: 11px;
  color: #8490a2;
}

/* ---------- 主体布局 ---------- */
.personal-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 22px;
  align-items: start;
}

.record-section {
  min-width: 0;
  scroll-margin-top: 90px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 0 14px;
}

.section-title h2 {
  margin: 0;
  font-size: 19px;
}

.section-title p {
  margin: 7px 0 0;
  color: #8791a2;
  font-size: 12px;
}

.small-label {
  color: var(--color-text-muted);
  font-size: 11px;
}

.record-tabs {
  display: inline-flex;
  padding: 4px;
  gap: 2px;
  margin-bottom: 15px;
  background: #edf0f5;
  border-radius: 7px;
}

.record-tabs button {
  padding: 7px 14px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #717d91;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.record-tabs button.active {
  background: #fff;
  color: var(--color-brand);
  box-shadow: 0 1px 3px #2439580d;
}

/* ---------- 记录列表 ---------- */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.question-record {
  padding: 20px 22px;
  border: 1px solid #e5e9f0;
  border-radius: 10px;
  background: var(--color-bg-surface);
}

.record-meta {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11px;
}

.record-status {
  padding: 3px 7px;
  border-radius: 4px;
  background: #f0f2f6;
  color: #7a8595;
}

.state-published {
  background: #edf7f0;
  color: #4d8763;
}

.state-pending {
  background: #fff7e6;
  color: #a77b2e;
}

.state-rejected {
  background: #fff0ef;
  color: #ba625b;
}

.state-removed {
  background: #f0f2f6;
  color: #7a8595;
}

.record-meta time {
  margin-left: auto;
  color: #929bad;
}

.question-record h3 {
  margin: 13px 0 10px;
  font-size: 15px;
  line-height: 1.6;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.question-record h3 a {
  display: block;
  color: inherit;
  text-decoration: none;
}

.question-record h3 a:hover {
  color: var(--color-brand);
}

.record-body {
  margin: 0;
  color: #59667a;
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.record-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
  padding-top: 13px;
  border-top: 1px solid #f0f2f6;
}

.record-feedback {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: #8a95a5;
  font-size: 11px;
}

.record-actions {
  display: flex;
  gap: 14px;
}

.record-actions a,
.record-actions button {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-family: inherit;
  font-size: 12px;
  text-decoration: none;
  cursor: pointer;
}

.record-actions .withdraw-record {
  color: #9c6c6c;
}

.record-pagination {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

.record-pagination button {
  padding: 7px 13px;
  border: 1px solid #dce2eb;
  border-radius: 5px;
  background: var(--color-bg-surface);
  color: #52617a;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}

.record-pagination button:disabled {
  opacity: 0.5;
  cursor: default;
}

/* 空状态 */
.records-empty {
  padding: 65px 15px;
  text-align: center;
  background: var(--color-bg-surface);
  border: 1px solid #e5e9f0;
  border-radius: 10px;
  color: #8590a2;
  font-size: 13px;
}

.records-empty strong {
  color: #5b6980;
  font-size: 16px;
}

.records-empty p {
  margin: 12px 0 20px;
  line-height: 1.8;
}

.records-empty a {
  color: var(--color-brand);
  text-decoration: none;
}

/* 历史浏览 */
.history-record {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 22px;
  border: 1px solid #e5e9f0;
  border-radius: 10px;
  background: var(--color-bg-surface);
}

.history-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-primary);
  font-size: 14px;
  text-decoration: none;
}

.history-title:hover {
  color: var(--color-brand);
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.history-meta time {
  color: var(--color-text-muted);
  font-size: 11px;
}

.history-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--color-text-muted);
  cursor: pointer;
}

.history-delete:hover {
  background: var(--color-bg-hover);
  color: var(--color-danger);
}

/* ---------- 右侧栏 ---------- */
.student-aside {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 73px;
}

.aside-card {
  padding: 22px;
  border: 1px solid var(--color-border-default);
  border-radius: 10px;
  background: var(--color-bg-surface);
}

.aside-card h2 {
  margin: 0 0 22px;
  font-size: 15px;
}

.aside-card dl {
  margin: 0;
  display: grid;
  gap: 19px;
}

.aside-card dt {
  color: var(--color-text-muted);
  font-size: 11px;
}

.aside-card dd {
  margin: 6px 0 0;
  color: #536078;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.complete-profile {
  margin-top: 22px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}

.aside-link {
  padding: 21px;
  border-radius: 10px;
  background: #eef3fc;
}

.aside-link > span {
  font-size: 13px;
  color: #526c94;
}

.aside-link p {
  margin: 10px 0 17px;
  color: #8292ac;
  font-size: 12px;
  line-height: 1.7;
}

.aside-link a {
  color: var(--color-brand);
  font-size: 12px;
  text-decoration: none;
}

/* 审核提示 */
.review-notice {
  margin: 0 0 15px;
  padding: 10px 14px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
  color: #b45309;
  font-size: 12px;
  line-height: 1.8;
}

.inline-error {
  font-size: 12px;
  color: #b35c57;
  line-height: 1.8;
}

.inline-error button {
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-brand);
  cursor: pointer;
}

/* 资料编辑弹窗 */
.profile-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: var(--color-text-regular);
  font-size: 13px;
}

.profile-form input {
  box-sizing: border-box;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  color: var(--color-text-primary);
  font: inherit;
  font-size: 13px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

.form-actions button {
  padding: 9px 19px;
  border: 1px solid #dce2eb;
  border-radius: 6px;
  background: var(--color-bg-surface);
  color: #5c697e;
  font-family: inherit;
  cursor: pointer;
}

.form-actions .save-profile {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: #fff;
}

.form-actions .save-profile:disabled {
  opacity: 0.6;
  cursor: default;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1023px) {
  .personal-layout {
    grid-template-columns: minmax(0, 1fr) 240px;
    gap: 16px;
  }
}

@media (max-width: 820px) {
  .student-personal {
    padding: 22px 18px 45px;
  }

  .personal-layout {
    grid-template-columns: 1fr;
  }

  .student-aside {
    position: static;
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: start;
  }

  .identity-card {
    padding: 23px;
    gap: 16px;
  }

  .profile-avatar {
    width: 65px;
    height: 65px;
    flex-basis: 65px;
  }

  .identity-title h1 {
    font-size: 23px;
  }

  .contribution-stats {
    padding: 18px 0;
  }
}

@media (max-width: 520px) {
  .student-personal {
    padding: 20px 12px 40px;
  }

  .identity-card {
    flex-wrap: wrap;
    padding: 20px;
  }

  .identity-copy {
    flex-basis: calc(100% - 85px);
  }

  .edit-profile {
    margin: 0 0 0 auto;
  }

  .student-aside {
    grid-template-columns: 1fr;
  }

  .question-record,
  .history-record {
    padding: 17px;
  }

  .record-footer {
    align-items: flex-start;
  }

  .record-meta time {
    width: 100%;
    margin: 0;
  }

  .stat-card strong {
    font-size: 22px;
  }

  .stat-card span {
    font-size: 11px;
  }
}
.favorite-record {
  padding: 20px 22px;
  border: 1px solid #e5e9f0;
  border-radius: 10px;
  background: var(--color-bg-surface);
}

.favorite-record h3 {
  margin: 13px 0 10px;
  font-size: 15px;
  line-height: 1.6;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.favorite-record h3 a {
  display: block;
  color: inherit;
  text-decoration: none;
}

.favorite-record h3 a:hover {
  color: var(--color-brand);
}

.favorite-kind {
  padding: 3px 7px;
  border-radius: 4px;
  background: #edf3ff;
  color: var(--color-brand);
  font-size: 11px;
}

</style>
