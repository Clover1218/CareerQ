<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { useDialog, useMessage } from "naive-ui";
import { api, ApiError, type HrPost, type HrPostContent } from "@/api";
import { useUserStore } from "@/stores/user";
import TagPicker from "@/components/TagPicker.vue";
import { backupLocalHrPost, editableHrPost, hrPostLabels, localHrPost, postFingerprint, removeLocalHrPost, saveLocalHrPost, type LocalHrPostDraft } from "@/utils/hrPosts";

const route = useRoute(); const router = useRouter(); const dialog = useDialog(); const toast = useMessage();
const user = useUserStore(); const ownerId = user.user?.role === "hr" ? user.user.id : null;
const requestedId = route.params.id == null ? null : Number(route.params.id);
const newKey = typeof route.query.draft === "string" && /^[a-zA-Z0-9-]{16,64}$/.test(route.query.draft) ? route.query.draft : "new";
const localKey = requestedId == null ? newKey : `post-${requestedId}`;
const local = ownerId == null ? null : localHrPost(ownerId, localKey);
const clientKey = local?.clientKey ?? crypto.randomUUID();
const post = ref<HrPost | null>(null); const title = ref(local?.title ?? ""); const body = ref(local?.body ?? ""); const tags = ref<string[]>(local?.tags ?? []);
const loading = ref(true); const saving = ref(false); const submitting = ref(false); const withdrawing = ref(false);
const error = ref(""); const saveError = ref(""); const localSaved = ref(true); const paused = ref(false);
const preview = ref(false); const restored = ref(!!local); const lastSavedAt = ref("");
const content = (): HrPostContent => ({ title: title.value, body: body.value, tags: [...tags.value] });
const fingerprint = computed(() => postFingerprint(content()));
const savedFingerprint = ref(local?.savedFingerprint ?? postFingerprint({ title: "", body: "", tags: [] }));
const baseVersion = ref<number | null>(local?.baseVersion ?? null);
let lastLocalFingerprint = local ? postFingerprint(local) : "";
let active = true; let applying = false; let finished = false; let saveTimer: ReturnType<typeof setTimeout> | null = null;
let savePromise: Promise<boolean> | null = null;
const editable = computed(() => !post.value || editableHrPost(post.value.status));
const hasContent = computed(() => !!(title.value || body.value || tags.value.length));
const dirty = computed(() => fingerprint.value !== savedFingerprint.value);
const busy = computed(() => submitting.value || withdrawing.value);
const returnTo = computed(() => typeof route.query.returnTo === "string" && (route.query.returnTo === "/hr/posts" || route.query.returnTo.startsWith("/hr/posts?")) ? route.query.returnTo : "/hr/posts");
function localSnapshot(): LocalHrPostDraft {
  return { ...content(), clientKey, postId: post.value?.id ?? local?.postId ?? requestedId,
    baseVersion: baseVersion.value, savedFingerprint: savedFingerprint.value, updatedAt: new Date().toISOString() };
}
function persistLocal() {
  if (ownerId == null || finished || applying || loading.value || !editable.value) return;
  const snapshot = localSnapshot();
  localSaved.value = saveLocalHrPost(ownerId, localKey, snapshot, lastLocalFingerprint);
  if (localSaved.value) lastLocalFingerprint = postFingerprint(snapshot);
}
function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer);
  if (loading.value || finished || paused.value || !editable.value || !dirty.value || busy.value) return;
  saveTimer = setTimeout(() => { saveTimer = null; void saveDraft(); }, 1000);
}
watch([title, body, tags], () => { if (applying) return; persistLocal(); scheduleSave(); }, { deep: true, flush: "sync" });
function applyPost(value: HrPost) {
  applying = true; post.value = value; title.value = value.title; body.value = value.body; tags.value = [...value.tags]; applying = false;
  savedFingerprint.value = postFingerprint(value); paused.value = false; saveError.value = ""; lastSavedAt.value = value.updatedAt;
  baseVersion.value = value.version; finished = false;
}
async function load() {
  if (ownerId == null) { loading.value = false; error.value = "请先以 HR 身份登录。"; return; }
  const id = requestedId ?? local?.postId;
  if (id != null && (!Number.isSafeInteger(id) || id <= 0)) { loading.value = false; error.value = "帖子地址无效。"; return; }
  loading.value = true; error.value = "";
  try {
    if (id != null) {
      const value = await api.hrPost(ownerId, id);
      if (!active || user.user?.id !== ownerId) return;
      post.value = value; lastSavedAt.value = value.updatedAt;
      const localChanged = local && postFingerprint(local) !== local.savedFingerprint;
      if (localChanged && postFingerprint(local) !== postFingerprint(value) && editableHrPost(value.status)) {
        savedFingerprint.value = postFingerprint(value);
        paused.value = local.baseVersion !== value.version;
        if (paused.value) saveError.value = "服务器上已有其他页面保存的新版本。本页未同步内容已保留，请选择读取服务器版本或另存为新帖。";
      } else {
        if (localChanged && !backupLocalHrPost(ownerId, local)) throw new Error("本机内容未能备份，请先保留原文。");
        applyPost(value);
      }
    }
  } catch (cause) { if (active) error.value = cause instanceof Error ? cause.message : "帖子读取失败，请重试。"; }
  finally {
    if (active) { loading.value = false; persistLocal(); scheduleSave(); }
  }
}
async function saveDraft(): Promise<boolean> {
  if (savePromise) return savePromise;
  if (ownerId == null || loading.value || error.value || paused.value || !editable.value) return false;
  if (!dirty.value) return true;
  if (!hasContent.value && !post.value) return true;
  if (user.user?.id !== ownerId || user.user.role !== "hr") { paused.value = true; saveError.value = "登录身份已变化，请重新确认账号。"; return false; }
  const snapshot = content(); const snapshotFingerprint = postFingerprint(snapshot);
  saving.value = true; saveError.value = ""; persistLocal();
  savePromise = (async () => {
    try {
      const value = post.value
        ? await api.saveHrPostDraft(ownerId, post.value.id, post.value.version, snapshot)
        : await api.createHrPostDraft(ownerId, clientKey, snapshot);
      if (!active) return true;
      post.value = value; lastSavedAt.value = value.updatedAt;
      baseVersion.value = value.version;
      savedFingerprint.value = postFingerprint(value);
      // The idempotent create may return an earlier successful save after a lost response.
      if (!editableHrPost(value.status)) {
        paused.value = true; saveError.value = "帖子已提交或状态发生变化，本页内容已保留，请读取服务器最新版本。";
        return false;
      }
      if (postFingerprint(value) === snapshotFingerprint) {
        // Preserve text typed while this request was in flight.
        if (fingerprint.value === snapshotFingerprint) savedFingerprint.value = fingerprint.value;
      }
      persistLocal(); return true;
    } catch (cause) {
      if (active) {
        saveError.value = cause instanceof Error ? cause.message : "草稿暂未同步，请重试。";
        if (cause instanceof ApiError && [409, 401, 403, 404].includes(cause.status)) paused.value = true;
        persistLocal();
      }
      return false;
    } finally {
      saving.value = false; savePromise = null;
      // Retry only when the user made newer edits during a successful save.
      if (active && !saveError.value && dirty.value && !paused.value) scheduleSave();
    }
  })();
  return savePromise;
}
async function flushDraft() {
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; }
  if (savePromise && !await savePromise) return false;
  if (dirty.value && !await saveDraft()) return false;
  // A duplicate create can return the last acknowledged payload; synchronize the latest text once.
  if (dirty.value && !await saveDraft()) return false;
  return !dirty.value;
}
async function submit() {
  if (busy.value || loading.value || !editable.value) return;
  if (!title.value.trim() || !body.value.trim()) { toast.warning("提交前请填写标题和正文。"); return; }
  submitting.value = true;
  try {
    if (!await flushDraft() || !post.value || ownerId == null) return;
    const value = await api.submitHrPost(ownerId, post.value.id, post.value.version);
    if (!active) return;
    applyPost(value);
    persistLocal();
    if (value.status === "rejected") { toast.error("内容未通过审核，原文已保留，请修改后重新提交。"); return; }
    finished = true; removeLocalHrPost(ownerId, localKey, fingerprint.value);
    toast.success("帖子已提交，等待审核。暂不会对外公开。");
    await router.replace({ path: `/hr/posts/${value.id}`, query: { returnTo: returnTo.value } });
  } catch (cause) { saveError.value = cause instanceof Error ? cause.message : "提交失败，草稿已保留。"; if (cause instanceof ApiError && cause.status === 409) paused.value = true; }
  finally { submitting.value = false; }
}
function withdraw() {
  if (busy.value || !post.value || ownerId == null) return;
  dialog.warning({ title: "撤回帖子", content: "撤回后可以继续编辑，重新提交时会再次审核。确定撤回吗？", positiveText: "确定撤回", negativeText: "取消",
    onPositiveClick: async () => {
      if (!post.value || withdrawing.value) return false;
      withdrawing.value = true;
      try { applyPost(await api.withdrawHrPost(ownerId, post.value.id, post.value.version)); persistLocal(); toast.success("已撤回，可以继续编辑。"); return true; }
      catch (cause) { toast.error(cause instanceof Error ? cause.message : "撤回失败，请刷新后重试。"); return false; }
      finally { withdrawing.value = false; }
    },
  });
}
function reloadServer() {
  if (!post.value || ownerId == null || busy.value) return;
  dialog.info({ title: "读取服务器版本", content: "本页未同步内容会保存为本机备份，可在我的帖子中另存为新帖。", positiveText: "读取最新版本", negativeText: "继续保留本页",
    onPositiveClick: async () => {
      if (!backupLocalHrPost(ownerId, localSnapshot())) { toast.error("本机内容未能备份，请先复制保留原文。"); return false; }
      try { applyPost(await api.hrPost(ownerId, post.value!.id)); persistLocal(); return true; }
      catch (cause) { toast.error(cause instanceof Error ? cause.message : "读取失败，请重试。"); return false; }
    },
  });
}
function saveAsNew() {
  if (ownerId == null || busy.value) return;
  const key = crypto.randomUUID();
  const snapshot = { ...localSnapshot(), postId: null, baseVersion: null, clientKey: key, savedFingerprint: "" };
  if (!saveLocalHrPost(ownerId, key, snapshot, "")) { toast.error("本机备份失败，请先复制原文。"); return; }
  void router.push({ path: "/hr/posts/new", query: { draft: key, returnTo: returnTo.value } });
}
function leave() {
  if (typeof window.history.state?.back === "string" && window.history.state.back === returnTo.value) router.back();
  else void router.push(returnTo.value);
}
function beforeUnload(event: BeforeUnloadEvent) {
  persistLocal();
  if ((!localSaved.value && dirty.value) || submitting.value) { event.preventDefault(); event.returnValue = ""; }
}
window.addEventListener("beforeunload", beforeUnload);
onBeforeRouteLeave(() => {
  persistLocal();
  if (busy.value && !finished) { toast.info("正在处理帖子，请稍候。"); return false; }
  return localSaved.value || !dirty.value || window.confirm("本机草稿保存失败，离开会丢失未同步内容，确定离开吗？");
});
onBeforeUnmount(() => { persistLocal(); active = false; if (saveTimer) clearTimeout(saveTimer); window.removeEventListener("beforeunload", beforeUnload); });
onMounted(load);
</script>

<template>
  <section class="hr-post-page post-editor">
    <div class="post-breadcrumb"><button type="button" @click="leave">← 返回我的帖子</button><span>HR 原创分享</span></div>
    <header class="post-page-heading"><div><p class="post-eyebrow">把经验写下来</p><h1>{{ post ? editable ? '编辑分享' : '查看帖子' : '写一篇分享' }}</h1><p>分享面试经验、招聘观察与职业建议。</p></div><span v-if="post" class="post-status" :class="post.status">{{ hrPostLabels[post.status] }}</span></header>
    <p class="post-stage-note">当前提交后将等待审核，尚未接入公开阅读。保存草稿仅本人可见。</p>
    <p v-if="loading" class="post-empty">正在读取草稿…</p>
    <p v-if="error" class="post-error" role="alert">{{ error }} <button type="button" @click="load">重新读取</button></p>
    <template v-if="!loading && !error">
      <div class="post-save-state" role="status"><span v-if="!localSaved" class="post-error">本机备份失败，请先保留原文，暂勿关闭页面。</span><span v-else-if="saving">正在同步草稿…</span><span v-else-if="paused">本页内容已保留，请处理版本或账号变化。</span><span v-else-if="saveError">{{ localSaved ? '草稿已保存在本机，服务器同步未完成。' : '' }}</span><span v-else-if="dirty">{{ restored ? '已恢复本机未完成内容，' : '' }}正在等待自动同步…</span><span v-else-if="lastSavedAt">已保存到账号 · {{ lastSavedAt.replace('T', ' ').slice(0, 16) }}</span><span v-else>开始编辑后会自动保存草稿。</span><button v-if="editable && !paused" type="button" :disabled="saving || busy" @click="flushDraft">立即保存</button></div>
      <div v-if="saveError" class="post-error" role="alert"><p>{{ saveError }}</p><div class="post-recovery-actions"><button v-if="!paused" type="button" :disabled="busy || saving" @click="flushDraft">重试保存</button><button v-if="post" type="button" :disabled="busy || saving" @click="reloadServer">读取服务器版本</button><button type="button" :disabled="busy || saving" @click="saveAsNew">另存为新帖</button><button v-if="paused" type="button" @click="user.syncSession()">重新确认当前账号</button></div></div>
      <div class="post-editor-tabs"><button type="button" :class="{ active: !preview }" :aria-pressed="!preview" @click="preview = false">{{ editable ? '编辑内容' : '帖子内容' }}</button><button type="button" :class="{ active: preview }" :aria-pressed="preview" @click="preview = true">预览</button></div>
      <article v-if="preview || !editable" class="post-writing-card post-preview"><h2>{{ title || '未命名分享' }}</h2><div class="post-tag-row"><span v-for="tag in tags" :key="tag">{{ tag }}</span></div><p>{{ body || '正文将在这里展示。' }}</p></article>
      <fieldset v-else class="post-writing-card" :disabled="busy"><label class="post-field"><span>标题 <small>{{ title.length }}/200</small></span><input v-model="title" maxlength="200" placeholder="给你的经验分享起一个标题" /></label><label class="post-field"><span>正文 <small>{{ body.length }}/10000</small></span><textarea v-model="body" maxlength="10000" placeholder="写下你的观察、经历和建议，帮助学生更好地理解职场…" /></label><div class="post-field"><span>标签（最多 5 个）</span><TagPicker v-model="tags" :max="5" /></div></fieldset>
      <footer class="post-editor-footer"><span v-if="post?.status === 'pending_human'">帖子已提交，请等待后续审核。需要修改可先撤回。</span><span v-else-if="post?.status === 'removed'">帖子已被运营下架，可保留内容另存新帖后重新审核。</span><span v-else>请避免填写个人联系方式及未经授权的公司或学生信息。</span><div><button v-if="editable" type="button" :disabled="busy || saving || paused" @click="flushDraft">保存草稿</button><button v-if="editable" class="post-primary" type="button" :disabled="busy || paused || !title.trim() || !body.trim()" @click="submit">{{ submitting ? '提交中…' : '提交审核' }}</button><button v-if="post && ['pending_human', 'published'].includes(post.status)" type="button" :disabled="busy" @click="withdraw">{{ withdrawing ? '撤回中…' : '撤回并继续编辑' }}</button><button v-if="post?.status === 'removed'" type="button" @click="saveAsNew">另存为新帖</button></div></footer>
    </template>
  </section>
</template>
<style src="../styles/hrPosts.css"></style>
