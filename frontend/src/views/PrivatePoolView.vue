<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, type Question } from "../api";

const items = ref<Question[]>([]);
const error = ref("");
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const page = await api.privatePool();
    items.value = page.items;
  } catch (e: any) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function claim(id: number) {
  try {
    await api.claim(id);
    await load();
  } catch (e: any) {
    error.value = e.message;
  }
}

onMounted(load);
</script>

<template>
  <h2>私密待认领</h2>
  <p class="muted">这里的问题已脱敏，认领后会出现在你的「回答」任务里。</p>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="!loading && items.length === 0" class="muted">暂时没有待认领的私密问题。</p>

  <div v-for="q in items" :key="q.id" class="card">
    <router-link :to="`/questions/${q.id}`" class="question-title">{{ q.title }}</router-link>
    <p class="question-body muted">{{ q.body }}</p>
    <div class="row">
      <span v-for="t in q.tags" :key="t" class="tag">{{ t }}</span>
      <button class="primary" style="margin-left: auto" @click="claim(q.id)">认领</button>
    </div>
  </div>
</template>
