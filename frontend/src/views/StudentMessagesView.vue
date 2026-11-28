<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { api, type StudentMessageCategory, type StudentNotification } from "@/api";
import { useStudentNotificationStore } from "@/stores/studentNotifications";
import { useUserStore } from "@/stores/user";
import { cursorPages } from "@/utils/cursorPages";
import { clinicRoomsLocation } from "@/utils/clinicTopics";

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
  <section class="student-messages message-center">
    <nav class="messages-breadcrumb" aria-label="消息中心导航">
      <router-link to="/me" class="editorial-button">
        <span aria-hidden="true">←</span> 返回个人中心
      </router-link>
      <router-link :to="{ name: 'clinic-home' }" class="editorial-button is-primary">
        前往职场门诊 <span aria-hidden="true">→</span>
      </router-link>
    </nav>
    <header class="messages-heading"><div><p>学生 · 站内通知</p><h1>消息中心 <span v-if="notices.counts && notices.counts.total > 0">{{ notices.counts.total }} 条未读</span></h1><p>查看 HR 的建议、收到的赞和内容审核结果。</p></div><div class="heading-actions"><button type="button" :disabled="busy" @click="refresh">刷新</button><button type="button" :disabled="busy || !snapshotId || notices.counts?.total === 0" @click="markAll">{{ markingAll ? '标记中…' : '全部标为已读' }}</button></div></header>
    <div class="message-filters"><nav aria-label="消息类型"><button v-for="tab in tabs" :key="tab.value" type="button" :disabled="busy" :class="{ active: category === tab.value }" :aria-pressed="category === tab.value" @click="navigate(tab.value, unreadOnly)">{{ tab.label }}<span v-if="tab.value !== 'all' && notices.counts?.[tab.value]">{{ notices.counts[tab.value] }}</span></button></nav><label><input type="checkbox" :checked="unreadOnly" :disabled="busy" @change="navigate(category, ($event.target as HTMLInputElement).checked)" />只看未读</label></div>
    <p class="message-note">点击消息可定位具体回答或追问。已读消息仍会保留，方便之后查看。</p>
    <p v-if="notices.error" class="message-error" role="alert">未读数量暂时无法读取。<button type="button" @click="notices.refresh(true)">重试</button></p>
    <p v-if="error" class="message-error" role="alert">{{ error }} <button type="button" @click="refresh">重试</button></p>
    <div class="message-list" :aria-busy="loading">
      <div v-if="loading && !items.length" class="message-empty">正在读取消息…</div>
      <div v-else-if="!items.length && !error" class="message-empty"><strong>{{ unreadOnly ? '当前没有未读消息' : '暂时没有消息' }}</strong><p>{{ unreadOnly ? '可以关闭未读筛选，查看已读消息。' : '收到新的回答、点赞或审核结果后，会在这里通知你。' }}</p><router-link :to="clinicRoomsLocation">去诊室提问 →</router-link></div>
      <article v-for="item in items" :key="item.id" class="notification-card" :class="{ unread: !item.readAt }" :data-notification-id="item.id">
        <span class="notification-icon" :class="item.category">{{ item.category === 'reply' ? '答' : item.category === 'like' ? '赞' : '审' }}</span>
        <div class="notification-main"><div class="notification-title"><h2>{{ item.title }}</h2><span v-if="!item.readAt" class="unread-dot" aria-label="未读"></span><time>{{ formatTime(item.createdAt) }}</time></div><p v-if="item.questionTitle" class="notification-question">关于《{{ item.questionTitle }}》</p><p class="notification-message">{{ item.message }}</p><div class="notification-actions"><button v-if="item.destination === 'question'" type="button" :disabled="busy" @click="readNotification(item, true)">{{ openingId === item.id ? '打开中…' : item.category === 'reply' ? '查看回复 →' : '查看内容 →' }}</button><span v-else class="unavailable">相关内容已不可查看</span><button v-if="!item.readAt" type="button" class="mark-read" :disabled="busy" @click="readNotification(item)">标为已读</button><span v-else class="read-label">已读</span></div></div>
      </article>
    </div>
    <div v-if="items.length || pageNumber > 1" class="message-pagination"><button type="button" :disabled="busy || pageNumber <= 1" @click="navigate(category, unreadOnly, pages.slice(0, -1))">上一页</button><span>第 {{ pageNumber }} 页</span><button type="button" :disabled="busy || nextCursor == null" @click="nextCursor != null && navigate(category, unreadOnly, [...pages, nextCursor])">下一页</button></div>
    <p v-if="!loading && !error && items.length && nextCursor == null" class="messages-end">消息已经看完咯</p>
  </section>
</template>

<style scoped src="../styles/messageCenter.css"></style>
