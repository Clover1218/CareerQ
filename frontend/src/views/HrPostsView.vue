<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useDialog, useMessage } from "naive-ui";
import { api, type HrPostStatus, type HrPostSummary } from "@/api";
import { useUserStore } from "@/stores/user";
import { cursorPages } from "@/utils/cursorPages";
import { hrPostLabels, postFingerprint, readLocalHrPosts, removeLocalHrPost, saveLocalHrPost, type LocalHrPostDraft } from "@/utils/hrPosts";

const router = useRouter(); const route = useRoute(); const user = useUserStore();
const ownerId = user.user?.id; const message = useMessage(); const dialog = useDialog();
const filters = [{ value: "all", label: "全部帖子" }, ...Object.entries(hrPostLabels).map(([value, label]) => ({ value, label }))];
const status = typeof route.query.status === "string" && filters.some(filter => filter.value === route.query.status) ? route.query.status : "all";
const pages = cursorPages(route.query.pages);
const items = ref<HrPostSummary[]>([]); const nextCursor = ref<number | null>(null);
const loading = ref(false); const error = ref(""); const actingId = ref<number | null>(null);
const localDrafts = ref<{ key: string; draft: LocalHrPostDraft }[]>([]);
let active = true; onBeforeUnmount(() => { active = false; });
const busy = computed(() => loading.value || actingId.value !== null);
function updateLocalDrafts() {
  if (ownerId == null) return;
  localDrafts.value = readLocalHrPosts(ownerId).filter(item => (item.key.startsWith("recovery-")
    || postFingerprint(item.draft) !== item.draft.savedFingerprint) && (item.draft.title || item.draft.body || item.draft.tags.length));
}
async function load() {
  if (ownerId == null) return;
  loading.value = true; error.value = ""; updateLocalDrafts();
  try {
    const page = await api.hrPosts(ownerId, status, pages.at(-1));
    if (!active || ownerId !== user.user?.id) return;
    if (!page.items.length && pages.length) { navigate(status); return; }
    items.value = page.items; nextCursor.value = page.nextCursor ?? null;
  } catch (cause) { if (active) error.value = cause instanceof Error ? cause.message : "帖子加载失败，请重试。"; }
  finally { if (active) loading.value = false; }
}
function navigate(filter: string, trail: number[] = []) {
  void router.replace({ path: "/hr/posts", query: { ...(filter !== "all" ? { status: filter } : {}), ...(trail.length ? { pages: trail.join(",") } : {}) } });
}
function create() { void router.push({ path: "/hr/posts/new", query: { draft: crypto.randomUUID(), returnTo: route.fullPath } }); }
function open(item: HrPostSummary) { void router.push({ path: `/hr/posts/${item.id}`, query: { returnTo: route.fullPath } }); }
function recover(draft: LocalHrPostDraft) {
  if (ownerId == null) return;
  const key = crypto.randomUUID();
  const copy = { ...draft, postId: null, baseVersion: null, clientKey: key, savedFingerprint: "" };
  if (!saveLocalHrPost(ownerId, key, copy, "")) { message.error("本机草稿暂时无法读取保存，请稍后重试。"); return; }
  void router.push({ path: "/hr/posts/new", query: { draft: key, returnTo: route.fullPath } });
}
function forget(key: string, draft: LocalHrPostDraft) {
  if (ownerId == null) return;
  dialog.warning({ title: "删除本机备份", content: "只删除这份本机未同步内容，服务器上的帖子不受影响。确定删除吗？", positiveText: "确定删除", negativeText: "保留",
    onPositiveClick: () => {
      if (!removeLocalHrPost(ownerId, key, postFingerprint(draft))) { message.error("删除失败，请重试。"); return false; }
      updateLocalDrafts(); return true;
    },
  });
}
function action(item: HrPostSummary, operation: "withdraw" | "delete") {
  if (busy.value || ownerId == null) return;
  dialog.warning({ title: operation === "delete" ? "删除草稿" : "撤回帖子", content: operation === "delete" ? "草稿删除后无法恢复，确定删除吗？" : "撤回后可以继续修改，重新提交时会再次审核。确定撤回吗？",
    positiveText: operation === "delete" ? "确定删除" : "确定撤回", negativeText: "取消",
    onPositiveClick: async () => {
      if (actingId.value !== null) return false;
      actingId.value = item.id;
      try {
        if (operation === "delete") await api.deleteHrPostDraft(ownerId, item.id, item.version);
        else await api.withdrawHrPost(ownerId, item.id, item.version);
        await load(); message.success(operation === "delete" ? "草稿已删除" : "帖子已撤回"); return true;
      } catch (cause) { message.error(cause instanceof Error ? cause.message : "操作失败，请刷新后重试。"); return false; }
      finally { actingId.value = null; }
    },
  });
}
const formatTime = (value: string) => value.replace("T", " ").slice(0, 16);
onMounted(load);
</script>

<template>
  <section class="hr-post-page">
    <div class="post-breadcrumb"><router-link to="/hr/profile">← HR 个人中心</router-link><router-link to="/hr">前往工作台 ↗</router-link></div>
    <header class="post-page-heading"><div><p class="post-eyebrow">HR 分享 · 创作管理</p><h1>我的帖子</h1><p>把招聘经验与职业思考，整理成一篇自己的分享。</p></div><button type="button" class="post-primary" @click="create">＋ 写一篇分享</button></header>
    <p class="post-stage-note">当前支持保存草稿和提交审核。帖子审核与公开阅读将在后续阶段接入，待审核内容暂不会公开。</p>
    <nav class="post-filters" aria-label="帖子状态"><button v-for="filter in filters" :key="filter.value" type="button" :class="{ active: status === filter.value }" :aria-pressed="status === filter.value" :disabled="busy" @click="navigate(filter.value)">{{ filter.label }}</button><button type="button" :disabled="busy" @click="load">刷新</button></nav>
    <details v-if="localDrafts.length" class="post-local-drafts"><summary>本机未同步内容与备份（{{ localDrafts.length }}）</summary><div v-for="item in localDrafts" :key="item.key"><span>{{ item.draft.title || '未命名草稿' }}</span><button type="button" @click="recover(item.draft)">另存为新帖</button><button type="button" @click="forget(item.key, item.draft)">删除备份</button></div></details>
    <p v-if="error" class="post-error" role="alert">{{ error }} <button type="button" @click="load">重试</button></p>
    <div v-if="loading && !items.length" class="post-empty">正在读取帖子…</div>
    <div v-else-if="!items.length && !error" class="post-empty"><span class="post-empty-icon">✎</span><h2>{{ status === 'all' ? '你的经验，值得被更多人看见' : '这个分类暂时没有帖子' }}</h2><p>从一次面试经历、一个招聘观察，或一条职业建议开始。</p><button class="post-primary" type="button" @click="create">写第一篇分享</button></div>
    <div class="post-list" :aria-busy="loading"><article v-for="item in items" :key="item.id" class="post-card" :data-post-id="item.id"><div class="post-card-meta"><span class="post-status" :class="item.status">{{ hrPostLabels[item.status] }}</span><time>更新于 {{ formatTime(item.updatedAt) }}</time></div><h2><button type="button" @click="open(item)">{{ item.title || '未命名草稿' }}</button></h2><p>{{ item.excerpt || '还没有正文，继续写下你的想法。' }}</p><div class="post-tag-row"><span v-for="tag in item.tags" :key="tag">{{ tag }}</span></div><div class="post-card-actions"><button type="button" @click="open(item)">{{ ['draft', 'rejected', 'withdrawn'].includes(item.status) ? '继续编辑 →' : '查看帖子 →' }}</button><button v-if="['pending_human', 'published'].includes(item.status)" type="button" :disabled="busy" @click="action(item, 'withdraw')">撤回帖子</button><button v-if="item.status === 'draft'" type="button" :disabled="busy" @click="action(item, 'delete')">删除草稿</button></div></article></div>
    <div v-if="items.length || pages.length" class="post-pagination"><button type="button" :disabled="busy || !pages.length" @click="navigate(status, pages.slice(0, -1))">上一页</button><span>第 {{ pages.length + 1 }} 页</span><button type="button" :disabled="busy || nextCursor == null" @click="nextCursor != null && navigate(status, [...pages, nextCursor])">下一页</button></div>
    <p v-if="items.length && !loading && !error && nextCursor == null" class="post-end">帖子已经看完咯</p>
  </section>
</template>
<style src="../styles/hrPosts.css"></style>
