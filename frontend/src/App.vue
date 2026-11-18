<script setup lang="ts">
import { onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { dateZhCN, zhCN, type GlobalThemeOverrides } from "naive-ui";
import { useUserStore } from "@/stores/user";

// Naive UI 主题与 Figma token 对齐，避免组件库默认蓝与设计稿脱节
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: "#2F6FED",
    primaryColorHover: "#1D4FC4",
    primaryColorPressed: "#1D4FC4",
    borderRadius: "6px",
    fontFamily: '"MiSans", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
    textColorBase: "#111827",
    borderColor: "#E5E7EB",
    bodyColor: "#F8F9FA",
  },
};

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const { user } = storeToRefs(userStore);

async function refresh() {
  await userStore.fetchUserInfo();
}

async function logout() {
  await userStore.logout();
  router.push("/login");
}

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
            <header class="topbar">
              <div class="brand" @click="router.push('/')">CareerQ</div>
              <nav class="nav">
                <router-link to="/">公开问答</router-link>
                <router-link to="/me">我的问答</router-link>
                <router-link to="/ask">提问</router-link>
                <router-link v-if="user?.role === 'hr'" to="/hr/private-pool">私密工单</router-link>
              </nav>
              <div class="account">
                <template v-if="user">
                  <span class="nickname">{{ user.nickname }}</span>
                  <button class="link logout-link" @click="logout">退出</button>
                </template>
                <template v-else>
                  <router-link class="account-link" to="/login">登录</router-link>
                  <router-link class="account-link" to="/register">注册</router-link>
                </template>
              </div>
            </header>
            <main :class="route.name === 'my-qa' ? 'wide-shell' : 'container'">
              <router-view />
            </main>
          </n-loading-bar-provider>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
