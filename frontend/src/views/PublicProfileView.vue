<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { ApiError, api, type PublicProfile, type PublicProfileAnswer, type PublicProfileQuestion, type PublicHrShareSummary } from "@/api";
import { useUserStore } from "@/stores/user";
import { cursorPages } from "@/utils/cursorPages";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const id = Number(route.params.id);
const profile = ref<PublicProfile | null>(null);
const loading = ref(true);
const error = ref("");
const listError = ref("");
const questions = ref<PublicProfileQuestion[]>([]);
const answers = ref<PublicProfileAnswer[]>([]);
const shares = ref<PublicHrShareSummary[]>([]);
const nextCursor = ref<number | null>(null);
const controller = new AbortController();
const pages = computed(() => cursorPages(route.query.pages));
const tab = computed(() => route.query.tab === "shares" ? "shares" : "answers");
const isHr = computed(() => profile.value?.role === "hr");
const education = computed(() => [profile.value?.school, profile.value?.major, profile.value?.grade].filter(Boolean));
const hasItems = computed(() => isHr.value ? (tab.value === "shares" ? shares.value.length : answers.value.length) : questions.value.length);
const stats = computed(() => {
  const values = profile.value?.stats;
  if (!values) return [];
  return isHr.value ? [
    { label: "主回答", value: values.rootAnswerCount },
    { label: "追问回复", value: values.followupReplyCount },
    { label: "获赞", value: values.likeCount },
    { label: "有用", value: values.usefulCount },
  ] : [
    { label: "公开提问", value: values.questionCount },
    { label: "收到回答", value: values.receivedAnswerCount },
    { label: "获赞", value: values.likeCount },
  ];
});
const verification = computed(() => {
  if (["approved", "verified"].includes(profile.value?.verifyStatus ?? "")) return "认证通过（演示）";
  return profile.value?.verifyStatus === "rejected" ? "认证未通过" : "认证待完善";
});

function changePage(trail: number[], nextTab = tab.value) {
  void router.replace({
    name: "public-profile",
    params: { id },
    query: { ...(nextTab === "shares" ? { tab: "shares" } : {}), ...(trail.length ? { pages: trail.join(",") } : {}) },
  });
}

function questionLink(questionId: number, answerId?: number) {
  return {
    path: `/questions/${questionId}`,
    query: { from: "public-profile", returnTo: route.fullPath, ...(answerId != null ? { answer: String(answerId) } : {}) },
  };
}

function goBack() {
  if (typeof window.history.state?.back === "string") router.back();
  else void router.push("/");
}

function returnToLogin() {
  controller.abort();
  profile.value = null;
  if (route.name === "public-profile") {
    void router.replace({ path: "/login", query: { redirect: route.fullPath } });
  }
}

watch(() => userStore.user, (user) => {
  if (!user) returnToLogin();
});

async function load() {
  loading.value = true;
  error.value = "";
  listError.value = "";
  try {
    if (!Number.isSafeInteger(id) || id < 1) throw new Error("公开主页暂不可查看");
    profile.value = await api.publicProfile(id, controller.signal);
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401 && !controller.signal.aborted) {
      returnToLogin();
    }
    if (!controller.signal.aborted) error.value = cause instanceof Error ? cause.message : "主页加载失败";
    loading.value = false;
    return;
  }
  try {
    const cursor = pages.value.at(-1);
    if (!isHr.value) {
      const page = await api.publicProfileQuestions(id, cursor, controller.signal);
      questions.value = page.items;
      nextCursor.value = page.nextCursor ?? null;
    } else if (tab.value === "shares") {
      const page = await api.publicProfileShares(id, cursor, controller.signal);
      shares.value = page.items;
      nextCursor.value = page.nextCursor ?? null;
    } else {
      const page = await api.publicProfileAnswers(id, cursor, controller.signal);
      answers.value = page.items;
      nextCursor.value = page.nextCursor ?? null;
    }
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401 && !controller.signal.aborted) {
      returnToLogin();
    }
    if (!controller.signal.aborted) listError.value = cause instanceof Error ? cause.message : "内容加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  const saved = window.history.state?.scroll;
  await load();
  await nextTick();
  if (!controller.signal.aborted && saved && Number.isFinite(saved.top)) window.scrollTo({ top: saved.top, left: saved.left ?? 0 });
});
onBeforeUnmount(() => controller.abort());
</script>

<template>
  <section class="public-profile-page">
    <button class="profile-back" type="button" @click="goBack">← 返回</button>
    <p v-if="error" class="card profile-state" role="alert">{{ error }} <button type="button" @click="load">重试</button></p>
    <p v-else-if="!profile" class="card profile-state" role="status">正在加载主页…</p>
    <template v-else>
      <header class="card public-identity">
        <div class="identity-top">
          <span class="public-avatar">
            <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="" />
            <span v-else>{{ Array.from(profile.nickname.trim() || "职")[0] }}</span>
          </span>
          <div class="identity-name">
            <div class="name-row">
              <h1>{{ profile.nickname }}</h1>
              <span class="identity-role">{{ isHr ? "HR" : "学生" }}</span>
            </div>
            <p v-if="isHr && (profile.companyDisplay || profile.position)" class="identity-meta">
              {{ [profile.companyDisplay, profile.position].filter(Boolean).join(" · ") }}
            </p>
            <p v-else-if="!isHr && education.length" class="identity-meta">{{ education.join(" · ") }}</p>
            <p v-if="isHr" class="verification">{{ verification }}</p>
          </div>
        </div>
        <p v-if="profile.bio" class="identity-bio">{{ profile.bio }}</p>
        <div v-if="isHr && (profile.industry || profile.specialties.length)" class="identity-specialties">
          <span v-if="profile.industry" class="industry">{{ profile.industry }}</span>
          <span v-for="tag in profile.specialties" :key="tag" class="topic">{{ tag }}</span>
        </div>
        <dl class="public-stats">
          <div v-for="stat in stats" :key="stat.label"><dd>{{ stat.value }}</dd><dt>{{ stat.label }}</dt></div>
        </dl>
      </header>

      <section class="profile-content" aria-label="公开内容">
        <div class="content-heading">
          <h2 v-if="!isHr">公开提问</h2>
          <div v-else class="content-tabs" role="group" aria-label="主页栏目">
            <button type="button" :aria-pressed="tab === 'answers'" @click="changePage([], 'answers')">公开回答</button>
            <button type="button" :aria-pressed="tab === 'shares'" @click="changePage([], 'shares')">原创分享</button>
          </div>
        </div>
        <p v-if="loading" class="card profile-state" role="status">正在加载内容…</p>
        <div v-else-if="listError" class="card profile-state" role="alert">{{ listError }} <button type="button" @click="load">重试</button></div>
        <p v-else-if="!hasItems" class="card profile-state">暂时没有可查看的公开内容。</p>
        <div v-else class="content-list">
          <article v-for="item in questions" :key="item.id" class="card content-card">
            <time>{{ item.createdAt.replace('T', ' ').slice(0, 16) }}</time>
            <h3><RouterLink :to="questionLink(item.id)">{{ item.title }}</RouterLink></h3>
            <p class="content-excerpt">{{ item.excerpt }}</p>
            <footer><span>{{ item.answerCount }} 个回答 · {{ item.followupCount }} 条追问</span><RouterLink :to="questionLink(item.id)">查看详情 →</RouterLink></footer>
          </article>
          <article v-for="item in answers" :key="item.id" class="card content-card">
            <div class="entry-meta"><span class="topic">{{ item.followupId == null ? "主回答" : "追问回复" }}</span><time>{{ item.createdAt.replace('T', ' ').slice(0, 16) }}</time></div>
            <h3><RouterLink :to="questionLink(item.questionId, item.id)">{{ item.title }}</RouterLink></h3>
            <p class="content-excerpt">{{ item.excerpt }}</p>
            <footer><span>{{ item.likeCount }} 获赞 · {{ item.usefulCount }} 有用</span><RouterLink :to="questionLink(item.questionId, item.id)">查看原回答 →</RouterLink></footer>
          </article>
          <article v-for="item in shares" :key="item.id" class="card content-card">
            <time>{{ item.publishedAt.slice(0, 10) }}</time>
            <h3><RouterLink :to="{ name: 'hr-share-detail', params: { id: item.id }, query: { from: 'public-profile', returnTo: route.fullPath } }">{{ item.title }}</RouterLink></h3>
            <p class="content-excerpt">{{ item.excerpt }}</p>
            <footer><span>{{ item.tags.join(' · ') }}</span><RouterLink :to="{ name: 'hr-share-detail', params: { id: item.id }, query: { from: 'public-profile', returnTo: route.fullPath } }">阅读全文 →</RouterLink></footer>
          </article>
        </div>
        <nav v-if="!loading && !listError && (hasItems || pages.length)" class="profile-pagination" aria-label="公开内容分页">
          <button type="button" :disabled="pages.length === 0" @click="changePage(pages.slice(0, -1))">上一页</button>
          <span>第 {{ pages.length + 1 }} 页</span>
          <button type="button" :disabled="nextCursor == null" @click="nextCursor != null && changePage([...pages, nextCursor])">下一页</button>
        </nav>
        <p v-if="!loading && !listError && hasItems && nextCursor == null" class="end-caption">公开内容已经看完咯</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.public-profile-page { display: grid; gap: 16px; padding-bottom: 32px; }
.public-profile-page .card { margin: 0; border-radius: 14px; }
.profile-back { justify-self: start; border: 0; background: transparent; padding: 4px 0; color: var(--color-brand); }
.public-identity { padding: 24px; }
.identity-top { display: flex; align-items: center; gap: 16px; }
.public-avatar { width: 66px; height: 66px; flex: 0 0 66px; display: grid; place-items: center; border-radius: 50%; overflow: hidden; background: var(--color-brand-soft); color: var(--color-brand); font-size: 28px; font-weight: 600; }
.public-avatar img { width: 100%; height: 100%; object-fit: cover; }
.identity-name { min-width: 0; overflow-wrap: anywhere; }
.name-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.name-row h1 { margin: 0; font-size: 24px; line-height: 1.4; color: var(--color-text-primary); }
.identity-role, .topic { border-radius: 5px; padding: 3px 7px; color: var(--color-brand-dark); background: var(--color-brand-soft); font-size: 12px; }
.identity-meta, .verification { margin: 5px 0 0; color: var(--color-text-muted); font-size: 13px; line-height: 1.6; }
.identity-bio { margin: 18px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--color-text-regular); line-height: 1.8; }
.identity-specialties { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 14px; }
.industry { padding: 3px 7px; border-radius: 5px; background: var(--color-bg-hover); font-size: 12px; }
.public-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(65px, 1fr)); gap: 8px; margin: 20px 0 0; border-top: 1px solid var(--color-border-subtle); padding-top: 17px; text-align: center; }
.public-stats dd { margin: 0; color: var(--color-text-primary); font-weight: 650; font-size: 21px; }
.public-stats dt { margin-top: 4px; color: var(--color-text-muted); font-size: 12px; }
.content-heading { margin: 4px 0 12px; }
.content-heading h2 { font-size: 20px; margin: 0; }
.content-tabs { display: flex; gap: 6px; }
.content-tabs button[aria-pressed="true"] { color: var(--color-brand-dark); border-color: var(--color-brand-border); background: var(--color-brand-soft); font-weight: 600; }
.content-list { display: grid; gap: 12px; }
.content-card { padding: 18px; }
.content-card time { color: var(--color-text-muted); font-size: 12px; }
.entry-meta { display: flex; gap: 8px; align-items: center; }
.content-card h3 { font-size: 17px; line-height: 1.55; margin: 8px 0; overflow-wrap: anywhere; }
.content-card h3 a { color: var(--color-text-primary); }
.content-card h3 a:hover { color: var(--color-brand); }
.content-excerpt { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--color-text-regular); line-height: 1.8; margin: 0; }
.content-card footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; padding-top: 12px; margin-top: 12px; border-top: 1px solid var(--color-border-subtle); font-size: 13px; color: var(--color-text-muted); }
.content-card footer a { white-space: nowrap; color: var(--color-brand); }
.profile-state { padding: 24px 18px; text-align: center; color: var(--color-text-muted); }
.profile-pagination { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 16px; font-size: 13px; }
.profile-pagination button:disabled { opacity: 0.5; cursor: default; }
.end-caption { margin-top: 12px; text-align: center; color: var(--color-text-muted); font-size: 12px; }
@media (max-width: 480px) {
  .public-identity { padding: 18px 16px; }
  .public-avatar { width: 52px; height: 52px; flex-basis: 52px; font-size: 23px; }
  .name-row h1 { font-size: 21px; }
  .content-card { padding: 16px; }
}
</style>
