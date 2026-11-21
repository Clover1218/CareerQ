<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { api, type StudentMessageCategory, type StudentNotification } from "@/api";
import { useStudentNotificationStore } from "@/stores/studentNotifications";
import { useUserStore } from "@/stores/user";
import { cursorPages } from "@/utils/cursorPages";

const route = useRoute(); const router = useRouter(); const toast = useMessage();
const notices = useStudentNotificationStore(); const user = useUserStore(); const ownerId = user.user?.id;
const category: StudentMessageCategory = ["reply", "like", "system"].includes(String(route.query.category)) ? route.query.category as StudentMessageCategory : "all";
const unreadOnly = route.query.unread === "true";
const pages = cursorPages(route.query.pages); const pageNumber = pages.length + 1;
const tabs: { value: StudentMessageCategory; label: string }[] = [
  { value: "all", label: "全部消息" }, { value: "reply", label: "回复我的" },
  { value: "like", label: "收到的赞" }, { value: "system", label: "审核与系统通知" },
];
const items = ref<StudentNotification[]>([]); const nextCursor = ref<number | null>(null);
const snapshotId = ref(0); const loading = ref(false); const error = ref("");
const openingId = ref<number | null>(null); const markingAll = ref(false);
let sequence = 0; let active = true;
onBeforeUnmount(() => { active = false; ++sequence; });
const busy = computed(() => loading.value || openingId.value !== null || markingAll.value);
function navigate(value: StudentMessageCategory, onlyUnread: boolean, trail: number[] = []) {
  void router.replace({ path: "/messages", query: { ...(value === "all" ? {} : { category: value }),
    ...(onlyUnread ? { unread: "true" } : {}), ...(trail.length ? { pages: trail.join(",") } : {}) } });
}
async function load() {
  const current = ++sequence; loading.value = true; error.value = "";
  try {
    const page = await api.studentNotifications(category, unreadOnly, pages.at(-1));
    if (!active || current !== sequence || user.user?.id !== ownerId) return;
    if (!page.items.length && pages.length) { navigate(category, unreadOnly); return; }
    items.value = page.items; nextCursor.value = page.nextCursor ?? null; snapshotId.value = page.snapshotId;
  } catch (cause) { if (active && current === sequence) error.value = cause instanceof Error ? cause.message : "消息加载失败，请重试。"; }
  finally { if (active && current === sequence) loading.value = false; }
}
async function refresh() { await Promise.all([load(), notices.refresh(true)]); }
async function markAll() {
  if (busy.value || !snapshotId.value) return;
  markingAll.value = true;
  try { await notices.readAll(snapshotId.value); if (active) { await load(); toast.success("已有消息已全部标为已读"); } }
  catch (cause) { if (active) toast.error(cause instanceof Error ? cause.message : "标记失败，请重试。"); }
  finally { markingAll.value = false; }
}
async function readNotification(item: StudentNotification, navigateToContent = false) {
  if (busy.value) return;
  openingId.value = item.id;
  try {
    const current = await notices.read(item.id);
    if (!active || user.user?.id !== ownerId) return;
    Object.assign(item, current);
    if (navigateToContent && current.destination === "question") {
      await router.push({ path: `/questions/${current.questionId}`, query: { from: "student-messages", returnTo: route.fullPath,
        ...(current.answerId ? { answer: String(current.answerId) } : {}),
        ...(current.followupId ? { followup: String(current.followupId) } : {}) } });
    } else {
      if (navigateToContent) toast.info("相关内容已不可查看，这条消息已标为已读。");
      if (unreadOnly) await load();
    }
  } catch (cause) { if (active) toast.error(cause instanceof Error ? cause.message : "消息读取失败，请重试。"); }
  finally { openingId.value = null; }
}
const formatTime = (time: string) => time.replace("T", " ").slice(0, 16);
onMounted(refresh);
</script>

<template>
  <section class="student-messages">
    <div class="messages-breadcrumb"><router-link to="/me">← 我的问答</router-link><router-link to="/">前往公开问答 ↗</router-link></div>
    <header class="messages-heading"><div><p>学生 · 站内通知</p><h1>消息中心 <span v-if="notices.counts && notices.counts.total > 0">{{ notices.counts.total }} 条未读</span></h1><p>查看 HR 的建议、收到的赞和内容审核结果。</p></div><div class="heading-actions"><button type="button" :disabled="busy" @click="refresh">刷新</button><button type="button" :disabled="busy || !snapshotId || notices.counts?.total === 0" @click="markAll">{{ markingAll ? '标记中…' : '全部标为已读' }}</button></div></header>
    <div class="message-filters"><nav aria-label="消息类型"><button v-for="tab in tabs" :key="tab.value" type="button" :disabled="busy" :class="{ active: category === tab.value }" :aria-pressed="category === tab.value" @click="navigate(tab.value, unreadOnly)">{{ tab.label }}<span v-if="tab.value !== 'all' && notices.counts?.[tab.value]">{{ notices.counts[tab.value] }}</span></button></nav><label><input type="checkbox" :checked="unreadOnly" :disabled="busy" @change="navigate(category, ($event.target as HTMLInputElement).checked)" />只看未读</label></div>
    <p class="message-note">点击消息可定位具体回答或追问。已读消息仍会保留，方便之后查看。</p>
    <p v-if="notices.error" class="message-error" role="alert">未读数量暂时无法读取。<button type="button" @click="notices.refresh(true)">重试</button></p>
    <p v-if="error" class="message-error" role="alert">{{ error }} <button type="button" @click="refresh">重试</button></p>
    <div class="message-list" :aria-busy="loading">
      <div v-if="loading && !items.length" class="message-empty">正在读取消息…</div>
      <div v-else-if="!items.length && !error" class="message-empty"><strong>{{ unreadOnly ? '当前没有未读消息' : '暂时没有消息' }}</strong><p>{{ unreadOnly ? '可以关闭未读筛选，查看已读消息。' : '收到新的回答、点赞或审核结果后，会在这里通知你。' }}</p><router-link to="/ask">去提出一个问题 →</router-link></div>
      <article v-for="item in items" :key="item.id" class="notification-card" :class="{ unread: !item.readAt }" :data-notification-id="item.id">
        <span class="notification-icon" :class="item.category">{{ item.category === 'reply' ? '答' : item.category === 'like' ? '赞' : '审' }}</span>
        <div class="notification-main"><div class="notification-title"><h2>{{ item.title }}</h2><span v-if="!item.readAt" class="unread-dot" aria-label="未读"></span><time>{{ formatTime(item.createdAt) }}</time></div><p v-if="item.questionTitle" class="notification-question">关于《{{ item.questionTitle }}》</p><p class="notification-message">{{ item.message }}</p><div class="notification-actions"><button v-if="item.destination === 'question'" type="button" :disabled="busy" @click="readNotification(item, true)">{{ openingId === item.id ? '打开中…' : item.category === 'reply' ? '查看回复 →' : '查看内容 →' }}</button><span v-else class="unavailable">相关内容已不可查看</span><button v-if="!item.readAt" type="button" class="mark-read" :disabled="busy" @click="readNotification(item)">标为已读</button><span v-else class="read-label">已读</span></div></div>
      </article>
    </div>
    <div v-if="items.length || pageNumber > 1" class="message-pagination"><button type="button" :disabled="busy || pageNumber <= 1" @click="navigate(category, unreadOnly, pages.slice(0, -1))">上一页</button><span>第 {{ pageNumber }} 页</span><button type="button" :disabled="busy || nextCursor == null" @click="nextCursor != null && navigate(category, unreadOnly, [...pages, nextCursor])">下一页</button></div>
    <p v-if="!loading && !error && items.length && nextCursor == null" class="messages-end">消息已经看完咯</p>
  </section>
</template>

<style scoped>
.student-messages { max-width: 1000px; margin: 0 auto; padding: 30px 28px 64px; }
.messages-breadcrumb { display: flex; justify-content: space-between; margin-bottom: 25px; font-size: 12px; }.messages-breadcrumb a { color: #718099; text-decoration: none; }.messages-breadcrumb a:hover { color: #2f6fed; }
.messages-heading { display: flex; justify-content: space-between; gap: 20px; align-items: center; margin-bottom: 29px; }.messages-heading h1 { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin: 8px 0 12px; font-size: 27px; }.messages-heading h1 span { background: #fff0ef; color: #d5655d; padding: 4px 8px; border-radius: 5px; font-size: 11px; font-weight: 500; }.messages-heading p { margin: 0; color: #8792a4; font-size: 12px; line-height: 1.8; }.heading-actions { display: flex; gap: 10px; flex-shrink: 0; }.heading-actions button { padding: 9px 13px; border: 1px solid #dce3ee; border-radius: 6px; background: #fff; color: #60728e; font-size: 12px; cursor: pointer; }
.message-filters { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 14px; border-bottom: 1px solid #e1e6ee; }.message-filters nav { display: flex; flex-wrap: wrap; gap: 8px; }.message-filters nav button { display: flex; align-items: center; gap: 6px; padding: 9px 12px; border: 0; background: none; color: #7d899b; border-radius: 6px; font-size: 13px; cursor: pointer; }.message-filters nav button.active { color: #2f6fed; background: #eaf1ff; }.message-filters nav span { font-size: 10px; border-radius: 8px; padding: 1px 5px; background: #fff; }.message-filters label { display: flex; align-items: center; gap: 7px; color: #7d899b; font-size: 12px; white-space: nowrap; }.message-filters input { accent-color: #2f6fed; }.message-note { margin: 14px 0 20px; color: #929cad; font-size: 11px; line-height: 1.8; }
.message-list { display: grid; gap: 13px; }.notification-card { display: flex; align-items: flex-start; gap: 16px; padding: 23px; border: 1px solid #e5e9f0; border-radius: 10px; background: #fff; }.notification-card.unread { border-color: #d8e3fa; }.notification-icon { display: grid; place-items: center; width: 39px; height: 39px; flex: 0 0 39px; border-radius: 10px; background: #f0f2f7; color: #8796b1; font-size: 15px; }.notification-icon.reply { background: #eef4ff; color: #5c87ce; }.notification-main { flex: 1; min-width: 0; }.notification-title { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }.notification-title h2 { margin: 0; color: #33435a; font-size: 15px; line-height: 1.7; }.unread-dot { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: #2f6fed; }.notification-title time { margin-left: auto; color: #9aa4b3; font-size: 11px; }.notification-question { margin: 11px 0 0; color: #687b97; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }.notification-message { margin: 7px 0 0; color: #8a95a5; font-size: 12px; line-height: 1.9; }.notification-actions { display: flex; align-items: center; gap: 15px; margin-top: 16px; }.notification-actions button,.message-error button { padding: 0; border: 0; background: none; color: #2f6fed; font-size: 12px; cursor: pointer; }.notification-actions .mark-read { color: #8a95a5; }.read-label { margin-left: auto; color: #a4adba; font-size: 11px; }.unavailable { color: #9aa4b3; font-size: 12px; }
.message-empty { padding: 68px 20px; background: #fff; border: 1px solid #e5e9f0; border-radius: 10px; text-align: center; color: #8895a8; font-size: 13px; }.message-empty strong { color: #64758e; font-size: 16px; }.message-empty p { margin: 12px 0 23px; line-height: 1.9; }.message-empty a { color: #2f6fed; text-decoration: none; font-size: 12px; }.message-pagination { display: flex; justify-content: center; align-items: center; gap: 18px; margin-top: 25px; color: #7c899e; font-size: 12px; }.message-pagination button { padding: 8px 14px; border: 1px solid #dce3ee; border-radius: 5px; background: #fff; color: #60728e; cursor: pointer; }.messages-end { text-align: center; margin: 12px 0; color: #9aa5b5; font-size: 11px; }.student-messages button:disabled { opacity: .5; cursor: default; }.message-error { color: #b75e57; font-size: 12px; line-height: 1.8; }
@media (max-width: 680px) { .student-messages { padding: 22px 14px 45px; }.messages-heading { align-items: flex-start; flex-direction: column; gap: 17px; }.message-filters { align-items: flex-start; flex-direction: column; gap: 10px; }.message-filters nav { gap: 3px; }.message-filters nav button { padding: 8px 10px; font-size: 12px; }.notification-card { padding: 18px 15px; gap: 11px; }.notification-icon { width: 30px; height: 30px; flex-basis: 30px; border-radius: 8px; font-size: 12px; }.notification-title h2 { font-size: 14px; }.notification-title time { width: 100%; margin: 0; font-size: 10px; }.notification-actions { gap: 12px; flex-wrap: wrap; } }
</style>
