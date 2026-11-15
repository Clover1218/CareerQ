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

/** 后端返回 ISO 时间，设计稿展示到分钟：2026-11-14 20:49 */
function formatTime(value: string): string {
  return value.replace("T", " ").slice(0, 16);
}

/** 暂无头像接口，用昵称首字占位 */
function avatarText(name: string): string {
  return name.trim().slice(0, 1) || "匿";
}

onMounted(() => {
  void load(true);
  void loadPublicPosts();
});
</script>

<template>
  <div class="mx-auto w-full max-w-feed">
    <!-- 工具栏：设计稿没有筛选区，保留功能但降低视觉权重 -->
    <div class="mb-2.5 flex flex-wrap items-center justify-between gap-3 rounded-card border border-line bg-surface px-4 py-3">
      <div class="flex min-w-0 flex-1 flex-wrap items-center gap-2">
        <input
          v-model="tag"
          class="h-9 w-full max-w-[240px] rounded-card border border-line bg-surface px-3 text-14 text-ink placeholder:text-muted focus:border-brand focus:outline-none focus:ring-0"
          placeholder="按标签筛选，如：实习"
          @keyup.enter="load(true)"
        />
        <button class="h-9 rounded-card border border-line bg-surface px-3 text-14 text-regular hover:bg-hover" @click="load(true)">
          筛选
        </button>
        <span class="shrink-0 text-14 text-muted">共 {{ items.length }} 条</span>
      </div>
      <router-link to="/ask" class="shrink-0 text-14 font-medium text-brand hover:text-brand-dark">
        我要提问
      </router-link>
    </div>

    <p v-if="error" class="mb-2.5 text-14 text-danger">{{ error }}</p>

    <!-- 问题流：对应 Figma Feed / FeedCard1 -->
    <div class="flex flex-col gap-2.5">
      <article
        v-for="q in items"
        :key="q.id"
        class="rounded-card border border-line bg-surface px-4 py-4 transition-colors hover:border-muted"
      >
        <router-link
          :to="`/questions/${q.id}`"
          class="block text-18 font-bold text-ink hover:text-brand md:text-24"
        >
          {{ q.title }}
        </router-link>

        <p class="mt-2 line-clamp-3 whitespace-pre-wrap text-16 text-ink">{{ q.body }}</p>
        <div class="-mt-1 flex justify-end">
          <router-link :to="`/questions/${q.id}`" class="text-14 text-faint hover:text-brand">
            阅读全文 ↓
          </router-link>
        </div>

        <div class="mt-2 flex flex-wrap items-center justify-between gap-3">
          <div class="flex min-w-0 items-center gap-1.5 text-14">
            <span class="grid h-[18px] w-[18px] shrink-0 place-items-center rounded-[2px] bg-hover text-[10px] leading-none text-regular">
              {{ avatarText(q.author.nickname) }}
            </span>
            <span class="truncate text-regular">{{ q.author.nickname }}</span>
            <span class="text-muted">·</span>
            <time class="shrink-0 text-muted">提问于 {{ formatTime(q.createdAt) }}</time>
          </div>

          <div class="flex shrink-0 items-center gap-4 text-14 text-ink">
            <router-link :to="`/questions/${q.id}`" class="flex items-center gap-1 hover:text-brand">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
              </svg>
              {{ q.answerCount ? `${q.answerCount} 条回答` : "我来回答" }}
            </router-link>
            <span class="flex items-center gap-1" title="有用数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M7 10v12" />
                <path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88z" />
              </svg>
              {{ q.usefulCount }}
            </span>
          </div>
        </div>

        <div v-if="q.tags.length" class="mt-3 flex flex-wrap gap-1.5">
          <span v-for="t in q.tags" :key="t" class="rounded-full bg-hover px-2 py-0.5 text-12 text-regular">
            {{ t }}
          </span>
        </div>
      </article>
    </div>

    <p v-if="!loading && items.length === 0" class="py-10 text-center text-14 text-muted">
      还没有公开问题，来问第一个吧。
    </p>

    <div v-if="cursor" class="mt-4 text-center">
      <button
        class="border-0 bg-transparent p-0 text-14 text-brand hover:bg-transparent hover:text-brand-dark disabled:cursor-not-allowed disabled:text-muted"
        :disabled="loading"
        @click="load(false)"
      >
        {{ loading ? "加载中…" : "加载更多" }}
      </button>
    </div>

    <!-- 工单公开问答：功能保留，沿用同一套卡片语言 -->
    <p v-if="postsError" class="mt-4 text-14 text-danger">{{ postsError }}</p>
    <section v-if="publicPosts.length" class="mt-8">
      <h2 class="mb-2.5 text-18 font-semibold text-ink">工单公开问答</h2>
      <div class="flex flex-col gap-2.5">
        <article v-for="post in publicPosts" :key="post.id" class="rounded-card border border-line bg-surface px-4 py-4">
          <h3 class="text-18 font-semibold text-ink">{{ post.title }}</h3>
          <p class="mt-2 whitespace-pre-wrap text-16 text-ink">{{ post.questionBody }}</p>
          <div class="mt-3 rounded-card bg-canvas px-3 py-2.5">
            <p class="text-12 font-medium text-regular">HR 回答</p>
            <p class="mt-1 whitespace-pre-wrap text-16 text-ink">{{ post.hrAnswer }}</p>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>
