<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, api } from "../api";
import { useUserStore } from "@/stores/user";
import { authRedirect } from "@/utils/authRedirect";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const returnTo = computed(() => authRedirect(route.query.redirect));
const role = ref<"student" | "hr">("student");
const phone = ref(String(route.query.phone ?? ""));
const code = ref("123456");
const nickname = ref("");
const school = ref("");
const major = ref("");
const grade = ref("");
const company = ref("");
const industry = ref("");
const position = ref("");
const error = ref("");
const notice = ref("");
const loading = ref(false);
const sendingCode = ref(false);

async function sendCode() {
  if (sendingCode.value || loading.value) return;
  error.value = "";
  notice.value = "";
  sendingCode.value = true;
  try {
    await api.sendCode(phone.value.trim());
    notice.value = "验证码已发送（演示环境固定为 123456）";
  } catch (e: any) {
    error.value = e.message;
  } finally {
    sendingCode.value = false;
  }
}

async function submit() {
  if (loading.value) return;
  notice.value = "";
  error.value = "";
  if (role.value === "hr" && !company.value.trim()) {
    error.value = "请填写就职公司。";
    return;
  }
  const selectedRole = role.value;
  loading.value = true;
  try {
    await api.register({
      phone: phone.value.trim(),
      code: code.value.trim(),
      role: selectedRole,
      nickname: nickname.value.trim() || undefined,
      ...(selectedRole === "student" ? {
        school: school.value.trim() || undefined,
        major: major.value.trim() || undefined,
        grade: grade.value.trim() || undefined,
      } : {
        company: company.value.trim(),
        industry: industry.value.trim() || undefined,
        position: position.value.trim() || undefined,
      }),
    });
    // 注册即登录：同步 store，保证顶部栏立刻显示用户
    await userStore.fetchUserInfo(true);
    const registrationReturn = selectedRole === "hr" && !returnTo.value.startsWith("/users/") ? "/hr" : returnTo.value;
    await router.replace(registrationReturn);
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
  <div class="card register-card">
    <h2>注册职问桥</h2>
    <p class="muted register-intro">选择你的身份，开启职场问答交流。</p>

    <form @submit.prevent="submit">
      <div class="role-options" role="group" aria-label="注册身份">
        <button type="button" class="role-option" :aria-pressed="role === 'student'" :disabled="loading" @click="role = 'student'; error = ''">
          <strong>学生</strong>
          <span>提问与交流</span>
        </button>
        <button type="button" class="role-option" :aria-pressed="role === 'hr'" :disabled="loading" @click="role = 'hr'; error = ''">
          <strong>HR</strong>
          <span>回答与经验分享</span>
        </button>
      </div>
      <div class="field">
        <label for="register-phone">手机号</label>
        <input id="register-phone" v-model="phone" type="tel" autocomplete="tel" inputmode="numeric" placeholder="1xxxxxxxxxx" :disabled="loading" />
      </div>
      <div class="field">
        <label for="register-code">验证码</label>
        <div class="row code-row">
          <input id="register-code" v-model="code" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码" :disabled="loading" />
          <button type="button" :disabled="loading || sendingCode" @click="sendCode">{{ sendingCode ? "发送中…" : "发送验证码" }}</button>
        </div>
      </div>
      <div class="field">
        <label for="register-nickname">昵称（选填，不填自动生成）</label>
        <input id="register-nickname" v-model="nickname" maxlength="64" placeholder="公开显示的名字" :disabled="loading" />
      </div>

      <template v-if="role === 'student'">
        <div class="field">
          <label for="register-school">学校（可选）</label>
          <input id="register-school" v-model="school" maxlength="128" :disabled="loading" />
        </div>
        <div class="field">
          <label for="register-major">专业（可选）</label>
          <input id="register-major" v-model="major" maxlength="128" :disabled="loading" />
        </div>
        <div class="field">
          <label for="register-grade">年级（可选）</label>
          <input id="register-grade" v-model="grade" maxlength="32" placeholder="如 大三" :disabled="loading" />
        </div>
      </template>
      <template v-else>
        <div class="field">
          <label for="register-company">就职公司</label>
          <input id="register-company" v-model="company" maxlength="128" placeholder="请输入公司名称" :disabled="loading" required />
        </div>
        <div class="field">
          <label for="register-industry">行业（可选）</label>
          <input id="register-industry" v-model="industry" maxlength="64" placeholder="如 互联网" :disabled="loading" />
        </div>
        <div class="field">
          <label for="register-position">职位（可选）</label>
          <input id="register-position" v-model="position" maxlength="64" placeholder="如 校招 HR" :disabled="loading" />
        </div>
        <p class="verification-note">演示阶段可先体验 HR 工作台，职业身份认证将在后续完善。</p>
      </template>
      <p v-if="notice" class="register-notice" role="status">{{ notice }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button type="submit" class="primary register-submit" :disabled="loading">
        {{ loading ? "注册中…" : role === "hr" ? "注册 HR 账号" : "注册并登录" }}
      </button>
    </form>
    <p class="muted" style="text-align: center; margin-top: 12px">
      已有账号？<router-link :to="{ path: '/login', query: { redirect: returnTo } }">去登录</router-link>
      <span> · </span>
      <router-link to="/">先随便逛逛</router-link>
    </p>
  </div>
</template>

<style scoped>
.register-card {
  max-width: 460px;
  margin: 40px auto;
}

.register-intro {
  margin: 8px 0 18px;
}

.role-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 20px;
}

.role-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
}

.role-option strong {
  font-size: 16px;
}

.role-option span {
  font-size: 12px;
  color: var(--color-text-muted);
}

.role-option[aria-pressed="true"] {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand-dark);
}

.code-row input {
  flex: 1;
  min-width: 0;
}

.code-row button {
  flex-shrink: 0;
  white-space: nowrap;
}

.verification-note {
  border-radius: 8px;
  background: var(--color-brand-soft);
  color: var(--color-text-regular);
  padding: 10px 12px;
  margin: 0 0 14px;
  font-size: 13px;
}

.register-notice {
  margin: 8px 0;
  color: var(--color-success);
  font-size: 14px;
}

.register-submit {
  width: 100%;
}

@media (max-width: 480px) {
  .register-card {
    margin: 16px auto;
  }
}
</style>
