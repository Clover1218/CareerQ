<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, type User } from "./api";

const router = useRouter();
const route = useRoute();
const user = ref<User | null>(null);

async function refresh() {
  try {
    user.value = await api.me();
  } catch {
    user.value = null;
  }
}

async function logout() {
  await api.logout();
  user.value = null;
  router.push("/login");
}

onMounted(refresh);
watch(() => route.fullPath, refresh);
</script>

<template>
  <header class="topbar" :class="{ 'topbar-hr': user?.role === 'hr', 'topbar-operator': user?.role === 'operator' || user?.role === 'admin' }">
    <div class="brand" @click="router.push(user?.role === 'hr' ? '/hr/questions' : user?.role === 'operator' || user?.role === 'admin' ? '/admin/moderation' : '/')">
      <template v-if="user?.role === 'hr'">HR 工作台 <span>CareerQ</span></template>
      <template v-else-if="user?.role === 'operator' || user?.role === 'admin'">运营审核 <span>CareerQ</span></template>
      <template v-else>职问桥 <span>CareerQ</span></template>
    </div>
    <nav v-if="user?.role === 'hr'" class="nav">
      <router-link to="/hr/questions">公开问题池</router-link>
    </nav>
    <nav v-else-if="user?.role === 'operator' || user?.role === 'admin'" class="nav">
      <router-link to="/admin/moderation">内容审核</router-link>
    </nav>
    <nav v-else class="nav">
      <router-link to="/">公开问答</router-link>
      <router-link v-if="!user || user.role === 'student'" to="/ask">提问</router-link>
    </nav>
    <div class="account">
      <template v-if="user">
        <span class="role">{{ user.role === "hr" ? "HR" : user.role }}</span>
        <span class="nick">{{ user.nickname }}</span>
        <button class="link" @click="logout">退出</button>
      </template>
      <template v-else>
        <router-link to="/login">登录</router-link>
        <router-link to="/register">注册</router-link>
      </template>
    </div>
  </header>
  <main class="container" :class="{ 'container-hr': user?.role === 'hr', 'container-operator': user?.role === 'operator' || user?.role === 'admin' }">
    <router-view />
  </main>
</template>
