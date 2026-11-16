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
const avatarFile = ref<File | null>(null);
const avatarMsg = ref("");
const eduMsg = ref("");
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

const hasSavedProfile = () =>
  !!(profile.value?.school || profile.value?.major || profile.value?.grade
    || profile.value?.degree || profile.value?.graduationYear
    || (profile.value?.experiences?.length ?? 0) > 0);

onMounted(async () => {
  try {
    me.value = await api.me();
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

function onAvatarPick(event: Event) {
  const input = event.target as HTMLInputElement;
  avatarFile.value = input.files?.[0] ?? null;
  avatarMsg.value = "";
}

async function uploadAvatar() {
  if (!avatarFile.value) {
    avatarMsg.value = "请先选择图片文件";
    return;
  }
  saving.value = true;
  error.value = "";
  avatarMsg.value = "";
  try {
    const updated = await api.uploadAvatar(avatarFile.value);
    if (me.value) me.value.avatarUrl = updated.avatarUrl ?? null;
    avatarMsg.value = "头像已上传并更新";
    avatarFile.value = null;
  } catch (e: any) {
    error.value = `上传头像失败：${e.message}`;
  } finally {
    saving.value = false;
  }
}

async function saveEdu() {
  saving.value = true;
  error.value = "";
  eduMsg.value = "";
  try {
    profile.value = await api.updateProfile({
      school: edu.value.school.trim() || null,
      major: edu.value.major.trim() || null,
      grade: edu.value.grade.trim() || null,
      degree: edu.value.degree.trim() || null,
      graduationYear: edu.value.graduationYear.trim() || null,
      experiences: profile.value?.experiences ?? [],
    });
    eduMsg.value = "资料已保存，已同步到上方「当前资料」";
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
          <span class="muted" style="margin-left: 8px">这里是你的个人中心，可管理头像、查看我的问题与收藏、完善资料。</span>
        </p>
      </div>
      <button class="link" @click="logout">退出登录</button>
    </div>

    <!-- 头像（仅支持本站上传，不使用外部 URL） -->
    <div class="card" style="margin-top: 16px">
      <h3>头像</h3>
      <p class="muted">支持 PNG / JPEG / WEBP，大小不超过 2MB；头像保存在本站，不接受外部链接。</p>
      <div class="row" style="align-items: center">
        <UserAvatar :nickname="me?.nickname" :avatar-url="me?.avatarUrl" :size="56" />
        <input type="file" accept="image/png,image/jpeg,image/webp" @change="onAvatarPick" style="flex: 1" />
        <button class="primary" :disabled="saving || !avatarFile" @click="uploadAvatar">
          {{ saving ? "上传中…" : "上传头像" }}
        </button>
      </div>
      <p v-if="avatarMsg" class="muted">{{ avatarMsg }}</p>
    </div>
    <!-- 学生：我的问题 / 我的收藏 / 当前资料 / 完善资料 -->
    <template v-if="me?.role === 'student'">
      <div class="card" style="margin-top: 16px">
        <h3>我的问题（{{ myQuestions.length }}）</h3>
        <p class="muted">这里是你提过的问题及当前处理状态，点击标题可查看详情与回答。</p>
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
        <p class="muted">收藏的问题、回答或公开沉淀会出现在这里，方便随时回看。</p>
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

      <!-- 当前已保存的资料 -->
      <div class="card" style="margin-top: 16px">
        <h3>当前资料</h3>
        <p class="muted">以下是你已经保存的资料信息，可在下方修改后保存。</p>
        <div v-if="hasSavedProfile()">
          <p>
            学校：{{ profile?.school || "未填写" }}
            <span style="margin-left: 16px">专业：{{ profile?.major || "未填写" }}</span>
          </p>
          <p>
            年级：{{ profile?.grade || "未填写" }}
            <span style="margin-left: 16px">学历：{{ profile?.degree || "未填写" }}</span>
            <span style="margin-left: 16px">毕业年份：{{ profile?.graduationYear || "未填写" }}</span>
          </p>
          <div v-if="(profile?.experiences?.length ?? 0) > 0">
            <p>实习 / 项目经历：</p>
            <ul style="padding-left: 18px; margin: 0">
              <li v-for="(exp, i) in profile?.experiences" :key="i" style="margin: 6px 0">
                <strong>{{ exp.type === "internship" ? "实习" : "项目" }}</strong>
                · {{ exp.title }}
                <span v-if="exp.role">· {{ exp.role }}</span>
                <span v-if="exp.startDate || exp.endDate">· {{ exp.startDate || "?" }} ~ {{ exp.endDate || "至今" }}</span>
                <div v-if="exp.description" class="muted" style="font-size: 13px">{{ exp.description }}</div>
              </li>
            </ul>
          </div>
          <p v-else class="muted">暂无实习 / 项目经历。</p>
        </div>
        <p v-else class="muted">还没有保存任何资料，填写下方表单后点击「保存资料」即可。</p>
      </div>

      <!-- 编辑资料 -->
      <div class="card" style="margin-top: 16px">
        <h3>完善资料（选填）</h3>
        <p class="muted">
          学校、专业、年级、学历、毕业年份均为选填；保存后会展示在「当前资料」中，方便 HR 了解你的背景。
        </p>
        <div class="row">
          <div class="field" style="flex: 1">
            <label>学校</label>
            <input v-model="edu.school" maxlength="128" placeholder="如 某大学" />
          </div>
          <div class="field" style="flex: 1">
            <label>专业</label>
            <input v-model="edu.major" maxlength="128" placeholder="如 计算机" />
          </div>
        </div>
        <div class="row">
          <div class="field" style="flex: 1">
            <label>年级</label>
            <input v-model="edu.grade" maxlength="32" placeholder="如 大三" />
          </div>
          <div class="field" style="flex: 1">
            <label>学历</label>
            <input v-model="edu.degree" maxlength="32" placeholder="如 本科" />
          </div>
          <div class="field" style="flex: 1">
            <label>毕业年份</label>
            <input v-model="edu.graduationYear" maxlength="16" placeholder="如 2027" />
          </div>
        </div>
        <button class="primary" :disabled="saving" @click="saveEdu">
          {{ saving ? "保存中…" : "保存资料" }}
        </button>
        <p v-if="eduMsg" class="muted" style="margin-top: 8px">{{ eduMsg }}</p>
      </div>
    </template>
  </div>
</template>
