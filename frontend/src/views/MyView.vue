<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  api,
  type Favorite,
  type Profile,
  type Question,
  type User,
} from "../api";
import UserAvatar from "../components/UserAvatar.vue";

const router = useRouter();
const me = ref<User | null>(null);
const profile = ref<Profile | null>(null);
const myQuestions = ref<Question[]>([]);
const favorites = ref<Favorite[]>([]);
const avatarInput = ref("");
const message = ref("");
const error = ref("");
const saving = ref(false);
const edu = ref({ school: "", major: "", grade: "", degree: "", graduationYear: "" });

const roleLabel = (role?: string) =>
  role === "hr" ? "HR" : role === "operator" ? "运营" : role === "admin" ? "管理员" : "学生";

const statusLabel: Record<string, string> = {
  pending_human: "待审核",
  published: "已发布",
  rejected: "已驳回",
  open: "待认领",
  assigned: "已认领",
  closed: "已关闭",
  public_pending: "公开待审",
  public_archived: "已公开",
  public_rejected: "公开被驳回",
};

onMounted(async () => {
  try {
    me.value = await api.me();
    avatarInput.value = me.value.avatarUrl ?? "";
    try {
      profile.value = await api.getProfile();
      edu.value = {
        school: profile.value.school ?? "",
        major: profile.value.major ?? "",
        grade: profile.value.grade ?? "",
        degree: profile.value.degree ?? "",
        graduationYear: profile.value.graduationYear ?? "",
      };
    } catch {}
    if (me.value.role === "student") {
      myQuestions.value = (await api.listMyQuestions()).items;
      favorites.value = (await api.listFavorites()).items;
    }
  } catch (e: any) {
    error.value = e.message ?? "加载失败";
  }
});

async function saveAvatar() {
  saving.value = true;
  error.value = "";
  message.value = "";
  try {
    const updated = await api.updateAvatar(avatarInput.value.trim());
    if (me.value) me.value.avatarUrl = updated.avatarUrl ?? null;
    message.value = "头像已更新";
  } catch (e: any) {
    error.value = `保存头像失败：${e.message}`;
  } finally {
    saving.value = false;
  }
}

async function saveEdu() {
  saving.value = true;
  error.value = "";
  message.value = "";
  try {
    profile.value = await api.updateProfile({
      school: edu.value.school.trim() || null,
      major: edu.value.major.trim() || null,
      grade: edu.value.grade.trim() || null,
      degree: edu.value.degree.trim() || null,
      graduationYear: edu.value.graduationYear.trim() || null,
      experiences: profile.value?.experiences ?? [],
    });
    message.value = "资料已保存";
  } catch (e: any) {
    error.value = `保存资料失败：${e.message}`;
  } finally {
    saving.value = false;
  }
}

async function logout() {
  await api.logout();
  router.push("/login");
}
</script>

<template>
  <div v-if="error" class="card" style="max-width: 760px; margin: 24px auto">
    <p class="error">{{ error }}</p>
    <p class="muted">请确认已登录：<router-link to="/login">去登录</router-link></p>
  </div>

  <div v-else style="max-width: 760px; margin: 24px auto">
    <div v-if="me" class="card" style="display: flex; gap: 16px; align-items: center">
      <UserAvatar :nickname="me.nickname" :avatar-url="me.avatarUrl" :size="72" />
      <div style="flex: 1">
        <h2 style="margin: 0">{{ me.nickname }}</h2>
        <p class="muted" style="margin: 4px 0 0">
          <span class="tag">{{ roleLabel(me.role) }}</span>
        </p>
      </div>
      <button class="link" @click="logout">退出登录</button>
    </div>

    <!-- 头像 -->
    <div class="card" style="margin-top: 16px">
      <h3>头像</h3>
      <p class="muted">填写图片 URL 即可使用自定义头像；留空则使用昵称首字生成的默认头像。</p>
      <div class="row">
        <input v-model="avatarInput" placeholder="https://…/avatar.png" style="flex: 1" />
        <button class="primary" :disabled="saving" @click="saveAvatar">保存头像</button>
      </div>
      <p v-if="message" class="muted">{{ message }}</p>
    </div>

    <!-- 学生：我的问题 / 我的收藏 / 完善资料 -->
    <template v-if="me?.role === 'student'">
      <div class="card" style="margin-top: 16px">
        <h3>我的问题（{{ myQuestions.length }}）</h3>
        <p v-if="myQuestions.length === 0" class="muted">还没有提过问题，<router-link to="/ask">去提问</router-link>。</p>
        <ul style="padding-left: 18px; margin: 0">
          <li v-for="q in myQuestions" :key="q.id" style="margin: 8px 0">
            <router-link :to="`/questions/${q.id}`">{{ q.title }}</router-link>
            <span class="tag" style="margin-left: 8px">{{ statusLabel[q.status] ?? q.status }}</span>
            <span class="tag" style="margin-left: 4px">{{ q.scope === "private" ? "私密" : "公开" }}</span>
          </li>
        </ul>
      </div>

      <div class="card" style="margin-top: 16px">
        <h3>我的收藏（{{ favorites.length }}）</h3>
        <p v-if="favorites.length === 0" class="muted">还没有收藏内容。</p>
        <ul style="padding-left: 18px; margin: 0">
          <li v-for="f in favorites" :key="`${f.targetType}-${f.targetId}`" style="margin: 8px 0">
            <template v-if="f.targetType === 'question'">
              <router-link :to="`/questions/${f.targetId}`">{{ f.title }}</router-link>
            </template>
            <template v-else>{{ f.title }}</template>
            <div class="muted" style="font-size: 13px">{{ f.excerpt }}</div>
          </li>
        </ul>
      </div>

      <div class="card" style="margin-top: 16px">
        <h3>完善资料（选填）</h3>
        <div class="row">
          <div class="field" style="flex: 1"><label>学校</label><input v-model="edu.school" /></div>
          <div class="field" style="flex: 1"><label>专业</label><input v-model="edu.major" /></div>
        </div>
        <div class="row">
          <div class="field" style="flex: 1"><label>年级</label><input v-model="edu.grade" placeholder="如 大三" /></div>
          <div class="field" style="flex: 1"><label>学历</label><input v-model="edu.degree" placeholder="如 本科" /></div>
          <div class="field" style="flex: 1"><label>毕业年份</label><input v-model="edu.graduationYear" placeholder="如 2027" /></div>
        </div>
        <button class="primary" :disabled="saving" @click="saveEdu">保存资料</button>
      </div>
    </template>
  </div>
</template>
