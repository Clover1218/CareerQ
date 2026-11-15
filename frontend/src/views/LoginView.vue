<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";

const router = useRouter();
const route = useRoute();
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
    await api.login(phone.value.trim(), code.value.trim());
    router.push(String(route.query.redirect ?? "/"));
  } catch (e: any) {
    if (e?.code === 40401) {
      router.push({ path: "/register", query: { phone: phone.value.trim() } });
      return;
    }
    error.value = `登录失败：${e.message}`;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card" style="max-width: 420px; margin: 40px auto">
    <h2>登录职问桥</h2>
    <p class="muted">dev 环境验证码固定为 123456；演示账号 13800000001（学生）/ 13800000002（HR）/ 13800000003（运营）</p>

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
    <p class="muted" style="text-align: center; margin-top: 12px">
      还没有账号？<router-link to="/register">去注册</router-link>
    </p>
  </div>
</template>
