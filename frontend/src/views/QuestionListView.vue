<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, type Question } from "../api";

const items = ref<Question[]>([]);
const tag = ref("");
const cursor = ref<number | null>(null);
const loading = ref(false);
const error = ref("");

async function load(reset = false) {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const page = await api.listQuestions(tag.value || undefined, reset ? null : cursor.value);
    items.value = reset ? page.items : [...items.value, ...page.items];
    cursor.value = page.nextCursor;
  } catch (e: any) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load(true));
</script>

<template>
  <div class="between">
    <h2>公开问答</h2>
    <router-link class="btn primary" to="/ask">我要提问</router-link>
  </div>

  <div class="card">
    <div class="row">
      <input v-model="tag" placeholder="按标签筛选，如：实习" style="max-width: 240px" @keyup.enter="load(true)" />
      <button @click="load(true)">筛选</button>
      <span class="muted">共 {{ items.length }} 条</span>
    </div>
  </div>

  <p v-if="error" class="error">{{ error }}</p>

  <div v-for="q in items" :key="q.id" class="card">
    <router-link :to="`/questions/${q.id}`" class="question-title">{{ q.title }}</router-link>
    <p class="question-body muted" style="max-height: 3.6em; overflow: hidden">{{ q.body }}</p>
    <div class="row">
      <span v-for="t in q.tags" :key="t" class="tag">{{ t }}</span>
      <span class="muted" style="margin-left: auto">
        {{ q.answerCount }} 个回答 · {{ q.usefulCount }} 有用 · {{ q.author.nickname }}
      </span>
    </div>
  </div>

  <p v-if="!loading && items.length === 0" class="muted">还没有公开问题，来问第一个吧。</p>

  <div v-if="cursor" style="text-align: center">
    <button :disabled="loading" @click="load(false)">{{ loading ? "加载中…" : "加载更多" }}</button>
  </div>

</template>
