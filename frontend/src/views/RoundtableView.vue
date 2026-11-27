<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { api, ApiError, type RoundtableState } from "@/api";
import { useUserStore } from "@/stores/user";

const route = useRoute();
const router = useRouter();
const { user } = storeToRefs(useUserStore());
const ballot = ref<RoundtableState | null>(null);
const nominatedTitle = ref("");
const loading = ref(true);
const busy = ref(false);
const notice = ref("");
const error = ref("");
const shown = ref(8);
let requestSequence = 0;

const nominationId = computed(() => {
  const value = Number(route.query.nominate);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
});
const detailsLink = (id: number) => ({ path: "/questions/" + id, query: { from: "roundtable" } });
const loginLink = computed(() => ({ path: "/login", query: { redirect: route.fullPath } }));

async function load() {
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = "";
  nominatedTitle.value = "";
  const id = nominationId.value;
  try {
    const [state, nomination] = await Promise.all([
      api.roundtable(),
      id && user.value?.role === "student" ? api.getQuestionContent(id).catch(() => null) : Promise.resolve(null),
    ]);
    if (sequence !== requestSequence) return;
    ballot.value = state;
    if (nomination?.question.status === "published") nominatedTitle.value = nomination.question.title;
    if (id && user.value?.role === "student" && !nominatedTitle.value) error.value = "这道问题当前无法推荐。";
  } catch (cause) {
    if (sequence === requestSequence) error.value = cause instanceof Error ? cause.message : "圆桌加载失败";
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function choose(questionId: number, nominate = false) {
  if (!ballot.value || user.value?.role !== "student" || busy.value) return;
  const week = ballot.value.week;
  const previous = ballot.value.myVote;
  const sequence = requestSequence;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const state = nominate ? await api.nominateRoundtableTopic(week, questionId) : await api.voteRoundtableTopic(week, questionId);
    if (sequence !== requestSequence) return;
    ballot.value = state;
    notice.value = nominate ? "已加入下期圆桌的候选话题。" : previous ? "已改投，原话题的票已撤回。" : "投票成功。";
    if (nominate) await router.replace({ name: "roundtable" });
  } catch (cause) {
    if (sequence !== requestSequence) return;
    if (cause instanceof ApiError && cause.status === 409) await load();
    error.value = cause instanceof Error ? cause.message : "操作失败，请重试。";
  } finally {
    busy.value = false;
  }
}

watch([() => user.value?.id, nominationId], () => void load(), { immediate: true });
</script>

<template>
  <section class="ballot-page">
    <router-link class="editorial-back" to="/">← 返回公开问答</router-link>
    <header class="editorial-heading">
      <div><h1>下期圆桌投票</h1><p>从学生推荐的公开问题中选出下期话题。每位学生每期一票，截止前可改投。</p></div>
    </header>
    <p v-if="loading && !ballot" class="card muted" role="status">正在读取圆桌投票…</p>
    <div v-if="error" class="card ballot-warning" role="alert">
      {{ error }} <button type="button" class="editorial-button" @click="load">刷新</button>
    </div>
    <p v-if="notice" class="ballot-notice" role="status">{{ notice }}</p>
    <div v-if="ballot">
      <div v-if="nominatedTitle && nominationId" class="card ballot-nominate">
        <div><span class="muted">推荐到下期圆桌</span><h2>{{ nominatedTitle }}</h2></div>
        <button type="button" class="editorial-button is-primary" :disabled="busy || loading" @click="choose(nominationId, true)">确认推荐</button>
      </div>

      <div class="ballot-heading">
        <h2>候选问题</h2><span class="muted">北京时间 {{ ballot.closesOn }} 00:00 截止</span>
      </div>
      <p v-if="!user" class="ballot-login"><router-link :to="loginLink">以学生身份登录</router-link>后可以推荐问题和投票。</p>
      <p v-if="ballot.candidates.length === 0" class="card muted">本期暂无候选。可以到公开问题详情推荐一道想讨论的问题。</p>
      <div v-else class="ballot-cards" :aria-busy="loading || busy">
        <article v-for="candidate in ballot.candidates.slice(0, shown)" :key="candidate.questionId" class="card ballot-option">
          <div><h3><router-link :to="detailsLink(candidate.questionId)">{{ candidate.title }}</router-link></h3><span class="muted">{{ candidate.votes }} 票</span></div>
          <button
            v-if="user?.role === 'student'"
            type="button"
            class="editorial-button"
            :class="{ 'is-primary': ballot.myVote === candidate.questionId }"
            :aria-pressed="ballot.myVote === candidate.questionId"
            :disabled="loading || busy"
            @click="ballot.myVote !== candidate.questionId && choose(candidate.questionId)"
          >{{ ballot.myVote === candidate.questionId ? "已投票" : ballot.myVote ? "改投" : "投一票" }}</button>
        </article>
        <button v-if="shown < ballot.candidates.length" type="button" class="editorial-button ballot-more" @click="shown += 8">显示更多候选</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ballot-page { max-width: 800px; margin-inline: auto; }
.ballot-heading, .ballot-option, .ballot-nominate, .ballot-warning { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.ballot-heading { margin: 20px 0 11px; }
.ballot-heading h2, .ballot-nominate h2 { margin: 0; font-size: 18px; }
.ballot-heading span, .ballot-option span, .ballot-login { font-size: 13px; }
.ballot-login { margin: 0 0 12px; }
.ballot-cards { display: grid; gap: 10px; }
.ballot-option h3 { margin: 0 0 5px; font-size: 16px; overflow-wrap: anywhere; }
.ballot-option a { color: var(--color-text-primary); }
.ballot-option a:hover { color: var(--color-brand); }
.ballot-warning { color: var(--color-danger); margin-bottom: 14px; }
.ballot-notice { color: var(--color-success); }
.ballot-nominate { border-color: var(--color-brand-border); background: var(--color-brand-soft); }
.ballot-more { justify-self: center; }
</style>
