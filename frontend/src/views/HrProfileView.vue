<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { api, type HrProfile, type HrContributionStats, type HrAnswerRecord, type HrAnswerKind, type HrAnswerStatus } from "@/api";
import { useUserStore } from "@/stores/user";
import { canWithdrawAnswer, useAnswerWithdrawal } from "@/composables/useAnswerWithdrawal";
import { answerStatusLabel, removedAnswerText } from "@/utils/answerRemoval";

defineOptions({ name: "HrProfileView" });
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const ownerId = userStore.user?.id;
const message = useMessage();
const profile = ref<HrProfile | null>(null);
const stats = ref<HrContributionStats | null>(null);
const profileError = ref("");
const statsError = ref("");
const listError = ref("");
const loading = ref(false);
const records = ref<HrAnswerRecord[]>([]);
const focusedRecord = ref<HrAnswerRecord | null>(null);
const focusedError = ref("");
const focusedLoading = ref(false);
const focusedMode = computed(() => typeof route.query.answer === "string");
const fromMessages = computed(() => route.query.from === "hr-messages");
const showAnswers = computed(() => focusedMode.value || route.query.tab === "answers" || route.hash === "#my-answers");
const visibleRecords = computed(() => focusedMode.value ? focusedRecord.value ? [focusedRecord.value] : [] : records.value);
let focusSequence = 0;
const kind = ref<HrAnswerKind>("all");
const status = ref<HrAnswerStatus>("all");
const cursors = ref<(number | null)[]>([null]);
const nextCursor = ref<number | null>(null);
const expandedIds = ref<number[]>([]);
const pageNumber = computed(() => cursors.value.length);
const displayName = computed(() => profile.value?.nickname ?? userStore.user?.nickname ?? "HR 答主");
const avatarUrl = computed(() => profile.value?.avatarUrl ?? userStore.user?.avatarUrl);
const verification = computed(() => ({ approved: "认证通过（演示）", verified: "认证通过（演示）", pending: "认证待完善", rejected: "认证未通过" }[profile.value?.verifyStatus ?? "pending"] ?? "认证待完善"));
const statCards = computed(() => [
  { label: "主回答", value: stats.value?.rootAnswerCount },
  { label: "追问回复", value: stats.value?.followupReplyCount },
  { label: "获得点赞", value: stats.value?.likeCount },
  { label: "被认为有用", value: stats.value?.usefulCount },
]);
const kindOptions: { value: HrAnswerKind; label: string }[] = [
  { value: "all", label: "全部内容" }, { value: "root", label: "主回答" }, { value: "reply", label: "追问回复" },
];
let listSequence = 0;
let activated = false;
let returnScroll: number | null = null;
const errorText = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback;

async function loadProfile() {
  profileError.value = "";
  try { profile.value = await api.hrProfile(); }
  catch (e) { profileError.value = errorText(e, "个人资料加载失败，请重试。"); }
}
async function loadStats() {
  statsError.value = "";
  try { stats.value = await api.hrStats(); }
  catch (e) { stats.value = null; statsError.value = errorText(e, "贡献统计加载失败，请重试。"); }
}
async function loadRecords(cursor: number | null): Promise<boolean> {
  const sequence = ++listSequence;
  loading.value = true;
  listError.value = "";
  try {
    let page = await api.hrAnswers(kind.value, status.value, cursor);
    if (sequence !== listSequence) return false;
    const reset = cursor !== null && page.items.length === 0;
    if (reset) {
      page = await api.hrAnswers(kind.value, status.value);
      if (sequence !== listSequence) return false;
      cursors.value = [null];
    }
    records.value = page.items;
    nextCursor.value = page.nextCursor;
    expandedIds.value = [];
    return !reset;
  } catch (e) {
    if (sequence === listSequence) listError.value = errorText(e, "回答记录加载失败，请重试。");
    return false;
  } finally {
    if (sequence === listSequence) loading.value = false;
  }
}
async function loadAll() {
  if (userStore.user?.id !== ownerId) return;
  await Promise.allSettled([loadProfile(), loadStats(), focusedMode.value ? loadFocusedRecord() : loadRecords(cursors.value.at(-1) ?? null)]);
}
async function loadFocusedRecord() {
  const current = ++focusSequence;
  focusedLoading.value = true;
  focusedError.value = "";
  focusedRecord.value = null;
  try {
    const id = Number(route.query.answer);
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error("这条回答记录暂时无法查看。");
    const record = await api.hrAnswer(id);
    if (current === focusSequence && userStore.user?.id === ownerId && Number(route.query.answer) === id) focusedRecord.value = record;
  } catch (cause) {
    if (current === focusSequence) focusedError.value = errorText(cause, "这条回答记录暂时无法查看。");
  } finally { if (current === focusSequence) focusedLoading.value = false; }
}
watch(() => route.query.answer, () => {
  if (route.name !== "hr-profile" || userStore.user?.id !== ownerId) return;
  if (focusedMode.value) void loadFocusedRecord();
  else { ++focusSequence; focusedRecord.value = null; focusedError.value = ""; void loadRecords(cursors.value.at(-1) ?? null); }
});
function returnToMessages() {
  if (typeof window.history.state?.back === "string" && window.history.state.back.startsWith("/hr/messages")) router.back();
  else void router.push("/hr/messages");
}
function selectSection(section: "profile" | "answers") {
  if (showAnswers.value === (section === "answers")) return;
  void router.push({ name: "hr-profile", query: section === "answers" ? { tab: "answers" } : {} });
}
function showAllRecords() {
  void router.replace({ name: "hr-profile", query: { tab: "answers", from: fromMessages.value ? "hr-messages" : undefined } });
}
async function nextPage() {
  if (loading.value || nextCursor.value == null) return;
  const cursor = nextCursor.value;
  if (await loadRecords(cursor)) cursors.value.push(cursor);
}
async function previousPage() {
  if (loading.value || pageNumber.value <= 1) return;
  if (await loadRecords(cursors.value.at(-2) ?? null)) cursors.value.pop();
}
watch([kind, status], () => {
  cursors.value = [null];
  nextCursor.value = null;
  records.value = [];
  void loadRecords(null);
});
const { requestWithdrawal, withdrawingAnswerId } = useAnswerWithdrawal(async () => {
  await Promise.all([loadStats(), focusedMode.value ? loadFocusedRecord() : loadRecords(cursors.value.at(-1) ?? null)]);
  if (statsError.value || listError.value || focusedError.value) throw new Error("撤销成功，最新记录或统计暂未读取成功");
});
function openContext(record: HrAnswerRecord) {
  if (!record.canViewQuestion) return;
  returnScroll = window.scrollY;
  void router.push({ path: `/questions/${record.questionId}`, query: { from: "hr-profile", answer: String(record.id) } });
}
function toggleExpanded(id: number) {
  expandedIds.value = expandedIds.value.includes(id) ? expandedIds.value.filter(value => value !== id) : [...expandedIds.value, id];
}
function formatTime(time: string) { return time.replace("T", " ").slice(0, 16); }
onMounted(loadAll);
onActivated(async () => {
  if (!activated) { activated = true; return; }
  await loadAll();
  if (returnScroll !== null) {
    await nextTick();
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
    window.scrollTo(0, returnScroll);
    returnScroll = null;
  }
});

const editing = ref(false);
const saving = ref(false);
const saveError = ref("");
const draft = reactive({ nickname: "", companyDisplay: "", industry: "", position: "", bio: "", specialtiesText: "" });
function editProfile() {
  if (!profile.value) return;
  const p = profile.value;
  Object.assign(draft, { nickname: p.nickname, companyDisplay: p.companyDisplay, industry: p.industry ?? "", position: p.position ?? "", bio: p.bio ?? "", specialtiesText: p.specialties.join("、") });
  saveError.value = "";
  editing.value = true;
}
async function saveProfile() {
  if (saving.value) return;
  saveError.value = "";
  const specialties = [...new Set(draft.specialtiesText.split(/[,，、]/).map(value => value.trim()).filter(Boolean))];
  if (!draft.nickname.trim()) { saveError.value = "请填写公开昵称。"; return; }
  if (specialties.length > 6 || specialties.some(value => value.length > 24)) {
    saveError.value = "擅长领域最多 6 项，每项最多 24 字。"; return;
  }
  saving.value = true;
  try {
    profile.value = await api.patchHrProfile({ nickname: draft.nickname.trim(), companyDisplay: draft.companyDisplay.trim(), industry: draft.industry.trim(), position: draft.position.trim(), bio: draft.bio.trim(), specialties });
    await userStore.fetchUserInfo(true);
    editing.value = false;
    message.success("个人资料已保存");
  } catch (e) { saveError.value = errorText(e, "保存失败，填写内容已保留。"); }
  finally { saving.value = false; }
}
const avatarInput = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file || uploading.value) return;
  if (!/^image\/(png|jpeg|webp)$/.test(file.type) || file.size > 2 * 1024 * 1024) {
    message.warning("请选择 2MB 以内的 PNG、JPEG 或 WEBP 图片。"); return;
  }
  uploading.value = true;
  try {
    const result = await api.uploadAvatar(file);
    if (profile.value) profile.value.avatarUrl = result.avatarUrl;
    await userStore.fetchUserInfo(true);
    message.success("头像已更新");
  } catch (e) { message.error(errorText(e, "头像上传失败，请重试。")); }
  finally { uploading.value = false; }
}
</script>

<template>
  <section class="hr-personal">
    <header class="page-intro">
      <button v-if="fromMessages" type="button" class="return-messages" @click="returnToMessages">< 返回消息中心</button>
      <h1>个人中心</h1>
    </header>

    <div class="profile-sections" role="group" aria-label="个人中心栏目">
      <button type="button" :class="{ active: !showAnswers }" :aria-pressed="!showAnswers" @click="selectSection('profile')">
        个人资料
      </button>
      <button type="button" :class="{ active: showAnswers }" :aria-pressed="showAnswers" @click="selectSection('answers')">
        回答记录
      </button>
    </div>

    <section v-if="!showAnswers" class="career-card" aria-label="个人资料">
      <div class="identity-card">
        <button class="profile-avatar" type="button" :disabled="uploading" aria-label="更换头像" @click="avatarInput?.click()">
          <img v-if="avatarUrl" :src="avatarUrl" alt="" />
          <span v-else>{{ Array.from(displayName.trim() || 'H')[0] }}</span>
          <span class="avatar-edit">{{ uploading ? '上传中' : '更换头像' }}</span>
        </button>
        <input ref="avatarInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="uploadAvatar" />
        <h2>{{ displayName }}</h2>
        <button type="button" class="edit-profile" :disabled="!profile" @click="editProfile">编辑资料</button>
        <router-link v-if="profile" :to="{ name: 'public-profile', params: { id: profile.id } }" class="public-author-link">查看我的公开主页 ></router-link>
      </div>
      <p v-if="profileError" class="inline-error" role="alert">{{ profileError }} <button type="button" @click="loadProfile">重试</button></p>
      <p v-else-if="!profile" class="profile-loading" role="status">正在读取职业资料…</p>
      <dl v-else class="career-fields">
        <div><dt>所属行业</dt><dd>{{ profile.industry || '待完善' }}</dd></div>
        <div><dt>职业方向</dt><dd>{{ profile.position || '待完善' }}</dd></div>
        <div><dt>企业展示名</dt><dd>{{ profile.companyDisplay || '待完善' }}</dd></div>
        <div><dt>身份状态</dt><dd>{{ verification }}</dd></div>
        <div class="career-field-wide">
          <dt>擅长领域</dt>
          <dd v-if="profile.specialties.length" class="specialties">
            <span v-for="tag in profile.specialties" :key="tag">{{ tag }}</span>
          </dd>
          <dd v-else>待完善</dd>
        </div>
        <div class="career-field-wide">
          <dt>个人简介</dt>
          <dd class="profile-bio">{{ profile.bio || '待完善' }}</dd>
        </div>
      </dl>
    </section>

    <section v-else id="my-answers" class="answer-records" aria-label="回答记录">
      <template v-if="!focusedMode">
        <div class="contribution-stats" aria-label="我的贡献统计">
          <div v-for="stat in statCards" :key="stat.label" class="stat-card"><strong>{{ stat.value ?? '—' }}</strong><span>{{ stat.label }}</span></div>
        </div>
        <p class="stats-caption">统计当前公开可见、已发布的回答与回复。</p>
        <p v-if="statsError" class="inline-error" role="alert">{{ statsError }} <button type="button" @click="loadStats">重试</button></p>
      </template>
      <div v-if="focusedMode" class="focused-record-banner"><span>通知关联的回答</span><button type="button" @click="showAllRecords">查看全部记录</button></div>
      <p v-if="focusedError" class="inline-error" role="alert">{{ focusedError }} <button type="button" @click="loadFocusedRecord">重试</button></p>
      <div v-if="!focusedMode" class="record-filters">
        <div class="record-tabs" role="group" aria-label="回答类型"><button v-for="option in kindOptions" :key="option.value" type="button" :class="{ active: kind === option.value }" :aria-pressed="kind === option.value" @click="kind = option.value">{{ option.label }}</button></div>
        <label class="status-filter"><span class="sr-only">回答状态</span><select v-model="status" aria-label="回答状态"><option value="all">全部状态</option><option value="published">已发布</option><option value="reviewing">审核中</option><option value="rejected">未通过</option><option value="withdrawn">作者已撤销</option><option value="downlisted">运营已下架</option><option value="removed">全部已移除</option></select></label>
      </div>
      <p v-if="!focusedMode && listError" class="inline-error" role="alert">{{ listError }} <button type="button" @click="loadRecords(cursors.at(-1) ?? null)">重试</button></p>
      <div class="record-list" :aria-busy="focusedMode ? focusedLoading : loading">
        <div v-if="focusedMode ? focusedLoading : loading && !records.length" class="records-empty">正在读取你的回答…</div>
        <div v-else-if="!focusedMode && !records.length && !listError" class="records-empty">{{ kind === 'all' && status === 'all' ? '暂无回答记录' : '没有符合筛选条件的回答' }}</div>
        <article v-for="record in visibleRecords" :id="`my-answer-${record.id}`" :key="record.id" class="answer-record">
          <div class="record-meta"><span class="record-kind">{{ record.followupId == null ? '主回答' : '追问回复' }}</span><span class="record-status" :class="`state-${record.status}`">{{ answerStatusLabel(record) }}</span><time>{{ formatTime(record.createdAt) }}</time></div>
          <h3>{{ record.questionTitle }}</h3>
          <p v-if="record.status === 'removed'" class="removed-copy">{{ removedAnswerText(record, record.followupId == null ? '这条回答' : '这条回复') }}</p>
          <p v-else class="record-body" :class="{ collapsed: record.body.length > 180 && !expandedIds.includes(record.id) }">{{ record.body }}</p>
          <button v-if="record.status !== 'removed' && record.body.length > 180" class="expand-answer" type="button" @click="toggleExpanded(record.id)">{{ expandedIds.includes(record.id) ? '收起' : '展开内容' }}</button>
          <div class="record-footer"><div class="record-feedback"><template v-if="record.status === 'published' && record.canViewQuestion"><span>{{ record.likeCount }} 点赞</span><span>{{ record.usefulCount }} 有用</span></template><span v-else-if="record.status === 'pending_human' || record.status === 'pending_machine'">审核通过后将公开展示</span><span v-else-if="record.status === 'rejected'">内容未通过审核，可重新组织后提交</span><span v-else>记录仅供本人查看</span></div><div class="record-actions"><button v-if="record.canViewQuestion" type="button" @click="openContext(record)">查看上下文</button><button v-if="canWithdrawAnswer(record)" class="withdraw-record" type="button" :disabled="withdrawingAnswerId !== null" @click="requestWithdrawal(record)">{{ withdrawingAnswerId === record.id ? '撤销中…' : '撤销' }}</button></div></div>
        </article>
      </div>
      <div v-if="!focusedMode && (records.length || pageNumber > 1)" class="record-pagination"><button type="button" :disabled="loading || pageNumber <= 1" @click="previousPage">上一页</button><span>第 {{ pageNumber }} 页</span><button type="button" :disabled="loading || nextCursor == null" @click="nextPage">下一页</button></div>
      <p v-if="!focusedMode && !loading && !listError && records.length && nextCursor == null" class="end-caption">回答记录已经看完咯</p>
    </section>
    <n-modal v-model:show="editing" preset="card" title="编辑 HR 资料" :mask-closable="!saving" :closable="!saving" :close-on-esc="!saving" style="width: 560px; max-width: calc(100vw - 32px)">
      <form class="hr-profile-form" @submit.prevent="saveProfile"><p class="form-note">使用公开昵称和职业信息。企业展示名可以填写概括称呼。</p><label>公开昵称<input v-model="draft.nickname" maxlength="64" required :disabled="saving" /></label><div class="form-two-cols"><label>所属行业<input v-model="draft.industry" maxlength="64" placeholder="例如：互联网" :disabled="saving" /></label><label>职业方向<input v-model="draft.position" maxlength="64" placeholder="例如：校园招聘" :disabled="saving" /></label></div><label>企业展示名<input v-model="draft.companyDisplay" maxlength="128" placeholder="例如：某互联网企业" :disabled="saving" /></label><label>擅长领域<input v-model="draft.specialtiesText" maxlength="160" placeholder="简历优化、面试准备、校园招聘" :disabled="saving" /><small>用顿号或逗号分隔，最多 6 项。</small></label><label>个人简介<textarea v-model="draft.bio" maxlength="500" rows="4" placeholder="介绍你熟悉的领域，以及可以提供的经验。" :disabled="saving" /><small>{{ draft.bio.length }} / 500</small></label><p v-if="saveError" class="inline-error" role="alert">{{ saveError }}</p><div class="form-actions"><button type="button" :disabled="saving" @click="editing = false">取消</button><button type="submit" class="save-profile" :disabled="saving">{{ saving ? '保存中…' : '保存资料' }}</button></div></form>
    </n-modal>
  </section>
</template>

<style scoped>
.hr-personal { max-width: 900px; margin: 0 auto; padding: 30px 28px 64px; color: var(--text); }
.return-messages { padding: 0; border: 0; background: none; font: inherit; color: var(--color-brand); cursor: pointer; }
.focused-record-banner { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; padding: 13px 15px; margin-bottom: 15px; background: var(--color-brand-soft); border-radius: 7px; color: var(--color-brand-dark); font-size: 12px; }
.focused-record-banner button { border: 0; padding: 0; background: none; color: var(--color-brand); cursor: pointer; font-size: 12px; }
.page-intro { margin-bottom: 18px; font-size: 13px; }
.page-intro h1 { margin: 0; font-size: clamp(23px, 4vw, 30px); line-height: 1.35; }
.page-intro .return-messages { margin-bottom: 10px; }

.profile-sections {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  margin-bottom: 16px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--color-bg-feed);
}

.profile-sections button {
  min-height: 40px;
  border: 1px solid transparent;
  border-radius: 7px;
  padding: 7px 16px;
  background: none;
  color: var(--color-text-regular);
  font-size: 14px;
  font-weight: 600;
}

.profile-sections button:hover { background: var(--color-bg-hover); }
.profile-sections button.active {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand-dark);
}

.career-card {
  padding: 24px;
  background: var(--color-bg-surface);
  border: 1px solid var(--border);
  border-radius: 14px;
}

.identity-card { display: flex; align-items: center; gap: 16px; }
.identity-card h2 { flex: 1; min-width: 0; margin: 0; font-size: 24px; line-height: 1.4; overflow-wrap: anywhere; }
.profile-avatar { position: relative; display: grid; place-items: center; width: 70px; height: 70px; flex: 0 0 70px; padding: 0; overflow: hidden; border: 0; border-radius: 50%; background: var(--color-brand); color: #fff; font-size: 28px; font-weight: 700; cursor: pointer; }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-avatar .avatar-edit { position: absolute; bottom: 0; left: 0; width: 100%; padding: 3px 0 5px; font-size: 10px; font-weight: 400; background: #163d8b99; }
.edit-profile { padding: 9px 13px; border: 1px solid var(--color-brand-border); border-radius: 7px; background: var(--color-brand-soft); color: var(--color-brand-dark); white-space: nowrap; font-size: 13px; cursor: pointer; }
.edit-profile:hover:not(:disabled) { border-color: var(--color-brand); }

.career-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin: 20px 0 0;
  border-top: 1px solid var(--color-border-subtle);
  padding-top: 20px;
}

.career-fields dt { color: var(--color-text-muted); font-size: 13px; }
.career-fields dd { margin: 5px 0 0; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.career-field-wide { grid-column: 1 / -1; }
.profile-bio { white-space: pre-wrap; }
.profile-loading { margin: 18px 0 0; color: var(--color-text-muted); font-size: 13px; }
.specialties { display: flex; flex-wrap: wrap; gap: 6px; }
.specialties span { padding: 3px 8px; border-radius: 5px; background: var(--color-brand-soft); color: var(--color-brand-dark); font-size: 12px; }

.contribution-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--border); border-radius: 12px; background: var(--color-bg-surface); padding: 21px 0; }
.stat-card { display: grid; gap: 7px; text-align: center; border-right: 1px solid var(--color-bg-hover); }.stat-card:last-child { border: 0; }
.stat-card strong { font-size: 25px; font-weight: 650; font-variant-numeric: tabular-nums; }.stat-card span { color: #788397; font-size: 12px; }
.stats-caption { margin: 8px 2px 25px; font-size: 11px; color: #8490a2; }
.answer-records { min-width: 0; scroll-margin-top: calc(var(--topbar-height, 57px) + 12px); }
.record-filters { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 15px; }
.record-tabs { display: flex; padding: 4px; gap: 2px; background: var(--color-bg-hover); border-radius: 7px; }.record-tabs button { padding: 7px 12px; border: 0; background: transparent; color: #717d91; font-size: 12px; cursor: pointer; white-space: nowrap; }.record-tabs button.active { background: var(--color-bg-surface); color: var(--color-brand); border-radius: 5px; box-shadow: 0 1px 3px #2439580d; }
.status-filter select { padding: 8px 26px 8px 10px; max-width: 135px; border: 1px solid var(--color-border-default); border-radius: 6px; background: var(--color-bg-surface); font: inherit; font-size: 12px; color: #657187; }
.record-list { display: flex; flex-direction: column; gap: 13px; }.answer-record { padding: 20px 22px; border: 1px solid var(--color-border-default); border-radius: 10px; background: var(--color-bg-surface); scroll-margin-top: 90px; }.record-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; font-size: 11px; }.record-kind { color: #52617a; }.record-status { padding: 3px 7px; background: #f0f2f6; border-radius: 4px; color: #7a8595; }.state-published { background: #edf7f0; color: #4d8763; }.state-pending_human,.state-pending_machine { background: #fff7e6; color: #a77b2e; }.state-rejected { background: #fff0ef; color: #ba625b; }.record-meta time { margin-left: auto; color: #929bad; }
.answer-record h3 { margin: 13px 0 10px; font-size: 15px; line-height: 1.6; font-weight: 600; overflow-wrap: anywhere; }
.record-body { margin: 0; color: #59667a; font-size: 13px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }.record-body.collapsed { display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }.removed-copy { color: #8590a2; font-size: 13px; }
.expand-answer { margin-top: 7px; padding: 0; border: 0; background: none; color: var(--color-brand); font-size: 12px; cursor: pointer; }.record-footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-top: 18px; padding-top: 13px; border-top: 1px solid #f0f2f6; }.record-feedback { display: flex; flex-wrap: wrap; gap: 14px; color: var(--color-text-muted); font-size: 11px; }.record-actions { display: flex; gap: 14px; }.record-actions button { padding: 0; border: 0; background: none; color: var(--color-brand); font-size: 12px; cursor: pointer; }.record-actions .withdraw-record { color: #9c6c6c; }
.record-pagination { display: flex; justify-content: center; align-items: center; gap: 17px; margin-top: 25px; font-size: 12px; color: #748096; }.record-pagination button { padding: 7px 13px; border: 1px solid var(--color-border-default); border-radius: 5px; background: var(--color-bg-surface); color: #52617a; cursor: pointer; }.hr-personal button:disabled { opacity: .5; cursor: default; }.end-caption { margin: 12px 0 0; text-align: center; font-size: 11px; color: #9aa3b1; }
.records-empty { padding: 42px 15px; text-align: center; background: var(--color-bg-surface); border: 1px solid var(--color-border-default); border-radius: 10px; color: var(--color-text-muted); font-size: 14px; }
.inline-error { font-size: 12px; color: #b35c57; line-height: 1.8; }.inline-error button { padding: 0; background: none; border: 0; color: var(--color-brand); cursor: pointer; }
.hr-profile-form { display: flex; flex-direction: column; gap: 16px; }.hr-profile-form label { display: flex; flex-direction: column; gap: 8px; color: #536078; font-size: 13px; }.hr-profile-form input,.hr-profile-form textarea { box-sizing: border-box; width: 100%; padding: 10px 12px; border: 1px solid var(--color-border-default); border-radius: 6px; color: #344259; font: inherit; font-size: 13px; }.hr-profile-form input:focus,.hr-profile-form textarea:focus { outline: 2px solid var(--color-brand-border); border-color: var(--color-brand); }.hr-profile-form textarea { resize: vertical; }.hr-profile-form small { font-size: 11px; color: var(--color-text-muted); }.form-note { margin: 0; color: #8c97a7; font-size: 12px; line-height: 1.8; }.form-two-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }.form-actions { display: flex; justify-content: flex-end; gap: 10px; }.form-actions button { padding: 9px 19px; border: 1px solid var(--color-border-default); border-radius: 6px; background: var(--color-bg-surface); color: #5c697e; cursor: pointer; }.form-actions .save-profile { color: #fff; background: var(--color-brand); border-color: var(--color-brand); }
@media (max-width: 520px) {
  .hr-personal { padding: 20px 12px 40px; }
  .career-card { padding: 16px; }
  .career-fields { gap: 16px; }
  .identity-card { gap: 12px; }
  .identity-card h2 { font-size: 20px; }
  .profile-avatar { width: 56px; height: 56px; flex-basis: 56px; font-size: 23px; }
  .profile-avatar .avatar-edit { padding-bottom: 3px; }
  .edit-profile { padding-inline: 8px; }
  .record-filters { align-items: stretch; flex-direction: column; }
  .status-filter select { width: 100%; max-width: none; }
  .record-tabs button { flex: 1; }
  .answer-record { padding: 17px; }
  .record-footer { align-items: flex-start; }
  .record-meta time { width: 100%; margin: 0; }
  .stat-card strong { font-size: 22px; }
  .stat-card span { font-size: 11px; }
  .form-two-cols { grid-template-columns: minmax(0, 1fr); }
}
</style>
