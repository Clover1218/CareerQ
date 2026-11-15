<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, type PublicPost, type Question } from "../api";

const items = ref<Question[]>([]);
const tag = ref("");
const cursor = ref<number | null>(null);
const loading = ref(false);
const error = ref("");
const publicPosts = ref<PublicPost[]>([]);
const postsError = ref("");

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

async function loadPublicPosts() {
  try {
    publicPosts.value = await api.posts();
  } catch (error) {
    postsError.value = error instanceof Error ? error.message : "公开工单加载失败";
  }
}

onMounted(() => {
  void load(true);
  void loadPublicPosts();
});
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

  <p v-if="postsError" class="error">{{ postsError }}</p>
  <section v-if="publicPosts.length">
    <h2>工单公开问答</h2>
    <article v-for="post in publicPosts" :key="post.id" class="card">
      <h3>{{ post.title }}</h3>
      <p class="question-body">{{ post.questionBody }}</p>
      <strong>HR 回答</strong>
      <p class="question-body">{{ post.hrAnswer }}</p>
    </article>
  </section>
</template>
