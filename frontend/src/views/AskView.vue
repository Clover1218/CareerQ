<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { api } from "../api";
import TagPicker from "../components/TagPicker.vue";

const router = useRouter();
const message = useMessage();
const title = ref("");
const body = ref("");
const tags = ref<string[]>([]);
const error = ref("");
const loading = ref(false);

async function submit() {
  if (!title.value.trim() || !body.value.trim()) {
    error.value = "标题和描述不能为空";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const created = await api.createQuestion({
      title: title.value.trim(),
      body: body.value.trim(),
      tags: tags.value,
      scope: "public",
    });
    if (created.status !== "published") {
      // 机审未直接通过：明确告知作者需要人工审核，避免“发了但看不到”的疑惑
      message.warning(
        "问题已提交，正在人工审核；审核通过后才会出现在公开问答中，可在「我的问答」查看进度。",
      );
      router.push({ path: "/me", query: { tab: "questions" } });
      return;
    }
    router.push(`/questions/${created.id}`);
  } catch (e: any) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card">
    <h2>我要提问</h2>
    <div class="field">
      <label>标题</label>
      <input v-model="title" maxlength="200" placeholder="一句话说清你的问题" />
    </div>
    <div class="field">
      <label>描述</label>
      <textarea v-model="body" maxlength="5000" placeholder="补充背景、你已尝试的方法、希望得到什么建议" />
    </div>
    <div class="field">
      <label>文章标签（最多 5 个）</label>
      <TagPicker v-model="tags" :max="5" />
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <button class="primary" :disabled="loading" @click="submit">{{ loading ? "提交中…" : "提交" }}</button>
  </div>
</template>
