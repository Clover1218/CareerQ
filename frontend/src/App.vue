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
const accountTrigger = ref<HTMLButtonElement | null>(null);
const logoutCancel = ref<HTMLButtonElement | null>(null);
let topbarObserver: ResizeObserver | undefined;

const isAdminPage = computed(() => ["admin-review", "admin-review-workspace"].includes(String(route.name)));

function syncTopbarHeight() {
  if (!topbar.value) return;
  document.documentElement.style.setProperty("--topbar-height", `${topbar.value.getBoundingClientRect().height}px`);
}

const avatarInitial = computed(() => Array.from(user.value?.nickname.trim() || "学")[0]);

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
const accountDialogOpen = computed(() => showProfileDrawer.value || showLogoutConfirm.value);

function openProfileDrawer() {
  showProfileDrawer.value = true;
}

function closeProfileDrawer() {
  showProfileDrawer.value = false;
}

function logoutFromDrawer() {
  showProfileDrawer.value = false;
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

watch([showProfileDrawer, showLogoutConfirm], ([drawer, confirm]) => {
  document.body.style.overflow = drawer || confirm ? "hidden" : "";
  if (confirm) logoutCancel.value?.focus({ preventScroll: true });
  else if (!drawer) accountTrigger.value?.focus({ preventScroll: true });
}, { flush: "post" });

watch(() => user.value?.id, () => {
  showProfileDrawer.value = false;
  showLogoutConfirm.value = false;
});

// 后台是独立界面：不留顶栏高度，也不预留页面滚动条
watch(
  isAdminPage,
  (admin) => {
    document.body.classList.toggle("admin-mode", admin);
    document.documentElement.classList.toggle("admin-mode", admin);
  },
  { immediate: true },
);

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
  document.body.classList.remove("admin-mode");
  document.documentElement.classList.remove("admin-mode");
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
            <header v-if="!isAdminPage" ref="topbar" class="topbar" :inert="accountDialogOpen">
              <router-link class="brand" to="/" aria-label="CareerQ 首页">CareerQ</router-link>
              <nav class="nav" :class="{ 'nav--hr': user?.role === 'hr' }" aria-label="主导航">
                <router-link to="/">公开问答</router-link>
                <router-link to="/hr-shares" :class="{ 'router-link-active': route.name === 'hr-share-detail' }">HR 分享</router-link>
                <router-link v-if="user?.role === 'hr'" to="/hr">HR 工作台</router-link>
                <router-link
                  v-if="user?.role === 'hr'"
                  to="/hr/posts"
                  :class="{ 'router-link-active': route.name === 'hr-post-new' || route.name === 'hr-post-editor' }"
                >我的帖子</router-link>
                <router-link v-if="user?.role === 'operator' || user?.role === 'admin'" to="/admin">
                  管理面板
                </router-link>
                <router-link v-if="user?.role === 'student' || !user" to="/ask">提问</router-link>
              </nav>
              <div class="account">
                <router-link v-if="user?.role === 'student' || !user" class="topbar-ask" to="/ask">提问</router-link>
                <template v-if="user">
                  <MessageMenu />
                  <button
                    ref="accountTrigger"
                    class="account-trigger"
                    type="button"
                    :aria-label="`${user.nickname} 的个人菜单`"
                    title="个人菜单"
                    aria-haspopup="dialog"
                    :aria-expanded="showProfileDrawer"
                    @click="openProfileDrawer"
                  >
                    <span class="account-avatar" aria-hidden="true">
                      <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                      <template v-else>{{ avatarInitial }}</template>
                    </span>
                  </button>
                </template>
                <div v-else class="account-auth">
                  <router-link class="account-link" to="/login">登录</router-link>
                  <router-link class="account-link" to="/register">注册</router-link>
                </div>
              </div>
            </header>
<main :inert="accountDialogOpen" :class="isAdminPage ? 'admin-shell' : ['my-qa', 'student-messages', 'hr-workbench', 'hr-profile', 'hr-messages', 'hr-posts', 'hr-post-new', 'hr-post-editor'].includes(String(route.name)) ? 'wide-shell' : 'container'">
              <router-view v-slot="{ Component, route: currentRoute }">
                <keep-alive include="QuestionListView,PublicHrSharesView,HrWorkspaceView,HrProfileView,HrMessagesView">
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
                    <p class="confirm-hint">退出后需要重新登录，才能继续查看个人中心和消息。</p>
                    <div class="confirm-actions">
                      <button ref="logoutCancel" class="confirm-btn confirm-btn--ghost" type="button" @click="closeLogoutConfirm">
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
