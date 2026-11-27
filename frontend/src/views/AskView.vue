<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { onBeforeRouteLeave, useRouter } from "vue-router";
import { useDialog, useMessage } from "naive-ui";
import { api } from "../api";
import TagPicker from "../components/TagPicker.vue";
import { useUserStore } from "@/stores/user";
import { clearAskDraft, draftFingerprint, forgetRecoveredAskDraft, preserveAskDraft, readAskDraft, recoveredAskDrafts, saveAskDraft, type RecoveredAskDraft } from "@/utils/askDraft";

const router = useRouter();
const userStore = useUserStore();
const ownerId = userStore.user?.role === "student" ? userStore.user.id : null;
const restoredDraft = ownerId == null ? null : readAskDraft(ownerId);
const title = ref(restoredDraft?.title ?? "");
const body = ref(restoredDraft?.body ?? "");
const tags = ref<string[]>(restoredDraft?.tags ?? []);
const error = ref("");
const loading = ref(false);
const dialog = useDialog();
const message = useMessage();
const restored = ref(!!restoredDraft);
const saved = ref(true);
let finished = false;
let clearing = false;
const hasDraft = computed(() => !!(title.value || body.value || tags.value.length));
const recoveries = ref(ownerId == null ? [] : recoveredAskDrafts(ownerId));
const currentDraft = () => ({ title: title.value, body: body.value, tags: [...tags.value] });
let lastSavedFingerprint = draftFingerprint(currentDraft());
function persist() {
  if (finished || clearing || ownerId == null) return;
  const draft = currentDraft();
  // 未编辑的旧页面退出时不回写，也不清除其他页面刚保存的内容。
  if (saved.value && draftFingerprint(draft) === lastSavedFingerprint) return;
  saved.value = saveAskDraft(ownerId, draft, lastSavedFingerprint);
  if (saved.value) lastSavedFingerprint = draftFingerprint(draft);
  recoveries.value = recoveredAskDrafts(ownerId);
}
watch([title, body, tags], persist, { deep: true, flush: "sync" });
function beforeUnload(event: BeforeUnloadEvent) {
  persist();
  if (!saved.value && hasDraft.value) { event.preventDefault(); event.returnValue = ""; }
}
window.addEventListener("beforeunload", beforeUnload);
onBeforeRouteLeave(() => {
  persist();
  if (loading.value) { message.info("正在提交问题，请稍候。"); return false; }
  return saved.value || !hasDraft.value || window.confirm("草稿未能保存，离开会丢失当前内容，确定离开吗？");
});
onBeforeUnmount(() => { persist(); window.removeEventListener("beforeunload", beforeUnload); });
function discardDraft() {
  if (ownerId == null) return;
  dialog.warning({ title: "清空提问草稿", content: "将删除本机保存的这份草稿，确定清空吗？", positiveText: "确定清空", negativeText: "继续编辑",
    onPositiveClick: () => {
      if (!clearAskDraft(ownerId, lastSavedFingerprint)) { message.error("草稿未能清空，请重试。"); return false; }
      clearing = true; title.value = ""; body.value = ""; tags.value = []; clearing = false;
      lastSavedFingerprint = draftFingerprint(currentDraft());
      recoveries.value = recoveredAskDrafts(ownerId);
      restored.value = false; saved.value = true; return true;
    },
  });
}
function restoreRecovery(item: RecoveredAskDraft) {
  if (ownerId == null || loading.value) return;
  dialog.info({ title: "恢复未完成草稿", content: "将载入这份草稿，当前未完成内容会另存以便恢复。", positiveText: "恢复这份草稿", negativeText: "取消",
    onPositiveClick: () => {
      if (!preserveAskDraft(ownerId, currentDraft())) { message.error("当前内容未能保存，请稍后重试。"); return false; }
      clearing = true; title.value = item.draft.title; body.value = item.draft.body; tags.value = [...item.draft.tags]; clearing = false;
      persist();
      if (!saved.value) return false;
      forgetRecoveredAskDraft(item.key, ownerId); recoveries.value = recoveredAskDrafts(ownerId); restored.value = true; return true;
    },
  });
}

async function submit() {
  if (loading.value) return;
  if (ownerId == null) { void router.replace({ path: "/login", query: { redirect: "/ask" } }); return; }
  if (!title.value.trim() || !body.value.trim()) {
    error.value = "标题和描述不能为空";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const created = await api.createQuestion({
      title: title.value.trim(),
      body: body.value.trim(),
      tags: tags.value,
      scope: "public",
    });
    if (created.status === "rejected") {
      error.value = "问题未通过审核，请修改后重新提交，草稿已保留。";
      return;
    }
    finished = true;
    if (!clearAskDraft(ownerId, lastSavedFingerprint)) message.warning("问题已提交，本机草稿暂未清除，可稍后手动清空。");
    loading.value = false;
    await router.push(`/questions/${created.id}`);
  } catch (e: any) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card public-ask-form">
    <h2>我要提问</h2>
    <p class="muted ask-audience">审核通过后，问题会进入公开问题池，由 HR 自主选择回答。</p>
    <div class="draft-status" role="status">
      <span v-if="!saved" class="error">草稿未能保存，请先保留内容，暂勿关闭页面。</span>
      <span v-else-if="hasDraft">{{ restored ? '已恢复上次未完成的草稿。' : '草稿已自动保存在本机。' }}离开或刷新后可继续编辑。</span>
      <span v-else>编辑内容会自动保存，提交成功后清除草稿。</span>
      <button v-if="hasDraft" type="button" class="link" :disabled="loading" @click="discardDraft">清空草稿</button>
    </div>
    <details v-if="recoveries.length" class="draft-recoveries"><summary>其他未完成草稿（{{ recoveries.length }}）</summary><div v-for="item in recoveries" :key="item.key"><span>{{ item.draft.title || '未命名提问' }}</span><button type="button" class="link" :disabled="loading" @click="restoreRecovery(item)">恢复草稿</button></div></details>
    <fieldset :disabled="loading" class="question-fields">
    <div class="field">
      <label>标题</label>
      <input v-model="title" maxlength="200" placeholder="一句话说清你的问题" />
    </div>
    <div class="field">
      <label>描述</label>
      <textarea v-model="body" maxlength="5000" placeholder="补充背景、你已尝试的方法、希望得到什么建议" />
    </div>
    <div class="field">
      <label>文章标签（最多 5 个）</label>
      <TagPicker v-model="tags" :max="5" />
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <button class="primary" :disabled="loading" @click="submit">{{ loading ? "提交中…" : "提交" }}</button>
    </fieldset>
  </div>
</template>

<style scoped>
.question-fields { border: 0; padding: 0; margin: 0; min-width: 0; }
.draft-status { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; color: #7b8798; font-size: 12px; line-height: 1.8; margin-bottom: 20px; }
.draft-recoveries { padding: 12px 15px; margin-bottom: 18px; border-radius: 6px; background: #f2f6fd; color: #71849f; font-size: 12px; }.draft-recoveries summary { cursor: pointer; }.draft-recoveries > div { display: flex; justify-content: space-between; gap: 15px; padding-top: 12px; }.draft-recoveries span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.draft-recoveries button { flex-shrink: 0; }
</style>
