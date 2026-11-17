<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, api } from "../api";

const router = useRouter();
const route = useRoute();
const phone = ref(String(route.query.phone ?? ""));
const code = ref("123456");
const nickname = ref("");
const school = ref("");
const major = ref("");
const grade = ref("");
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

async function submit() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    await api.register({
      phone: phone.value.trim(),
      code: code.value.trim(),
      nickname: nickname.value.trim() || undefined,
      school: school.value.trim() || undefined,
      major: major.value.trim() || undefined,
      grade: grade.value.trim() || undefined,
    });
    router.push(String(route.query.redirect ?? "/"));
  } catch (e: any) {
    if (e instanceof ApiError && e.code === 40901) {
      error.value = "该手机号已注册，请直接登录";
    } else {
      error.value = `注册失败：${e.message}`;
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card" style="max-width: 460px; margin: 40px auto">
    <h2>注册职问桥</h2>
    <p class="muted">注册后才能发帖、回答、追问；未注册也可以直接浏览公开问题池。</p>

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
    <div class="field">
      <label>昵称（选填，不填自动生成）</label>
      <input v-model="nickname" maxlength="64" placeholder="公开显示的名字" />
    </div>
    <div class="field">
      <label>学校（可选）</label>
      <input v-model="school" maxlength="128" />
    </div>
    <div class="field">
      <label>专业（可选）</label>
      <input v-model="major" maxlength="128" />
    </div>
    <div class="field">
      <label>年级（可选）</label>
      <input v-model="grade" maxlength="32" placeholder="如 大三" />
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <button class="primary" style="width: 100%" :disabled="loading" @click="submit">
      {{ loading ? "注册中…" : "注册并登录" }}
    </button>
    <p class="muted" style="text-align: center; margin-top: 12px">
      已有账号？<router-link to="/login">去登录</router-link>
      <span> · </span>
      <router-link to="/">先随便逛逛</router-link>
    </p>
  </div>
</template>
