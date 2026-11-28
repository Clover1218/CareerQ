<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import { useUserStore } from "@/stores/user";
import { authRedirect } from "@/utils/authRedirect";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const returnTo = computed(() => authRedirect(route.query.redirect));
const phone = ref("13800000001");
const code = ref("123456");
const error = ref("");
const loading = ref(false);

async function sendCode() {
  error.value = "";
  try {
    await api.sendCode(phone.value.trim());
    error.value = "验证码已发送（dev 环境固定为 123456）";
  } catch (e: any) {
    error.value = e.message;
  }
}

async function login() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    await userStore.login(phone.value.trim(), code.value.trim());
    router.replace(returnTo.value);
  } catch (e: any) {
    if (e?.code === 40401) {
      router.push({ path: "/register", query: { phone: phone.value.trim(), redirect: returnTo.value } });
      return;
    }
    error.value = `登录失败：${e.message}`;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card public-login-card">
    <h2>登录 CareerQ</h2>
    <p v-if="returnTo.startsWith('/users/')" class="muted">登录后即可查看该用户的主页。</p>
    <p class="muted">dev 环境验证码固定为 123456；演示账号 13800000001（学生）/ 13800000003、13800000008（运营）/ 13800000007（仲裁运营）/ HR：13800000002 陈静怡、13800000004 王浩然、13800000005 李梦琪、13800000006 张帆</p>

    <div class="field">
      <label>手机号</label>
      <input v-model="phone" placeholder="1xxxxxxxxxx" />
    </div>
    <div class="field">
      <label>验证码</label>
      <div class="row">
        <input v-model="code" placeholder="6 位验证码" style="flex: 1" />
        <button @click="sendCode">发送验证码</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <button class="primary" style="width: 100%" :disabled="loading" @click="login">
      {{ loading ? "登录中…" : "登录" }}
    </button>
    <nav class="login-alternatives" aria-label="注册与浏览">
      <router-link class="editorial-button register-entry" :to="{ path: '/register', query: { redirect: returnTo } }">
        注册账号
      </router-link>
      <router-link class="editorial-button" to="/">游客浏览</router-link>
    </nav>
  </div>
</template>

<style scoped>
.login-alternatives {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-subtle);
}

.login-alternatives .editorial-button {
  min-height: 42px;
  font-size: 14px;
}

.register-entry {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
}

.register-entry:hover {
  border-color: var(--color-brand);
}
</style>
