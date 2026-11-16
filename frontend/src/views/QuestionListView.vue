<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, type PublicPost, type QuestionFeedItem } from "../api";
import FeedCard from "@/components/FeedCard.vue";

const items = ref<QuestionFeedItem[]>([]);
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
    const page = await api.listQuestions(undefined, reset ? null : cursor.value);
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
  } catch (e) {
    postsError.value = e instanceof Error ? e.message : "公开工单加载失败";
  }
}

onMounted(() => {
  void load(true);
  void loadPublicPosts();
});
</script>

<template>
  <div class="mx-auto w-full">
    <p v-if="error" class="m-0 mb-3 text-14 text-danger">{{ error }}</p>

    <div class="flex flex-col gap-5">
      <FeedCard v-for="q in items" :key="q.id" :item="q" />
    </div>

    <p v-if="!loading && items.length === 0" class="m-0 py-10 text-center text-14 text-muted">
      还没有公开问题，来问第一个吧。
    </p>

    <div v-if="cursor" class="mt-5 text-center">
      <button class="aux-btn" :disabled="loading" @click="load(false)">
        {{ loading ? "加载中…" : "加载更多" }}
      </button>
    </div>

    <p v-if="postsError" class="m-0 mt-4 text-14 text-danger">{{ postsError }}</p>
    <section v-if="publicPosts.length" class="mt-8">
      <h2 class="mb-3 text-18 font-semibold text-ink">工单公开问答</h2>
      <div class="flex flex-col gap-5">
        <article v-for="post in publicPosts" :key="post.id" class="rounded-card-lg border border-line bg-surface p-4 sm:p-[17px]">
          <h3 class="m-0 text-[22px] font-bold leading-[30px] text-ink sm:text-24 sm:leading-8">{{ post.title }}</h3>
          <p class="m-0 mt-2 whitespace-pre-wrap text-16 leading-6 text-ink">{{ post.questionBody }}</p>
          <div class="mt-3 rounded-card bg-canvas px-3 py-2.5">
            <p class="text-12 font-medium text-regular">HR 回答</p>
            <p class="m-0 mt-1 whitespace-pre-wrap text-16 leading-6 text-ink">{{ post.hrAnswer }}</p>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>
