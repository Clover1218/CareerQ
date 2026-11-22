<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { api, type PublicHrShareSummary } from "@/api";
import "@/styles/hrShares.css";

defineOptions({ name: "PublicHrSharesView" });

const items = ref<PublicHrShareSummary[]>([]);
const nextCursor = ref<number | null>(null);
const loading = ref(true);
const error = ref("");
const controller = new AbortController();

async function load(cursor: number | null) {
  loading.value = true;
  error.value = "";
  try {
    const page = await api.publicHrShares(cursor, controller.signal);
    items.value = cursor == null ? page.items : [...items.value, ...page.items];
    nextCursor.value = page.nextCursor ?? null;
  } catch {
    if (!controller.signal.aborted) error.value = "分享加载失败，请重试。";
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load(null));
onBeforeUnmount(() => controller.abort());
</script>

<template>
  <section class="hr-shares" aria-labelledby="hr-shares-title">
    <header class="hr-shares-heading">
      <h1 id="hr-shares-title">HR 分享</h1>
      <p>从招聘者的视角看简历、面试和职业选择。</p>
    </header>

    <div class="hr-shares-list" :aria-busy="loading">
      <article v-for="share in items" :key="share.id" class="hr-share-card">
        <router-link :to="{ name: 'hr-share-detail', params: { id: share.id } }" :aria-labelledby="`hr-share-${share.id}`">
          <div class="hr-share-meta">
            <span class="hr-share-author">{{ share.authorNickname }}</span>
            <time :datetime="share.publishedAt">{{ share.publishedAt.slice(0, 10) }}</time>
          </div>
          <h2 :id="`hr-share-${share.id}`">{{ share.title }}</h2>
          <p class="hr-share-excerpt">{{ share.excerpt }}</p>
          <div class="hr-share-card-footer">
            <div class="hr-share-tags" aria-label="帖子标签">
              <span v-for="tag in share.tags" :key="tag">{{ tag }}</span>
            </div>
            <span class="hr-share-read" aria-hidden="true">阅读全文 →</span>
          </div>
        </router-link>
      </article>
    </div>

    <p v-if="loading" class="hr-share-state" role="status">正在加载分享…</p>
    <div v-else-if="error" class="hr-share-state" role="alert">
      <p>{{ error }}</p>
      <button type="button" class="aux-btn" @click="load(nextCursor)">重试</button>
    </div>
    <p v-else-if="!items.length" class="hr-share-state" role="status">暂时没有已发布的分享。</p>
    <div v-else-if="nextCursor !== null" class="hr-share-state">
      <button type="button" class="aux-btn" @click="load(nextCursor)">加载更多</button>
    </div>
  </section>
</template>
