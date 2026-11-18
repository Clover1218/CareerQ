<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, type QuestionDetail } from "../api";

const route = useRoute();
const router = useRouter();
const detail = ref<QuestionDetail | null>(null);
const body = ref("");
const lengthType = ref("detailed");
const loading = ref(false);
const submitting = ref(false);
const error = ref("");
const submittedStatus = ref("");
const questionId = computed(() => Number(route.params.id));

async function load() {
  if (!Number.isSafeInteger(questionId.value) || questionId.value <= 0) {
    error.value = "问题编号无效";
    return;
  }

  loading.value = true;
  error.value = "";
  try {
    detail.value = await api.getQuestion(questionId.value);
    if (detail.value.question.scope !== "public") {
      error.value = "该问题不属于公开问题池";
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "问题加载失败";
  } finally {
    loading.value = false;
  }
}

async function submit() {
  const answerBody = body.value.trim();
  if (!answerBody || submitting.value || !detail.value || detail.value.question.scope !== "public") return;

  submitting.value = true;
  error.value = "";
  try {
    const answer = await api.answer(questionId.value, answerBody, lengthType.value);
    submittedStatus.value = answer.status;
    body.value = "";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "回答提交失败";
  } finally {
    submitting.value = false;
  }
}

function returnToPool() {
  router.push("/hr/questions");
}

onMounted(load);
</script>

<template>
  <div class="between">
    <div>
      <p class="muted"><router-link to="/hr/questions">公开问题池</router-link> / 回答问题</p>
      <h2>回答问题</h2>
    </div>
    <button @click="returnToPool">返回问题池</button>
  </div>

  <p v-if="loading" class="muted">正在加载问题…</p>
  <p v-if="error" class="error">{{ error }}</p>

  <template v-if="detail && detail.question.scope === 'public'">
    <article class="card">
      <div class="row">
        <span class="tag">公开问题</span>
        <span v-for="tag in detail.question.tags" :key="tag" class="tag">{{ tag }}</span>
      </div>
      <h3>{{ detail.question.title }}</h3>
      <p class="question-body">{{ detail.question.body }}</p>
      <p class="muted">提问者：{{ detail.question.author.nickname }} · {{ detail.question.createdAt }}</p>
    </article>

    <form v-if="!submittedStatus" class="card" @submit.prevent="submit">
      <h3>你的回答</h3>
      <div class="field">
        <label for="answer-body">回答内容</label>
        <textarea id="answer-body" v-model="body" rows="8" maxlength="5000" required
          placeholder="结合你的经验，给出清晰、具体的建议。" />
        <span class="muted">{{ body.length }} / 5000</span>
      </div>
      <div class="field">
        <label for="answer-length">回答形式</label>
        <select id="answer-length" v-model="lengthType">
          <option value="detailed">详细经验</option>
          <option value="one_liner">一句话建议</option>
        </select>
      </div>
      <button class="primary" type="submit" :disabled="submitting || !body.trim()">
        {{ submitting ? "提交中…" : "提交回答" }}
      </button>
    </form>

    <section v-else class="card">
      <h3>回答已提交</h3>
      <p class="muted">
        {{ submittedStatus === "published" ? "回答已发布。" : "回答已提交，正在等待审核。" }}
      </p>
      <button class="primary" @click="returnToPool">返回公开问题池</button>
    </section>
  </template>
</template>
