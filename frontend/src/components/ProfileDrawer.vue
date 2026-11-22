<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { api, type MeStats, type Profile, type User, type HrProfile, type HrContributionStats } from "@/api";

const props = defineProps<{
  show: boolean;
  user: User | null;
}>();

const emit = defineEmits<{
  close: [];
  logout: [];
}>();

const router = useRouter();
const profile = ref<Profile | null>(null);
const stats = ref<MeStats | null>(null);
const loading = ref(false);
const isHr = computed(() => props.user?.role === "hr");
const hrProfile = ref<HrProfile | null>(null);
const hrStats = ref<HrContributionStats | null>(null);
const identityProfile = computed(() => isHr.value ? hrProfile.value : profile.value);
let loadSequence = 0;

const ROLE_LABEL: Record<string, string> = {
  student: "学生",
  hr: "HR",
  operator: "运营",
  admin: "管理员",
};

const nickname = computed(() => identityProfile.value?.nickname ?? props.user?.nickname ?? "同学");
const roleLabel = computed(() => ROLE_LABEL[props.user?.role ?? "student"] ?? "学生");
const avatarUrl = computed(() => identityProfile.value?.avatarUrl ?? props.user?.avatarUrl ?? "");
const avatarInitial = computed(() => (nickname.value || "学").trim().slice(0, 1));

/** 学校 / 专业 / 年级，没填的项不展示 */
const profileLines = computed(() => {
  if (isHr.value) return [hrProfile.value?.industry, hrProfile.value?.position].filter((value): value is string => !!value?.trim());
  const values = [profile.value?.school, profile.value?.major, profile.value?.grade];
  return values.filter((value): value is string => !!value && value.trim().length > 0);
});

const statItems = computed(() => isHr.value ? [
  { label: "主回答", value: hrStats.value?.rootAnswerCount ?? "—" },
  { label: "追问回复", value: hrStats.value?.followupReplyCount ?? "—" },
  { label: "获赞", value: hrStats.value?.likeCount ?? "—" },
  { label: "有用", value: hrStats.value?.usefulCount ?? "—" },
] : [
  { label: "提问", value: stats.value?.questionCount ?? 0 },
  { label: "收到回答", value: stats.value?.receivedAnswerCount ?? 0 },
  { label: "获赞", value: stats.value?.likeCount ?? 0 },
]);

async function load() {
  const sequence = ++loadSequence;
  loading.value = true;
  if (isHr.value) {
    const results = await Promise.allSettled([api.hrProfile(), api.hrStats()]);
    if (sequence !== loadSequence) return;
    hrProfile.value = results[0].status === "fulfilled" ? results[0].value : null;
    hrStats.value = results[1].status === "fulfilled" ? results[1].value : null;
    loading.value = false;
    return;
  }
  try {
    const result = await api.getProfile();
    if (sequence !== loadSequence) return;
    profile.value = result;
  } catch {
    // 忽略：用户信息仍可从 store 兜底
  }
  try {
    const result = await api.myStats();
    if (sequence !== loadSequence) return;
    stats.value = result;
  } catch {
    // 忽略：统计不可用时显示 0
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

watch(
  () => [props.show, props.user?.id] as const,
  ([open], previous) => {
    if (previous?.[1] !== props.user?.id) {
      ++loadSequence;
      profile.value = null; stats.value = null; hrProfile.value = null; hrStats.value = null;
    }
    if (open) {
      void load();
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
  },
);

function go(path: string) {
  emit("close");
  router.push(path);
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="show" class="profile-drawer-mask" @click.self="emit('close')">
        <aside class="profile-drawer" role="dialog" aria-modal="true" aria-label="个人信息">
          <button class="drawer-close" type="button" aria-label="关闭" @click="emit('close')">×</button>

          <!-- 头像 / 昵称 / 身份 -->
          <div class="drawer-head">
            <span class="drawer-avatar">
              <img v-if="avatarUrl" :src="avatarUrl" alt="" />
              <template v-else>{{ avatarInitial }}</template>
            </span>
            <h2 class="drawer-name">{{ nickname }}</h2>
            <span class="drawer-role">{{ roleLabel }}</span>
            <p v-if="profileLines.length" class="drawer-meta">{{ profileLines.join(" · ") }}</p>
            <p v-else class="drawer-meta drawer-meta--muted">{{ isHr ? '职业资料待完善' : '学校 / 专业 / 年级待完善' }}</p>
          </div>

          <!-- 数据统计 -->
          <div class="drawer-stats" :class="{ 'drawer-stats--hr': isHr }">
            <div v-for="item in statItems" :key="item.label" class="drawer-stat">
              <b>{{ item.value }}</b>
              <span>{{ item.label }}</span>
            </div>
          </div>

          <!-- 快捷入口 -->
          <nav class="drawer-menu">
            <template v-if="isHr">
              <button type="button" class="drawer-menu-item" @click="go('/hr/profile')"><span>个人中心</span><span class="drawer-arrow">›</span></button>
              <button type="button" class="drawer-menu-item" @click="go('/hr/profile#my-answers')"><span>我的回答</span><span class="drawer-arrow">›</span></button>
              <button type="button" class="drawer-menu-item" @click="go('/hr/posts')"><span>我的帖子</span><span class="drawer-arrow">›</span></button>
              <button type="button" class="drawer-menu-item" @click="go('/hr')"><span>HR 工作台</span><span class="drawer-arrow">›</span></button>
            </template>
            <template v-else>
            <button type="button" class="drawer-menu-item" @click="go('/me')">
              <span>我的问答</span>
              <span class="drawer-arrow">›</span>
            </button>
            <button type="button" class="drawer-menu-item" @click="go('/me?tab=history')">
              <span>历史浏览</span>
              <span class="drawer-arrow">›</span>
            </button>
            <button v-if="user?.role === 'student'" type="button" class="drawer-menu-item" @click="go('/me?tab=favorites')"><span>我的收藏</span><span class="drawer-arrow">›</span></button>
            <button v-if="user?.role === 'student'" type="button" class="drawer-menu-item" @click="go('/messages')"><span>消息中心</span><span class="drawer-arrow">›</span></button>
            <button type="button" class="drawer-menu-item" @click="go('/me')">
              <span>个人资料</span>
              <span class="drawer-arrow">›</span>
            </button>
            </template>
          </nav>

          <button class="drawer-logout" type="button" @click="emit('logout')">退出登录</button>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.profile-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  justify-content: flex-end;
  background: rgba(17, 24, 39, 0.45);
}

.profile-drawer {
  position: relative;
  width: 340px;
  max-width: 88vw;
  height: 100%;
  padding: 28px 24px 24px;
  background: var(--color-bg-surface);
  box-shadow: -12px 0 32px rgba(17, 24, 39, 0.12);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.drawer-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: var(--color-text-muted);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.drawer-close:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.drawer-head {
  padding-top: 12px;
  text-align: center;
}

.drawer-avatar {
  width: 76px;
  height: 76px;
  margin: 0 auto;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-brand);
  color: #fff;
  font-size: 28px;
  font-weight: 700;
}

.drawer-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.drawer-name {
  margin: 12px 0 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.drawer-role {
  display: inline-flex;
  align-items: center;
  height: 22px;
  margin-top: 6px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eef4ff;
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 600;
}

.drawer-meta {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--color-text-regular);
}

.drawer-meta--muted {
  color: var(--color-text-muted);
}

.drawer-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 22px;
  padding: 14px 0;
  border-top: 1px solid var(--color-border-subtle);
  border-bottom: 1px solid var(--color-border-subtle);
}

.drawer-stat {
  text-align: center;
}

.drawer-stats--hr { grid-template-columns: repeat(2, 1fr); row-gap: 15px; }

.drawer-stat b {
  display: block;
  font-size: 20px;
  line-height: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.drawer-stat span {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  line-height: 18px;
  color: var(--color-text-muted);
}

.drawer-menu {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
}

.drawer-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 48px;
  padding: 0 4px;
  border: 0;
  border-bottom: 1px solid var(--color-border-subtle);
  background: none;
  color: var(--color-text-regular);
  font-family: inherit;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}

.drawer-menu-item:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-hover);
}

.drawer-arrow {
  color: var(--color-text-muted);
  font-size: 18px;
  line-height: 1;
}

.drawer-logout {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  margin-top: 24px;
  padding: 0;
  border: 1px solid var(--color-border-default);
  border-radius: 8px;
  background: none;
  color: var(--color-danger);
  font-family: inherit;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.drawer-logout:hover {
  background: var(--color-bg-hover);
}

/* 右侧滑入动效 */
.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.2s ease;
}

.drawer-fade-enter-active .profile-drawer,
.drawer-fade-leave-active .profile-drawer {
  transition: transform 0.22s ease;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-fade-enter-from .profile-drawer,
.drawer-fade-leave-to .profile-drawer {
  transform: translateX(100%);
}
</style>
