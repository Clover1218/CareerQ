<script setup lang="ts">
import { computed, onActivated, ref } from "vue";
import { api, ApiError, type FeaturedHrDiscussion } from "@/api";
import { useUserStore } from "@/stores/user";
import ClinicRoomLabels from "./ClinicRoomLabels.vue";
import FeedIcon from "./FeedIcon.vue";

const userStore = useUserStore();
const discussion = ref<FeaturedHrDiscussion | null>(null);
const loading = ref(false);
const failed = ref(false);
const ballotAvailable = ref(true);
const canAnswer = computed(() => userStore.user?.role === "hr");

function questionLink(answerId?: number) {
  return {
    path: `/questions/${discussion.value?.questionId}`,
    query: { from: "clinic-discussion", answer: answerId ? String(answerId) : undefined },
  };
}

async function loadPreviewDiscussion() {
  // 本地预览可能仍接着上一版后端，用已有的公开问答展示卡片。
  const { items } = await api.feedQuestions({ sort: "hot" }, null, 10);
  const candidates = items.filter(item => item.answerCount > 0).slice(0, 3);
  const details = await Promise.allSettled(candidates.map(item => api.getQuestion(item.id)));
  const options = details.flatMap(result => {
    if (result.status !== "fulfilled") return [];
    const { question, answers } = result.value;
    const published = answers.filter(answer => answer.status === "published" && answer.followupId == null);
    const participants = new Map<number, FeaturedHrDiscussion["seats"][number]>();
    for (const answer of published) {
      if (!participants.has(answer.author.id)) {
        participants.set(answer.author.id, { answerId: answer.id, author: answer.author });
      }
    }
    if (!participants.size) return [];
    return [{
      questionId: question.id, title: question.title, tags: question.tags, selectedByVote: false,
      answerCount: published.length, authorCount: participants.size, seats: [...participants.values()].slice(0, 3),
    }];
  });
  return options.sort((a, b) => b.authorCount - a.authorCount || b.answerCount - a.answerCount)[0] ?? null;
}

async function load() {
  if (loading.value) return;
  loading.value = true;
  failed.value = false;
  try {
    discussion.value = await api.featuredHrDiscussion();
    ballotAvailable.value = true;
  } catch (error) {
    const oldPreview = import.meta.env.DEV && error instanceof ApiError && [401, 403, 404].includes(error.status);
    if (oldPreview) {
      ballotAvailable.value = false;
      try { discussion.value = await loadPreviewDiscussion(); }
      catch { failed.value = true; }
    } else {
      failed.value = true;
    }
  } finally {
    loading.value = false;
  }
}

onActivated(load);
</script>

<template>
  <section id="hr-discussion" class="discussion-card" aria-labelledby="hr-discussion-title" :aria-busy="loading">
    <header class="discussion-heading">
      <span class="discussion-icon" aria-hidden="true"><FeedIcon name="comment" :size="18" /></span>
      <h2 id="hr-discussion-title">HR 共议</h2>
    </header>

    <p v-if="loading && !discussion" class="discussion-status" role="status">正在加载共议…</p>
    <div v-else-if="failed" class="discussion-status" role="status">
      共议暂时无法加载。<button type="button" class="editorial-button" @click="load">重试</button>
    </div>
    <template v-else-if="discussion">
      <div class="discussion-topic">
        <div class="discussion-tags">
          <span v-if="discussion.selectedByVote" class="voted-label">本期票选</span>
          <ClinicRoomLabels :tags="discussion.tags" />
        </div>
        <h3><router-link :to="questionLink()">{{ discussion.title }}</router-link></h3>
        <p>{{ discussion.authorCount }} 位 HR · {{ discussion.answerCount }} 条回答</p>
      </div>
      <ul v-if="discussion.seats.length" class="discussion-authors" aria-label="参与共议的 HR">
        <li v-for="seat in discussion.seats" :key="seat.author.id">
          <router-link :to="questionLink(seat.answerId)" :aria-label="`查看 ${seat.author.nickname} 的回答`">
            <span class="author-initial" aria-hidden="true">{{ Array.from(seat.author.nickname || 'H')[0] }}</span>
            <span class="author-copy">
              <strong>{{ seat.author.nickname }}</strong>
              <small v-if="seat.author.company || seat.author.position">{{ [seat.author.company, seat.author.position].filter(Boolean).join(' · ') }}</small>
            </span>
            <span class="author-arrow" aria-hidden="true">></span>
          </router-link>
        </li>
      </ul>
      <p v-if="discussion.authorCount > discussion.seats.length" class="discussion-more">
        还有 {{ discussion.authorCount - discussion.seats.length }} 位答主，进入讨论查看
      </p>
      <div class="discussion-actions">
        <router-link class="editorial-button is-primary" :to="questionLink()">查看讨论 <span aria-hidden="true">></span></router-link>
        <router-link v-if="canAnswer" class="editorial-button"
          :to="{ ...questionLink(), query: { ...questionLink().query, focus: 'answer' } }">参与回答</router-link>
      </div>
    </template>
    <p v-else class="discussion-status">暂无讨论。学生可以推荐想听多位 HR 解答的问题。</p>

    <footer v-if="ballotAvailable && !failed" class="discussion-footer">
      <router-link :to="{ name: 'hr-discussions' }">下期提名与投票 <span aria-hidden="true">></span></router-link>
    </footer>
  </section>
</template>

<style scoped>
.discussion-card {
  min-width: 0;
  padding: 15px 17px;
  border: 1px solid var(--color-border-default);
  border-radius: 15px;
  background: var(--color-bg-surface);
  scroll-margin-top: calc(var(--topbar-height, 85px) + 14px);
}
.discussion-heading, .discussion-tags, .discussion-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.discussion-heading { margin-bottom: 12px; }
.discussion-heading h2 { margin: 0; font-size: 17px; }
.discussion-icon { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 8px; background: var(--color-brand-soft); color: var(--color-brand-dark); }
.discussion-topic { padding: 11px 12px; border-radius: 9px; background: var(--color-bg-hover); }
.discussion-tags:empty { display: none; }
.voted-label { color: var(--color-brand-dark); font-size: 12px; font-weight: 700; }
.discussion-topic h3 { margin: 5px 0 3px; font-size: 17px; line-height: 1.45; overflow-wrap: anywhere; }
.discussion-topic h3 a { color: inherit; }
.discussion-topic h3 a:hover { color: var(--color-brand); text-decoration: underline; }
.discussion-topic p, .discussion-status, .discussion-more { margin: 0; color: var(--color-text-muted); font-size: 13px; line-height: 1.6; }
.discussion-status button { margin-top: 6px; }
.discussion-authors { list-style: none; padding: 0; margin: 7px 0 0; }
.discussion-authors li + li { border-top: 1px solid var(--color-border-subtle); }
.discussion-authors a { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 5px 2px; color: var(--color-text-primary); }
.discussion-authors a:hover .author-arrow { transform: translateX(2px); }
.discussion-authors a:focus-visible { outline: 2px solid var(--color-brand); outline-offset: 2px; }
.author-initial { display: grid; place-items: center; flex: 0 0 27px; height: 27px; border-radius: 50%; background: var(--color-brand-soft); color: var(--color-brand-dark); font-size: 12px; font-weight: 700; }
.author-copy { display: grid; gap: 2px; min-width: 0; }
.author-copy strong { font-size: 13px; }
.author-copy small { color: var(--color-text-muted); font-size: 12px; }
.author-copy :is(strong, small) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.author-arrow { margin-left: auto; color: var(--color-brand-dark); }
.discussion-more { margin-top: 5px; }
.discussion-actions { margin-top: 11px; }
.discussion-footer { margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--color-border-subtle); font-size: 13px; font-weight: 700; }
.discussion-footer a:hover { text-decoration: underline; }
</style>
