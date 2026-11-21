<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { api, type HrMessageCategory, type HrNotification } from "@/api";
import { useHrNotificationStore } from "@/stores/hrNotifications";
import { useUserStore } from "@/stores/user";

defineOptions({ name: "HrMessagesView" });
const route = useRoute();
const router = useRouter();
const notices = useHrNotificationStore();
const user = useUserStore();
const ownerId = user.user?.id;
const toast = useMessage();
const category = ref<HrMessageCategory>("all");
const unreadOnly = ref(false);
// KeepAlive 离开消息页后继续保留筛选，详情页的查询参数不应重置消息列表。
watch(() => [route.name, route.query.category, route.query.unread], () => {
  if (route.name !== "hr-messages" || user.user?.id !== ownerId) return;
  category.value = route.query.category === "followup" || route.query.category === "system" ? route.query.category : "all";
  unreadOnly.value = route.query.unread === "true";
}, { immediate: true });
const tabs: { value: HrMessageCategory; label: string }[] = [
  { value: "all", label: "全部消息" }, { value: "followup", label: "学生追问" }, { value: "system", label: "审核与系统通知" },
];
const items = ref<HrNotification[]>([]);
const cursors = ref<(number | null)[]>([null]);
const nextCursor = ref<number | null>(null);
const snapshotId = ref(0);
const loading = ref(false);
const error = ref("");
const openingId = ref<number | null>(null);
const markingAll = ref(false);
const pageNumber = computed(() => cursors.value.length);
let sequence = 0;
let activated = false;
let returnScroll: number | null = null;

async function loadPage(cursor: number | null): Promise<boolean> {
  if (user.user?.id !== ownerId) return false;
  const current = ++sequence;
  loading.value = true;
  error.value = "";
  try {
    let page = await api.hrNotifications(category.value, unreadOnly.value, cursor);
    if (current !== sequence || user.user?.id !== ownerId) return false;
    const reset = cursor !== null && !page.items.length;
    if (reset) {
      page = await api.hrNotifications(category.value, unreadOnly.value);
      if (current !== sequence || user.user?.id !== ownerId) return false;
      cursors.value = [null];
    }
    items.value = page.items;
    nextCursor.value = page.nextCursor;
    snapshotId.value = page.snapshotId;
    return !reset;
  } catch (cause) {
    if (current === sequence) error.value = cause instanceof Error ? cause.message : "消息加载失败，请重试。";
    return false;
  } finally {
    if (current === sequence) loading.value = false;
  }
}
async function refresh() {
  await Promise.all([loadPage(cursors.value.at(-1) ?? null), notices.refresh(true)]);
}
function changeFilter(value: HrMessageCategory, onlyUnread = unreadOnly.value) {
  void router.replace({ path: "/hr/messages", query: { ...(value === "all" ? {} : { category: value }), ...(onlyUnread ? { unread: "true" } : {}) } });
}
watch([category, unreadOnly], () => {
  if (user.user?.id !== ownerId) return;
  cursors.value = [null];
  nextCursor.value = null;
  items.value = [];
  snapshotId.value = 0;
  void refresh();
});
async function nextPage() {
  if (loading.value || nextCursor.value == null) return;
  const cursor = nextCursor.value;
  if (await loadPage(cursor)) cursors.value.push(cursor);
}
async function previousPage() {
  if (loading.value || pageNumber.value <= 1) return;
  if (await loadPage(cursors.value.at(-2) ?? null)) cursors.value.pop();
}
async function markAll() {
  if (markingAll.value || loading.value || !snapshotId.value) return;
  markingAll.value = true;
  try {
    await notices.readAll(snapshotId.value);
    await loadPage(cursors.value.at(-1) ?? null);
    toast.success("已有消息已全部标为已读");
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : "标记失败，请重试。");
  } finally { markingAll.value = false; }
}
async function readNotification(item: HrNotification, navigate = false) {
  if (openingId.value !== null || markingAll.value) return;
  openingId.value = item.id;
  try {
    // 写接口同时重新判断目标可见性，点击前后内容变更时不会使用陈旧跳转地址。
    const current = await notices.read(item.id);
    if (user.user?.id !== ownerId) return;
    Object.assign(item, current);
    if (navigate && current.destination !== "unavailable") {
      returnScroll = window.scrollY;
      if (current.destination === "answer_record" && current.answerId != null) {
        await router.push({ path: "/hr/profile", query: { from: "hr-messages", answer: String(current.answerId) } });
      } else {
        await router.push({ path: `/questions/${current.questionId}`, query: {
          from: "hr-messages",
          ...(current.type === "followup_published" ? { followup: String(current.followupId) } : { answer: String(current.answerId) }),
        } });
      }
    } else {
      if (navigate) toast.info("相关内容已不可查看，这条消息已标为已读。");
      if (unreadOnly.value) await loadPage(cursors.value.at(-1) ?? null);
    }
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : "消息读取失败，请重试。");
  } finally { openingId.value = null; }
}
function formatTime(time: string) { return time.replace("T", " ").slice(0, 16); }
onMounted(refresh);
onActivated(async () => {
  if (!activated) { activated = true; return; }
  await refresh();
  if (returnScroll !== null) {
    await nextTick();
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
    window.scrollTo(0, returnScroll);
    returnScroll = null;
  }
});
</script>

<template>
  <section class="hr-messages">
    <div class="messages-breadcrumb"><router-link to="/hr/profile">← 个人中心</router-link><router-link to="/hr">前往 HR 工作台 ↗</router-link></div>
    <header class="messages-heading"><div><p>HR 答主 · 站内通知</p><h1>消息中心 <span v-if="notices.counts && notices.counts.total > 0">{{ notices.counts.total }} 条未读</span></h1><p>跟进学生追问，查看你的回答审核结果。</p></div><div class="heading-actions"><button type="button" :disabled="loading" @click="refresh">刷新</button><button type="button" :disabled="loading || markingAll || openingId !== null || !snapshotId || notices.counts?.total === 0" @click="markAll">{{ markingAll ? '标记中…' : '全部标为已读' }}</button></div></header>
    <div class="message-filters"><nav aria-label="消息类型"><button v-for="tab in tabs" :key="tab.value" type="button" :class="{ active: category === tab.value }" :aria-pressed="category === tab.value" @click="changeFilter(tab.value)">{{ tab.label }}<span v-if="tab.value !== 'all' && notices.counts?.[tab.value]">{{ notices.counts[tab.value] }}</span></button></nav><label><input type="checkbox" :checked="unreadOnly" @change="changeFilter(category, ($event.target as HTMLInputElement).checked)" />只看未读</label></div>
    <p class="message-note">读过消息后，工作台里的待回复追问仍会保留，直到你提交有效回复。</p>
    <p v-if="notices.error" class="message-error" role="alert">未读数量暂时无法读取。<button type="button" @click="notices.refresh(true)">重试</button></p>
    <p v-if="error" class="message-error" role="alert">{{ error }} <button type="button" @click="refresh">重试</button></p>
    <div class="message-list" :aria-busy="loading">
      <div v-if="loading && !items.length" class="message-empty">正在读取消息…</div>
      <div v-else-if="!items.length && !error" class="message-empty"><strong>{{ unreadOnly ? '当前没有未读消息' : '暂时没有新消息' }}</strong><p>{{ unreadOnly ? '可以切换到全部消息，查看已读记录。' : '新的学生追问、人工审核结果和运营下架通知会出现在这里。' }}</p><router-link to="/hr">去工作台看看 →</router-link></div>
      <article v-for="item in items" :key="item.id" class="notification-card" :class="{ unread: !item.readAt }" :data-notification-id="item.id">
        <span class="notification-icon" :class="item.category">{{ item.category === 'followup' ? '问' : '审' }}</span>
        <div class="notification-main"><div class="notification-title"><h2>{{ item.title }}</h2><span v-if="!item.readAt" class="unread-dot" aria-label="未读"></span><time>{{ formatTime(item.createdAt) }}</time></div><p v-if="item.questionTitle" class="notification-question">关于《{{ item.questionTitle }}》</p><p class="notification-message">{{ item.message }}</p><div class="notification-actions"><button v-if="item.destination !== 'unavailable'" type="button" :disabled="openingId !== null || markingAll" @click="readNotification(item, true)">{{ openingId === item.id ? '打开中…' : item.destination === 'answer_record' ? '查看回答记录 →' : item.category === 'followup' ? '查看追问 →' : '查看回答 →' }}</button><span v-else class="unavailable">相关内容已不可查看</span><button v-if="!item.readAt" class="mark-read" type="button" :disabled="openingId !== null || markingAll" @click="readNotification(item)">标为已读</button><span v-else class="read-label">已读</span></div></div>
      </article>
    </div>
    <div v-if="items.length || pageNumber > 1" class="message-pagination"><button type="button" :disabled="loading || pageNumber <= 1" @click="previousPage">上一页</button><span>第 {{ pageNumber }} 页</span><button type="button" :disabled="loading || nextCursor == null" @click="nextPage">下一页</button></div>
    <p v-if="!loading && !error && items.length && nextCursor == null" class="messages-end">消息已经看完咯</p>
  </section>
</template>

<style scoped>
.hr-messages { max-width: 1000px; margin: 0 auto; padding: 30px 28px 64px; }
.messages-breadcrumb { display: flex; justify-content: space-between; margin-bottom: 25px; font-size: 12px; }.messages-breadcrumb a { color: #718099; text-decoration: none; }.messages-breadcrumb a:hover { color: #2f6fed; }
.messages-heading { display: flex; justify-content: space-between; gap: 20px; align-items: center; margin-bottom: 29px; }.messages-heading h1 { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin: 8px 0 12px; font-size: 27px; }.messages-heading h1 span { background: #fff0ef; color: #d5655d; padding: 4px 8px; border-radius: 5px; font-size: 11px; font-weight: 500; }.messages-heading p { margin: 0; color: #8792a4; font-size: 12px; line-height: 1.8; }.heading-actions { display: flex; gap: 10px; flex-shrink: 0; }.heading-actions button { padding: 9px 13px; border: 1px solid #dce3ee; border-radius: 6px; background: #fff; color: #60728e; font-size: 12px; cursor: pointer; }
.message-filters { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 14px; border-bottom: 1px solid #e1e6ee; }.message-filters nav { display: flex; flex-wrap: wrap; gap: 8px; }.message-filters nav button { display: flex; align-items: center; gap: 6px; padding: 9px 12px; border: 0; background: none; color: #7d899b; border-radius: 6px; font-size: 13px; cursor: pointer; }.message-filters nav button.active { color: #2f6fed; background: #eaf1ff; }.message-filters nav span { font-size: 10px; border-radius: 8px; padding: 1px 5px; background: #fff; }.message-filters label { display: flex; align-items: center; gap: 7px; color: #7d899b; font-size: 12px; white-space: nowrap; }.message-filters input { accent-color: #2f6fed; }.message-note { margin: 14px 0 20px; color: #929cad; font-size: 11px; line-height: 1.8; }
.message-list { display: grid; gap: 13px; }.notification-card { display: flex; align-items: flex-start; gap: 16px; padding: 23px; border: 1px solid #e5e9f0; border-radius: 10px; background: #fff; }.notification-card.unread { border-color: #d8e3fa; }.notification-icon { display: grid; place-items: center; width: 39px; height: 39px; flex: 0 0 39px; border-radius: 10px; background: #f0f2f7; color: #8796b1; font-size: 15px; }.notification-icon.followup { background: #eef4ff; color: #5c87ce; }.notification-main { flex: 1; min-width: 0; }.notification-title { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }.notification-title h2 { margin: 0; color: #33435a; font-size: 15px; line-height: 1.7; }.unread-dot { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: #2f6fed; }.notification-title time { margin-left: auto; color: #9aa4b3; font-size: 11px; }.notification-question { margin: 11px 0 0; color: #687b97; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }.notification-message { margin: 7px 0 0; color: #8a95a5; font-size: 12px; line-height: 1.9; }.notification-actions { display: flex; align-items: center; gap: 15px; margin-top: 16px; }.notification-actions button,.message-error button { padding: 0; border: 0; background: none; color: #2f6fed; font-size: 12px; cursor: pointer; }.notification-actions .mark-read { color: #8a95a5; }.read-label { margin-left: auto; color: #a4adba; font-size: 11px; }.unavailable { color: #9aa4b3; font-size: 12px; }
.message-empty { padding: 68px 20px; background: #fff; border: 1px solid #e5e9f0; border-radius: 10px; text-align: center; color: #8895a8; font-size: 13px; }.message-empty strong { color: #64758e; font-size: 16px; }.message-empty p { margin: 12px 0 23px; line-height: 1.9; }.message-empty a { color: #2f6fed; text-decoration: none; font-size: 12px; }.message-pagination { display: flex; justify-content: center; align-items: center; gap: 18px; margin-top: 25px; color: #7c899e; font-size: 12px; }.message-pagination button { padding: 8px 14px; border: 1px solid #dce3ee; border-radius: 5px; background: #fff; color: #60728e; cursor: pointer; }.messages-end { text-align: center; margin: 12px 0; color: #9aa5b5; font-size: 11px; }.hr-messages button:disabled { opacity: .5; cursor: default; }.message-error { color: #b75e57; font-size: 12px; line-height: 1.8; }
@media (max-width: 680px) { .hr-messages { padding: 22px 14px 45px; }.messages-heading { align-items: flex-start; flex-direction: column; gap: 17px; }.message-filters { align-items: flex-start; flex-direction: column; gap: 10px; }.message-filters nav { gap: 3px; }.message-filters nav button { padding: 8px 10px; font-size: 12px; }.notification-card { padding: 18px 15px; gap: 11px; }.notification-icon { width: 30px; height: 30px; flex-basis: 30px; border-radius: 8px; font-size: 12px; }.notification-title h2 { font-size: 14px; }.notification-title time { width: 100%; margin: 0; font-size: 10px; }.notification-actions { gap: 12px; flex-wrap: wrap; } }
</style>
