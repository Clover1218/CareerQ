<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute } from "vue-router";
import { storeToRefs } from "pinia";
import { api, type FeedFilterValue, type QuestionFeedItem } from "../api";
import { useUserStore } from "@/stores/user";
import FeedCard from "@/components/FeedCard.vue";
import FeedCardSkeleton from "@/components/FeedCardSkeleton.vue";
import FeedFilterPanel from "@/components/FeedFilterPanel.vue";
import RoundtableFeature from "@/components/RoundtableFeature.vue";

defineOptions({ name: "QuestionListView" });

const route = useRoute();
const { user } = storeToRefs(useUserStore());
const items = ref<QuestionFeedItem[]>([]);
const cursor = ref<string | null>(null);
const loading = ref(false);
const error = ref("");
const dismissed = ref<{ item: QuestionFeedItem; index: number } | null>(null);
const undoing = ref(false);
const filters = ref<FeedFilterValue>({
  q: "",
  tags: [],
  period: "all",
  from: null,
  to: null,
  sort: "recommend",
});
const tagSlugMap = ref<Record<string, string>>({});
const listRef = ref<HTMLElement | null>(null);
// 重置加载时锁定列表高度，骨架屏填满原区域，避免内容塌陷与底部跳动
const lockedHeight = ref<number | null>(null);
const skeletonCount = ref(3);
const MORE_SKELETON_COUNT = 2;

const showResetSkeletons = computed(() => loading.value && items.value.length === 0);
const showMoreSkeletons = computed(() => loading.value && items.value.length > 0);
// 请求过快时也至少展示 250ms 骨架，避免一闪而过
const MIN_LOADING_MS = 250;

function wait(ms: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, ms));
}

const sort = computed(() => filters.value.sort);
const hasFilters = computed(
  () =>
    Boolean(filters.value.q) ||
    filters.value.tags.length > 0 ||
    filters.value.period !== "all" ||
    filters.value.from !== null,
);
const emptyText = computed(() => {
  if (hasFilters.value) return "没有符合筛选条件的问题，换个条件试试。";
  if (sort.value === "latest") return "还没有公开问题，来问第一个吧。";
  if (sort.value === "hot") return "暂时没有热门内容，去看看最新问题吧。";
  return "暂时没有可推荐的内容，去看看最新问题吧。";
});

// 筛选条件变化时上一个请求可能还没回来，用序号丢弃过期响应
let requestSeq = 0;
let openedQuestionId: number | null = null;

async function load(reset = false) {
  const seq = ++requestSeq;
  const value = filters.value;
  const startedAt = performance.now();
  loading.value = true;
  error.value = "";
  try {
    const page = await api.feedQuestions(
      {
        q: value.q || undefined,
        tags: value.tags.map((name) => tagSlugMap.value[name] ?? name),
        period: value.from ? undefined : value.period,
        from: value.from,
        to: value.to,
        sort: value.sort,
      },
      reset ? null : cursor.value,
    );
    if (seq !== requestSeq) return;
    const remaining = MIN_LOADING_MS - (performance.now() - startedAt);
    if (remaining > 0) {
      await wait(remaining);
      if (seq !== requestSeq) return;
    }
    items.value = reset ? page.items : [...items.value, ...page.items];
    cursor.value = page.nextCursor;
  } catch (e) {
    if (seq === requestSeq) error.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
      await nextTick();
      lockedHeight.value = null;
    }
  }
}

function beginReload() {
  const previousCount = items.value.length;
  skeletonCount.value = previousCount > 0 ? Math.min(previousCount, 6) : 3;
  lockedHeight.value =
    previousCount > 0 && listRef.value ? listRef.value.getBoundingClientRect().height : null;
}

function applyFilters(value: FeedFilterValue) {
  beginReload();
  filters.value = value;
  cursor.value = null;
  items.value = [];
  dismissed.value = null;
  void load(true);
}

function refreshFeed() {
  beginReload();
  dismissed.value = null;
  cursor.value = null;
  items.value = [];
  void load(true);
}

function dismiss(id: number) {
  const index = items.value.findIndex((item) => item.id === id);
  if (index < 0) return;
  dismissed.value = { item: items.value[index], index };
  items.value = items.value.filter((item) => item.id !== id);
}

async function undoDismiss() {
  const entry = dismissed.value;
  if (!entry || undoing.value) return;
  undoing.value = true;
  try {
    await api.uninteract("question", entry.item.id, "dislike");
    const next = [...items.value];
    next.splice(Math.min(entry.index, next.length), 0, entry.item);
    items.value = next;
    dismissed.value = null;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "撤销失败";
  } finally {
    undoing.value = false;
  }
}

async function loadTagSlugs() {
  try {
    const list = await api.listTags({ limit: 100 });
    tagSlugMap.value = Object.fromEntries(list.map((tag) => [tag.name, tag.slug]));
  } catch {
    // 标签目录加载失败时退回用名称作 slug；固定目录里两者一致
  }
}

onMounted(() => {
  void load(true);
  void loadTagSlugs();
});

watch(() => user.value?.id, () => {
  openedQuestionId = null;
  refreshFeed();
}, { flush: "sync" });

onBeforeRouteLeave((to) => {
  openedQuestionId = to.query.from === "feed" ? Number(to.params.id) : null;
});

onActivated(async () => {
  const id = openedQuestionId;
  openedQuestionId = null;
  const item = items.value.find((question) => question.id === id);
  if (!item) return;
  try {
    const { question } = await api.getQuestionContent(item.id);
    const index = items.value.indexOf(item);
    if (index < 0) return;
    items.value[index] = {
      ...item,
      answerCount: question.answerCount,
      followupCount: question.followupCount,
      replyCount: question.answerCount + question.followupCount,
    };
  } catch {
    // 列表仍可使用，用户可重新加载获取最新数据。
  }
});
</script>

<template>
  <div class="feed-page mx-auto w-full">
    <header class="editorial-heading">
      <div>
        <h1>公开问答</h1>
        <p v-if="!user">
          游客可直接浏览，<router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">登录后参与问答</router-link>。
        </p>
        <p v-else>搜索求职问题，看看招聘者怎么说。</p>
      </div>
      <router-link v-if="!user || user.role === 'student'" class="editorial-button is-primary" to="/ask">＋ 公开提问</router-link>
    </header>
    <div class="editorial-feed-grid">
      <section class="editorial-feed-main" aria-label="公开问题列表">
        <FeedFilterPanel class="mb-4" @change="applyFilters" @refresh="refreshFeed" />

        <p v-if="error" class="m-0 mb-3 text-14 text-danger">{{ error }}</p>

        <div
          v-if="dismissed"
          class="mb-3 flex items-center justify-between gap-3 rounded-card border border-line bg-surface px-4 py-2.5"
        >
          <span class="min-w-0 truncate text-14 text-regular">
            已减少「{{ dismissed.item.title }}」这类内容
          </span>
          <button class="aux-btn shrink-0" :disabled="undoing" @click="undoDismiss">
            {{ undoing ? "撤销中…" : "撤销" }}
          </button>
        </div>

        <div
          ref="listRef"
          class="feed-list flex flex-col gap-3"
          :class="{ 'list-locked': lockedHeight !== null }"
          :style="lockedHeight !== null ? { height: `${lockedHeight}px` } : undefined"
          :aria-busy="loading"
        >
          <template v-if="showResetSkeletons">
            <FeedCardSkeleton v-for="n in skeletonCount" :key="`skeleton-${n}`" />
          </template>
          <FeedCard v-for="q in items" :key="q.id" :item="q" @dismiss="dismiss" />
          <template v-if="showMoreSkeletons">
            <FeedCardSkeleton v-for="n in MORE_SKELETON_COUNT" :key="`more-skeleton-${n}`" />
          </template>
        </div>

        <p v-if="!loading && items.length === 0" class="m-0 py-10 text-center text-14 text-muted">
          {{ emptyText }}
        </p>

        <div v-if="cursor" class="mt-5 text-center">
          <button class="aux-btn" :disabled="loading" @click="load(false)">
            {{ loading ? "加载中…" : "加载更多" }}
          </button>
        </div>
        <p v-else-if="!loading && !error && items.length" class="feed-end">问题已经看完咯</p>
      </section>
      <RoundtableFeature />
    </div>
  </div>
</template>

<style scoped>
/* 内容不足一屏时也把页面撑满，配合全局常驻滚动条避免显隐抖动 */
.feed-page {
  min-height: calc(100vh - 120px);
}

.editorial-feed-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 322px;
  align-items: start;
  gap: 24px;
}
.editorial-feed-main { min-width: 0; }

@media (max-width: 900px) {
  .editorial-feed-grid { grid-template-columns: minmax(0, 1fr) 270px; gap: 15px; }
}
@media (max-width: 720px) {
  .editorial-feed-grid { grid-template-columns: minmax(0, 1fr); }
}

.feed-end { text-align: center; margin: 18px 0 0; font-size: 13px; color: var(--color-text-muted); }

.list-locked > :deep(.feed-skeleton) {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
}

.feed-list > :deep(article:not(.feed-skeleton)) {
  animation: feed-fade-in 180ms ease-out both;
}

@keyframes feed-fade-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .feed-list > :deep(article:not(.feed-skeleton)) {
    animation: none;
  }
}
</style>
