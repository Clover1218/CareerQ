<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { api, type Question } from "../api";

const items = ref<Question[]>([]);
const error = ref("");
const loading = ref(false);
const router = useRouter();

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
    await router.push(`/questions/${id}`);
  } catch (e: any) {
    error.value = e.message;
  }
}

onMounted(load);
</script>

<template>
  <h2>私密待认领</h2>
  <p class="muted">审核通过的问题会在这里展示。认领后可提交回答审核。</p>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="!loading && items.length === 0" class="muted">暂时没有待认领的私密问题。</p>

  <div v-for="q in items" :key="q.id" class="card">
    <strong class="question-title">{{ q.title }}</strong>
    <p class="question-body muted">{{ q.body }}</p>
    <div class="row">
      <span v-for="t in q.tags" :key="t" class="tag">{{ t }}</span>
      <button class="primary" style="margin-left: auto" @click="claim(q.id)">认领</button>
    </div>
  </div>
</template>
