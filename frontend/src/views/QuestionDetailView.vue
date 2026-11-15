<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, type QuestionDetail, type User } from "../api";

const route = useRoute();
const router = useRouter();
const detail = ref<QuestionDetail | null>(null);
const me = ref<User | null>(null);
const error = ref("");
const answerBody = ref("");
const followupBody = ref("");

const questionId = Number(route.params.id);

async function load() {
  try {
    detail.value = await api.getQuestion(questionId);
    me.value = await api.me().catch(() => null);
  } catch (e: any) {
    error.value = e.message;
  }
}

async function submitAnswer() {
  if (!answerBody.value.trim()) return;
  try {
    await api.answer(questionId, answerBody.value.trim());
    answerBody.value = "";
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

async function submitFollowup() {
  if (!followupBody.value.trim()) return;
  try {
    await api.followup(questionId, followupBody.value.trim());
    followupBody.value = "";
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

async function markUseful(answerId: number) {
  try {
    await api.interact("answer", answerId, "useful");
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

async function closeQuestion() {
  try {
    await api.closeQuestion(questionId);
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

async function publishQuestion() {
  try {
    await api.publishQuestion(questionId);
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

const isAuthor = () => detail.value && me.value && detail.value.question.author.id === me.value.id;
const reviewStatus: Record<string, string> = {
  pending_human: "待审核",
  rejected: "审核未通过",
  public_pending: "已结单，公开稿待审核",
  public_archived: "已脱敏公开",
  public_rejected: "公开稿审核未通过",
};

onMounted(load);
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>

  <div v-if="detail" class="card">
    <div class="between">
      <h2 style="margin: 0">{{ detail.question.title }}</h2>
      <router-link to="/" class="muted">返回列表</router-link>
    </div>
    <div class="row" style="margin: 8px 0">
      <span class="tag">{{ detail.question.scope === "private" ? "私密工单" : "公开" }}</span>
      <span class="tag">{{ reviewStatus[detail.question.status] ?? detail.question.status }}</span>
      <span v-for="t in detail.question.tags" :key="t" class="tag">{{ t }}</span>
      <span class="muted">{{ detail.question.author.nickname }}</span>
    </div>
    <p class="question-body">{{ detail.question.body }}</p>
    <div v-if="detail.question.scope === 'private' && isAuthor()" class="row">
      <button v-if="detail.question.status === 'assigned' && detail.answers.some(a => a.status === 'published')" class="primary" @click="closeQuestion">
        结单并提交脱敏公开
      </button>
      <button v-if="['closed', 'public_rejected'].includes(detail.question.status)" class="primary" @click="publishQuestion">
        提交公开审核
      </button>
    </div>
  </div>

  <div v-if="detail" class="card">
    <h3>回答（{{ detail.answers.length }}）</h3>
    <p v-if="detail.answers.length === 0" class="muted">还没有回答。</p>
    <div v-for="a in detail.answers" :key="a.id" class="answer">
      <div class="answer-head">
        <span>{{ a.author.nickname }}</span>
        <span>·</span>
        <span>{{ a.lengthType === "one_liner" ? "一句话建议" : "详细经验" }}</span>
      </div>
      <p class="question-body">{{ a.body }}</p>
      <span v-if="a.status !== 'published'" class="tag">{{ reviewStatus[a.status] }}</span>
      <button v-if="me && a.status === 'published'" @click="markUseful(a.id)">有用（{{ a.usefulCount }}）</button>
    </div>
  </div>

  <div v-if="detail" class="card">
    <h3>追问（{{ detail.followups.length }}）</h3>
    <p v-if="detail.followups.length === 0" class="muted">暂无追问。</p>
    <div v-for="f in detail.followups" :key="f.id" class="followup">
      <strong>{{ f.senderRole === "hr" ? "HR" : "学生" }}：</strong>{{ f.body }}
      <span v-if="f.status !== 'published'" class="tag">{{ reviewStatus[f.status] }}</span>
    </div>
    <template v-if="me && (detail.question.scope === 'public' || detail.question.status === 'assigned')">
      <div class="field" style="margin-top: 12px">
        <textarea v-model="followupBody" placeholder="继续追问…" />
      </div>
      <button class="primary" @click="submitFollowup">发送追问</button>
    </template>
    <p v-else-if="!me" class="muted">
      <router-link :to="{ path: '/login', query: { redirect: $route.fullPath } }">登录</router-link>
      或 <router-link to="/register">注册</router-link> 后可追问
    </p>
  </div>

  <div v-if="detail && me?.role === 'hr' && (detail.question.scope === 'public' || detail.question.status === 'assigned')" class="card">
    <h3>以 HR 身份回答</h3>
    <div class="field">
      <textarea v-model="answerBody" placeholder="写下你的经验或建议…" />
    </div>
    <button class="primary" @click="submitAnswer">提交回答</button>
  </div>
</template>
