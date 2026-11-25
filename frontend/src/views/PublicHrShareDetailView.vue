<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, ApiError, type PublicHrShareDetail } from "@/api";
import PublicAuthorLink from "@/components/PublicAuthorLink.vue";
import "@/styles/hrShares.css";

const route = useRoute();
const router = useRouter();
const id = Number(route.params.id);
const share = ref<PublicHrShareDetail | null>(null);
const loading = ref(true);
const unavailable = ref(false);
const controller = new AbortController();
const returnTo = computed(() => {
  const target = route.query.returnTo;
  return route.query.from === "public-profile" && typeof target === "string" && /^\/users\/\d+(\?.*)?$/.test(target)
    ? target : "/hr-shares";
});

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
  if (window.history.state?.back !== returnTo.value) return;
  event.preventDefault();
  router.back();
}

onMounted(async () => {
  const savedScroll = window.history.state?.scroll;
  await load();
  await nextTick();
  if (!controller.signal.aborted && savedScroll && Number.isFinite(savedScroll.top)) {
    window.scrollTo({ top: savedScroll.top, left: savedScroll.left ?? 0 });
  }
});
onBeforeUnmount(() => controller.abort());
</script>

<template>
  <section class="hr-shares" aria-label="HR 分享详情">
    <router-link :to="returnTo" custom v-slot="{ href, navigate }">
      <a :href="href" class="hr-share-back" @click="returnToList($event); navigate($event)">← {{ returnTo === '/hr-shares' ? '返回 HR 分享' : '返回公开主页' }}</a>
    </router-link>

    <article v-if="share" class="hr-share-article detail-enter">
      <header>
        <h1>{{ share.title }}</h1>
        <div class="hr-share-meta">
          <PublicAuthorLink :user-id="share.authorId" :nickname="share.authorNickname" :avatar-url="share.authorAvatarUrl" class="hr-share-author" />
          <time :datetime="share.publishedAt">{{ share.publishedAt.slice(0, 10) }}</time>
        </div>
        <div v-if="share.tags.length" class="hr-share-tags" aria-label="帖子标签">
          <span v-for="tag in share.tags" :key="tag">{{ tag }}</span>
        </div>
      </header>
      <div class="hr-share-body">{{ share.body }}</div>
    </article>
    <p v-else-if="loading" class="hr-share-state detail-loading" role="status">正在加载分享…</p>
    <p v-else-if="unavailable" class="hr-share-state" role="status">这篇分享暂不可查看。</p>
    <div v-else class="hr-share-state" role="alert">
      <p>分享加载失败，请重试。</p>
      <button type="button" class="aux-btn" @click="load">重试</button>
    </div>
  </section>
</template>
