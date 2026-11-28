<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, ApiError, type HrDiscussionBallot } from "@/api";
import { useUserStore } from "@/stores/user";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const ballot = ref<HrDiscussionBallot | null>(null);
const nominationTitle = ref("");
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const notice = ref("");
const shown = ref(8);
let requestSequence = 0;

const isStudent = computed(() => userStore.user?.role === "student");
const nominationId = computed(() => {
  const value = route.query.nominate;
  if (typeof value !== "string" || !/^\d+$/.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
});

function questionLink(id: number) {
  return { path: `/questions/${id}`, query: { from: "hr-discussions" } };
}

async function load() {
  const sequence = ++requestSequence;
  loading.value = true;
  ballot.value = null;
  error.value = "";
  nominationTitle.value = "";
  const id = isStudent.value ? nominationId.value : null;
  try {
    const [state, nomination] = await Promise.all([
      api.hrDiscussionBallot(),
      id ? api.getQuestionContent(id).catch(() => null) : null,
    ]);
    if (sequence !== requestSequence) return;
    ballot.value = state;
    if (nomination?.question.status === "published") nominationTitle.value = nomination.question.title;
    if (id && !nominationTitle.value) error.value = "这道问题当前无法提名。";
  } catch {
    if (sequence === requestSequence) error.value = "暂时无法读取共议投票，请稍后重试。";
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function submit(questionId: number, nominate = false) {
  const state = ballot.value;
  if (!state || !isStudent.value || loading.value || saving.value) return;
  const sequence = requestSequence;
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    const updated = nominate
      ? await api.nominateHrDiscussion(state.week, questionId)
      : await api.voteHrDiscussion(state.week, questionId);
    if (sequence !== requestSequence) return;
    ballot.value = updated;
    notice.value = nominate ? "已加入下期共议候选。" : state.myVote ? "已更新你的投票。" : "已投票。";
    if (nominate) {
      shown.value = Math.max(shown.value, updated.candidates.findIndex(candidate => candidate.questionId === questionId) + 1);
      await router.replace({ name: "hr-discussions" });
    }
  } catch (cause) {
    if (sequence !== requestSequence) return;
    if (cause instanceof ApiError && cause.status === 409) await load();
    error.value = cause instanceof Error ? cause.message : "操作失败，请重试。";
  } finally {
    saving.value = false;
  }
}

watch([() => userStore.user?.id, nominationId], () => void load(), { immediate: true });
</script>

<template>
  <section class="discussion-page">
    <router-link class="editorial-back" :to="{ name: 'clinic-home', hash: '#hr-discussion' }">< 返回职场门诊</router-link>
    <header class="discussion-page-header">
      <h1>下期 HR 共议</h1>
      <p>从学生推荐的公开问题中选出下期共议题目。每位学生每周一票，截止前可以改投。</p>
    </header>

    <p v-if="loading && !ballot" class="discussion-state" role="status">正在读取投票…</p>
    <div v-if="error" class="discussion-state discussion-error" role="alert">
      {{ error }} <button type="button" class="editorial-button" :disabled="loading || saving" @click="load">重试</button>
    </div>
    <p v-if="notice" class="discussion-notice" role="status">{{ notice }}</p>

    <template v-if="ballot">
      <section v-if="nominationId && nominationTitle" class="discussion-nomination">
        <div><p>提名这道问题</p><h2>{{ nominationTitle }}</h2></div>
        <button type="button" class="editorial-button is-primary" :disabled="saving || loading"
          @click="submit(nominationId, true)">确认提名</button>
      </section>

      <div class="discussion-list-heading">
        <h2>候选问题</h2>
        <span>北京时间 {{ ballot.closesOn }} 00:00 截止</span>
      </div>
      <p v-if="!userStore.user" class="discussion-login">
        <router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">登录学生账号</router-link>后可以提名和投票。
      </p>
      <p v-if="!ballot.candidates.length" class="discussion-state">本周还没有候选题目。学生可以在公开问题详情页推荐想讨论的问题。</p>
      <div v-else class="discussion-ballot" :aria-busy="saving || loading">
        <article v-for="candidate in ballot.candidates.slice(0, shown)" :key="candidate.questionId" class="discussion-option">
          <div>
            <h3><router-link :to="questionLink(candidate.questionId)">{{ candidate.title }}</router-link></h3>
            <span>{{ candidate.votes }} 票</span>
          </div>
          <button v-if="isStudent" type="button" class="editorial-button"
            :class="{ 'is-primary': ballot.myVote === candidate.questionId }"
            :aria-pressed="ballot.myVote === candidate.questionId"
            :disabled="saving || loading || ballot.myVote === candidate.questionId"
            @click="submit(candidate.questionId)"
          >{{ ballot.myVote === candidate.questionId ? "已投票" : ballot.myVote ? "改投" : "投一票" }}</button>
        </article>
        <button v-if="shown < ballot.candidates.length" type="button" class="editorial-button discussion-show-more"
          @click="shown += 8">显示更多问题</button>
      </div>
      <router-link class="editorial-button discussion-browse" :to="{ name: 'clinic-home', hash: '#public-questions' }">
        去问答列表看看 <span aria-hidden="true">></span>
      </router-link>
    </template>
  </section>
</template>

<style scoped>
.discussion-page { max-width: 800px; margin-inline: auto; }
.discussion-page-header { padding: 20px 24px; border: 1px solid var(--color-brand-border); border-radius: 15px; background: var(--color-brand-soft); }
.discussion-page-header h1 { margin: 0 0 5px; font-size: clamp(25px, 5vw, 38px); letter-spacing: -.03em; }
.discussion-page-header p, .discussion-login { margin: 0; color: var(--color-text-regular); font-size: 14px; line-height: 1.6; }
.discussion-state, .discussion-option, .discussion-nomination { padding: 14px 16px; border: 1px solid var(--color-border-default); border-radius: 11px; background: var(--color-bg-surface); }
.discussion-option, .discussion-nomination, .discussion-list-heading { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.discussion-option h3, .discussion-nomination h2 { margin: 0 0 4px; font-size: 16px; overflow-wrap: anywhere; }
.discussion-option h3 a { color: var(--color-text-primary); }
.discussion-option h3 a:hover { color: var(--color-brand); text-decoration: underline; }
.discussion-option span, .discussion-nomination p, .discussion-list-heading span, .discussion-state { color: var(--color-text-muted); font-size: 13px; }
.discussion-nomination { margin: 15px 0; border-color: var(--color-brand-border); }
.discussion-nomination p { margin: 0 0 5px; }
.discussion-list-heading { margin: 19px 0 10px; flex-wrap: wrap; }
.discussion-list-heading h2 { margin: 0; font-size: 18px; }
.discussion-login { margin-bottom: 10px; }
.discussion-login a { text-decoration: underline; }
.discussion-ballot { display: grid; gap: 9px; }
.discussion-show-more { justify-self: center; }
.discussion-browse { margin-top: 13px; }
.discussion-notice { color: var(--color-success); }
.discussion-error { margin-top: 12px; color: var(--color-danger); }
.discussion-error button { margin-left: 6px; }
@media (max-width: 550px) { .discussion-page-header { padding: 15px; } }
</style>
