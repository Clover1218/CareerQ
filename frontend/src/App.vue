<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, type User } from "./api";
import UserAvatar from "./components/UserAvatar.vue";

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



onMounted(refresh);
watch(() => route.fullPath, refresh);
</script>

<template>
  <header class="topbar">
    <div class="brand" @click="router.push('/')">职问桥 <span>CareerQ</span></div>
    <nav class="nav">
      <router-link to="/">公开问答</router-link>
      <router-link v-if="!user || user.role === 'student'" to="/ask">提问</router-link>
      <router-link v-if="user?.role === 'hr'" to="/hr/private-pool">私密工单</router-link>
    </nav>
    <div class="account">
      <template v-if="user">
        <router-link to="/me" class="account-link">
          <UserAvatar :nickname="user.nickname" :avatar-url="user.avatarUrl" :size="28" />
          <span class="nick">{{ user.nickname }}</span>
        </router-link>
      </template>
      <template v-else>
        <router-link to="/login">登录</router-link>
        <router-link to="/register">注册</router-link>
      </template>
    </div>
  </header>
  <main class="container">
    <router-view />
  </main>
</template>
