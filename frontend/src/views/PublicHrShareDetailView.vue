<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, ApiError, type PublicHrShareDetail } from "@/api";
import "@/styles/hrShares.css";

const route = useRoute();
const router = useRouter();
const id = Number(route.params.id);
const share = ref<PublicHrShareDetail | null>(null);
const loading = ref(true);
const unavailable = ref(false);
const controller = new AbortController();

async function load() {
  if (!Number.isSafeInteger(id) || id <= 0) {
    unavailable.value = true;
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    share.value = await api.publicHrShare(id, controller.signal);
  } catch (error) {
    if (!controller.signal.aborted) unavailable.value = error instanceof ApiError && error.status === 404;
  } finally {
    loading.value = false;
  }
}

function returnToList(event: MouseEvent) {
  if (event.button !== 0 || event.metaKey || event.altKey || event.ctrlKey || event.shiftKey) return;
  if (window.history.state?.back !== "/hr-shares") return;
  event.preventDefault();
  router.back();
}

onMounted(load);
onBeforeUnmount(() => controller.abort());
</script>

<template>
  <section class="hr-shares" aria-label="HR 分享详情">
    <router-link to="/hr-shares" custom v-slot="{ href, navigate }">
      <a :href="href" class="hr-share-back" @click="returnToList($event); navigate($event)">← 返回 HR 分享</a>
    </router-link>

    <article v-if="share" class="hr-share-article">
      <header>
        <h1>{{ share.title }}</h1>
        <div class="hr-share-meta">
          <span class="hr-share-author">{{ share.authorNickname }}</span>
          <time :datetime="share.publishedAt">{{ share.publishedAt.slice(0, 10) }}</time>
        </div>
        <div v-if="share.tags.length" class="hr-share-tags" aria-label="帖子标签">
          <span v-for="tag in share.tags" :key="tag">{{ tag }}</span>
        </div>
      </header>
      <div class="hr-share-body">{{ share.body }}</div>
    </article>
    <p v-else-if="loading" class="hr-share-state" role="status">正在加载分享…</p>
    <p v-else-if="unavailable" class="hr-share-state" role="status">这篇分享暂不可查看。</p>
    <div v-else class="hr-share-state" role="alert">
      <p>分享加载失败，请重试。</p>
      <button type="button" class="aux-btn" @click="load">重试</button>
    </div>
  </section>
</template>
