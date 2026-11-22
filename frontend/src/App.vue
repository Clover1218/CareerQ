<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { dateZhCN, zhCN, type GlobalThemeOverrides } from "naive-ui";
import { useUserStore } from "@/stores/user";
import MessageMenu from "@/components/MessageMenu.vue";
import ProfileDrawer from "@/components/ProfileDrawer.vue";
import { SESSION_SIGNAL_KEY, SESSION_REFRESH_EVENT } from "@/utils/sessionSync";

// Naive UI 主题与 Figma token 对齐，避免组件库默认蓝与设计稿脱节
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: "#2F6FED",
    primaryColorHover: "#1D4FC4",
    primaryColorPressed: "#1D4FC4",
    borderRadius: "6px",
    fontFamily: '"MiSans", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
    textColorBase: "#111827",
    borderColor: "#E3E8EF",
    bodyColor: "#F5F7FA",
  },
};

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const { user } = storeToRefs(userStore);
const topbar = ref<HTMLElement | null>(null);
let topbarObserver: ResizeObserver | undefined;

function syncTopbarHeight() {
  if (!topbar.value) return;
  document.documentElement.style.setProperty("--topbar-height", `${topbar.value.getBoundingClientRect().height}px`);
}

// 顶栏账号：角色文案、头像兜底首字、点击进入学生主页（/me）
const ROLE_LABEL: Record<string, string> = {
  student: "学生",
  hr: "HR",
  operator: "运营",
  admin: "管理员",
};
const roleLabel = computed(() => ROLE_LABEL[user.value?.role ?? "student"] ?? "学生");
const avatarInitial = computed(() => (user.value?.nickname ?? "学").trim().slice(0, 1));
// 悬停头像时展示的简要资料（学校 · 专业 · 年级）
const profileSummary = computed(() =>
  user.value?.role === "hr" ? "职业资料 · 我的回答" :
  [user.value?.school, user.value?.major, user.value?.grade]
    .filter((value): value is string => !!value && value.trim().length > 0)
    .join(" · "),
);

function goProfile() {
  if (user.value) {
    router.push(user.value.role === "hr" ? "/hr/profile" : "/me");
  }
}

async function refresh() {
  await userStore.fetchUserInfo(user.value?.role === "student");
}

function syncStudentSession() {
  if (document.visibilityState === "visible" && (!user.value || user.value.role === "student")) {
    void userStore.syncSession();
  }
}
function onSessionStorage(event: StorageEvent) {
  if (event.key === SESSION_SIGNAL_KEY) syncStudentSession();
}

async function logout() {
  await userStore.logout();
  router.push("/login");
}

// 退出登录：全屏居中的确认弹窗
const showLogoutConfirm = ref(false);
// 个人信息抽屉：点击顶栏头像打开
const showProfileDrawer = ref(false);

function openProfileDrawer() {
  showProfileDrawer.value = true;
}

function closeProfileDrawer() {
  showProfileDrawer.value = false;
}

function logoutFromDrawer() {
  showProfileDrawer.value = false;
  confirmLogout();
}

function confirmLogout() {
  showLogoutConfirm.value = true;
}

function closeLogoutConfirm() {
  showLogoutConfirm.value = false;
}

async function doLogout() {
  showLogoutConfirm.value = false;
  await logout();
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && showLogoutConfirm.value) {
    closeLogoutConfirm();
  }
}

watch(showLogoutConfirm, (open) => {
  document.body.style.overflow = open ? "hidden" : "";
});

onMounted(() => {
  syncTopbarHeight();
  topbarObserver = new ResizeObserver(syncTopbarHeight);
  if (topbar.value) topbarObserver.observe(topbar.value);
  window.addEventListener("keydown", onKeydown);
  window.addEventListener("focus", syncStudentSession);
  window.addEventListener("storage", onSessionStorage);
  window.addEventListener(SESSION_REFRESH_EVENT, syncStudentSession);
  document.addEventListener("visibilitychange", syncStudentSession);
});

onUnmounted(() => {
  topbarObserver?.disconnect();
  document.documentElement.style.removeProperty("--topbar-height");
  window.removeEventListener("keydown", onKeydown);
  window.removeEventListener("focus", syncStudentSession);
  window.removeEventListener("storage", onSessionStorage);
  window.removeEventListener(SESSION_REFRESH_EVENT, syncStudentSession);
  document.removeEventListener("visibilitychange", syncStudentSession);
  document.body.style.overflow = "";
});

onMounted(refresh);
watch(() => route.fullPath, refresh);
</script>

<template>
  <!-- 全局 Provider：naive-ui 的 message/dialog/notification/loadingBar 依赖这些宿主组件 -->
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <n-loading-bar-provider>
            <header ref="topbar" class="topbar">
              <div class="brand" @click="router.push('/')">CareerQ</div>
              <nav class="nav">
                <router-link to="/">公开问答</router-link>
                <router-link v-if="user?.role === 'student'" to="/me">我的问答</router-link>
                <router-link v-if="user?.role === 'hr'" to="/hr">HR 工作台</router-link>
                <router-link v-if="user?.role === 'hr'" to="/hr/posts">我的帖子</router-link>
                <router-link v-if="user?.role === 'student' || !user" to="/ask">提问</router-link>
              </nav>
              <div class="account">
                <template v-if="user">
                  <div class="account-profile">
                    <button
                      class="account-trigger"
                      type="button"
                      :aria-label="`${user.nickname} 的个人信息`"
                      @click="openProfileDrawer"
                    >
                      <span class="account-avatar">
                        <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                        <template v-else>{{ avatarInitial }}</template>
                      </span>
                      <span class="account-name">{{ user.nickname }}</span>
                    </button>
                    <div class="account-pop" role="tooltip" @click="openProfileDrawer">
                      <span class="role">{{ roleLabel }}</span>
                      <p v-if="profileSummary" class="account-pop-meta">{{ profileSummary }}</p>
                      <p v-else class="account-pop-meta account-pop-meta--muted">资料待完善</p>
                      <span class="account-pop-hint">点击查看完整资料</span>
                    </div>
                  </div>
                  <MessageMenu />
                  <button class="link logout-link" type="button" @click="confirmLogout">退出</button>
                </template>
                <template v-else>
                  <router-link class="account-link" to="/login">登录</router-link>
                  <router-link class="account-link" to="/register">注册</router-link>
                </template>
              </div>
            </header>
            <main :class="['my-qa', 'student-messages', 'hr-workbench', 'hr-profile', 'hr-messages', 'hr-posts', 'hr-post-new', 'hr-post-editor'].includes(String(route.name)) ? 'wide-shell' : 'container'">
              <router-view v-slot="{ Component, route: currentRoute }">
                <keep-alive include="QuestionListView,HrWorkspaceView,HrProfileView,HrMessagesView">
                  <component
                    :is="Component"
                    :key="currentRoute.path === '/' ? 'public-feed' : user?.role === 'student' ? `student-${user.id}-${currentRoute.fullPath}` : ['hr-posts', 'hr-post-new', 'hr-post-editor'].includes(String(currentRoute.name)) ? `${user?.id}-${currentRoute.fullPath}` : ['hr-profile', 'hr-messages'].includes(String(currentRoute.name)) ? `${String(currentRoute.name)}-${user?.id}` : currentRoute.name === 'hr-workbench' ? 'hr-workbench' : currentRoute.fullPath"
                  />
                </keep-alive>
              </router-view>
            </main>

            <!-- 退出登录确认：全屏遮罩 + 居中大字号弹窗 -->
            <Teleport to="body">
              <Transition name="confirm-fade">
                <div
                  v-if="showLogoutConfirm"
                  class="confirm-mask"
                  @click.self="closeLogoutConfirm"
                >
                  <div
                    class="confirm-dialog"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="logout-confirm-title"
                  >
                    <h2 id="logout-confirm-title" class="confirm-title">确定要退出登录吗？</h2>
                    <p class="confirm-hint">退出后需要重新登录，才能继续查看我的问答和消息。</p>
                    <div class="confirm-actions">
                      <button class="confirm-btn confirm-btn--ghost" type="button" @click="closeLogoutConfirm">
                        取消
                      </button>
                      <button class="confirm-btn confirm-btn--primary" type="button" @click="doLogout">
                        退出登录
                      </button>
                    </div>
                  </div>
                </div>
              </Transition>
            </Teleport>

            <ProfileDrawer
              :show="showProfileDrawer"
              :user="user"
              @close="closeProfileDrawer"
              @logout="logoutFromDrawer"
            />
          </n-loading-bar-provider>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
