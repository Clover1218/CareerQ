<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, api } from "../api";

const router = useRouter();
const route = useRoute();
const phone = ref(String(route.query.phone ?? ""));
const code = ref("123456");
const nickname = ref("");
const role = ref<"student" | "hr">("student");
const company = ref("");
const industry = ref("");
const position = ref("");
const error = ref("");
const loading = ref(false);

function switchRole(next: "student" | "hr") {
  role.value = next;
  error.value = "";
}

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
  if (role.value === "hr" && !company.value.trim()) {
    error.value = "HR 注册需要填写就职公司";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    await api.register({
      phone: phone.value.trim(),
      code: code.value.trim(),
      nickname: nickname.value.trim() || undefined,
      role: role.value,
      company: role.value === "hr" ? company.value.trim() : undefined,
      industry: role.value === "hr" ? industry.value.trim() || undefined : undefined,
      position: role.value === "hr" ? position.value.trim() || undefined : undefined,
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

function close() {
  router.push("/");
}
</script>

<template>
  <div class="modal-overlay">
    <div class="modal-card">
      <div class="modal-head">
        <h2>注册职问桥</h2>
        <button class="modal-close" title="先逛逛" @click="close">✕</button>
      </div>

      <!-- 浮动窗口顶端：身份切换（点击事件切换表单） -->
      <div class="segmented">
        <button :class="{ active: role === 'student' }" @click="switchRole('student')">我是学生</button>
        <button :class="{ active: role === 'hr' }" @click="switchRole('hr')">我是 HR</button>
      </div>

      <div class="modal-body">
        <p v-if="role === 'student'" class="role-hint">
          学生注册只需手机号 + 验证码；学校/专业等资料可在登录后「完善资料」里选填。
        </p>
        <p v-else class="role-hint">
          HR 账号用于回答问题、认领私密工单。真实在职认证暂缓，当前填写公司信息即可注册。
        </p>

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

        <template v-if="role === 'hr'">
          <div class="field">
            <label>就职公司（必填）</label>
            <input v-model="company" maxlength="128" placeholder="如 某互联网公司" />
          </div>
          <div class="row">
            <div class="field" style="flex: 1">
              <label>职位（选填）</label>
              <input v-model="position" maxlength="64" placeholder="如 HRBP" />
            </div>
            <div class="field" style="flex: 1">
              <label>行业（选填）</label>
              <input v-model="industry" maxlength="64" placeholder="如 互联网" />
            </div>
          </div>
        </template>

        <p v-if="error" class="error">{{ error }}</p>
        <button class="primary" style="width: 100%" :disabled="loading" @click="submit">
          {{ loading ? "注册中…" : role === "hr" ? "注册 HR 账号" : "注册学生账号" }}
        </button>
        <p class="muted" style="text-align: center; margin-top: 12px">
          已有账号？<router-link to="/login">去登录</router-link>
          <span> · </span>
          <router-link to="/">先随便逛逛</router-link>
        </p>
      </div>
    </div>
  </div>
</template>
