<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { storeToRefs } from "pinia";
import { useMessage } from "naive-ui";
import { api, type MeStats, type Profile, type Question, type QuestionFeedItem } from "@/api";
import { useUserStore } from "@/stores/user";
import FeedCard from "@/components/FeedCard.vue";
import FeedIcon from "@/components/FeedIcon.vue";

const userStore = useUserStore();
const { user } = storeToRefs(userStore);
const message = useMessage();

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

const displayName = computed(() => profile.value?.nickname ?? user.value?.nickname ?? "同学");

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
    matchedTags: [],
  };
}

const feedEntries = computed(() =>
  questions.value.map((question) => ({ question, item: toFeedItem(question) })),
);

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
  void loadProfile();
  void loadStats();
  void loadQuestions(true);
});
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
      <div v-else class="flex items-center gap-2">
        <h1 class="m-0 text-24 font-bold leading-8 text-ink">{{ displayName }}</h1>
        <button class="icon-btn" aria-label="编辑昵称" @click="startEditName">
          <FeedIcon name="edit" :size="18" />
        </button>
      </div>
      <p class="m-0 text-14 text-muted">占位</p>
    </div>
  </section>

  <div class="mx-auto w-full max-w-[1000px] px-4 pb-16 pt-6">
    <div class="flex flex-wrap items-end justify-between gap-3 border-b border-line">
      <button class="myqa-tab is-active" type="button">
        <FeedIcon name="clock" :size="18" />
        <span>我的提问</span>
      </button>
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
      <div class="flex flex-col gap-5">
        <p v-if="error" class="m-0 text-14 text-danger">{{ error }}</p>

        <FeedCard
          v-for="entry in feedEntries"
          :key="entry.question.id"
          :item="entry.item"
          :status="entry.question.status"
          owner-mode
        />

        <p v-if="!loading && feedEntries.length === 0" class="m-0 py-16 text-center text-14 text-muted">
          还没有提问，去提出第一个问题吧。
        </p>

        <div v-if="cursor" class="text-center">
          <button class="aux-btn" :disabled="loading" @click="loadQuestions(false)">
            {{ loading ? "加载中…" : "加载更多" }}
          </button>
        </div>
      </div>

      <aside class="order-first lg:order-none">
        <section class="rounded-card border border-line bg-surface p-4">
          <div class="flex items-center justify-between">
            <h2 class="m-0 text-16 font-semibold text-ink">个人资料</h2>
            <button class="aux-btn text-12" @click="openProfileEditor">编辑 &gt;</button>
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
