<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { api, type Favorite } from "@/api";
import { cursorPages } from "@/utils/cursorPages";
import { useUserStore } from "@/stores/user";
const route = useRoute(); const router = useRouter(); const message = useMessage();
const user = useUserStore(); const ownerId = user.user?.id; const pages = cursorPages(route.query.pages);
const items = ref<Favorite[]>([]); const loading = ref(false); const error = ref("");
const nextCursor = ref<number | null>(null); const removing = ref<number | null>(null);
let active = true; onBeforeUnmount(() => { active = false; });
function navigate(trail: number[]) { void router.replace({ path: "/me", query: { tab: "favorites", ...(trail.length ? { pages: trail.join(",") } : {}) } }); }
async function load() {
  loading.value = true; error.value = "";
  try {
    const page = await api.myFavorites(pages.at(-1));
    if (!active || user.user?.id !== ownerId) return;
    if (!page.items.length && pages.length) { navigate([]); return; }
    items.value = page.items; nextCursor.value = page.nextCursor ?? null;
  } catch (cause) { if (active) error.value = cause instanceof Error ? cause.message : "收藏加载失败，请重试。"; }
  finally { if (active) loading.value = false; }
}
function destination(item: Favorite) { return { path: `/questions/${item.questionId}`, query: { from: "student-favorites", returnTo: route.fullPath,
  ...(item.targetType === "answer" ? { answer: String(item.targetId) } : {}), ...(item.followupId ? { followup: String(item.followupId) } : {}) } }; }
async function remove(item: Favorite) {
  if (removing.value !== null || loading.value) return;
  removing.value = item.targetId;
  try { await api.uninteract(item.targetType, item.targetId, "favorite"); if (active) { await load(); message.success("已取消收藏"); } }
  catch (cause) { if (active) message.error(cause instanceof Error ? cause.message : "取消收藏失败，请重试。"); }
  finally { removing.value = null; }
}
onMounted(load);
</script>
<template>
  <section class="favorites" aria-label="我的收藏">
    <div class="favorites-heading"><p>收藏的公开问题和回答，随时回来继续阅读。</p><button type="button" :disabled="loading || removing !== null" @click="load">刷新收藏</button></div>
    <p v-if="error" class="error" role="alert">{{ error }} <button type="button" @click="load">重试</button></p>
    <p v-if="loading && !items.length" class="favorites-empty">正在读取收藏…</p>
    <div v-else-if="!items.length && !error" class="favorites-empty"><h3>还没有收藏内容</h3><p>在公开问答中点击星标，把有帮助的内容留在这里。</p><router-link to="/">去公开问答看看 ></router-link></div>
    <article v-for="item in items" :key="`${item.targetType}-${item.targetId}`" class="favorite-card">
      <div class="favorite-meta"><span>{{ item.targetType === 'question' ? '公开问题' : 'HR 回答' }}</span><time>收藏于 {{ item.createdAt.replace('T', ' ').slice(0, 16) }}</time></div>
      <h2><router-link :to="destination(item)">{{ item.title }}</router-link></h2><p>{{ item.excerpt }}</p>
      <div class="favorite-actions"><router-link :to="destination(item)">查看内容 ></router-link><button type="button" :disabled="removing !== null || loading" @click="remove(item)">{{ removing === item.targetId ? '取消中…' : '取消收藏' }}</button></div>
    </article>
    <div v-if="items.length || pages.length" class="favorite-pagination"><button type="button" :disabled="loading || removing !== null || !pages.length" @click="navigate(pages.slice(0, -1))">上一页</button><span>第 {{ pages.length + 1 }} 页</span><button type="button" :disabled="loading || removing !== null || nextCursor == null" @click="nextCursor != null && navigate([...pages, nextCursor])">下一页</button></div>
    <p v-if="items.length && !loading && !error && nextCursor == null" class="favorites-end">收藏已经看完咯</p>
  </section>
</template>
<style scoped>
.favorites { min-width: 0; }.favorites-heading,.favorite-meta,.favorite-actions { display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap; }.favorites-heading { margin-bottom: 16px; }.favorites-heading p { margin: 0; color: #8491a4; font-size: 12px; }.favorites button { cursor: pointer; }.favorites button:disabled { cursor: default; opacity: .5; }.favorite-card { padding: 22px; margin-bottom: 16px; border: 1px solid #e1e6ef; background: #fff; border-radius: 12px; }.favorite-meta { color: #8c98a9; font-size: 11px; }.favorite-meta span { background: #edf3ff; color: #5d85cb; padding: 3px 7px; border-radius: 4px; }.favorite-card h2 { margin: 13px 0 10px; font-size: 17px; line-height: 1.7; overflow-wrap: anywhere; }.favorite-card h2 a { color: #33435a; text-decoration: none; }.favorite-card p { color: #8b96a6; font-size: 13px; line-height: 1.8; overflow-wrap: anywhere; }.favorite-actions { margin-top: 20px; }.favorite-actions a { color: #2f6fed; font-size: 12px; text-decoration: none; }.favorite-actions button,.favorites-heading button { font-size: 12px; color: #7c8ca3; background: none; border: 0; }.favorite-pagination { display: flex; justify-content: center; gap: 16px; align-items: center; color: #8390a2; font-size: 12px; }.favorite-pagination button { padding: 8px 12px; background: #fff; border: 1px solid #dce3ee; border-radius: 5px; color: #6f819c; }.favorites-end { text-align: center; font-size: 11px; color: #9aa5b5; }.favorites-empty { text-align: center; padding: 55px 20px; background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; color: #8a97aa; font-size: 13px; }.favorites-empty h3 { color: #63758e; }.favorites-empty a { color: #2f6fed; text-decoration: none; }
</style>
