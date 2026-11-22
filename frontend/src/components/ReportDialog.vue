<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useMessage } from "naive-ui";
import { api } from "@/api";

const props = defineProps<{
  show: boolean;
  targetType: "question" | "answer";
  targetId: number;
}>();
const emit = defineEmits<{
  "update:show": [value: boolean];
  reported: [];
}>();

const message = useMessage();
const reason = ref("");
const detail = ref("");
const error = ref("");
const submitting = ref(false);
const visible = computed({
  get: () => props.show,
  set: value => emit("update:show", value),
});
const title = computed(() => props.targetType === "question" ? "举报问题" : "举报回答");
const reasons = [
  { value: "spam", label: "垃圾广告或营销" },
  { value: "harassment", label: "人身攻击或骚扰" },
  { value: "privacy", label: "泄露个人或企业隐私" },
  { value: "misinformation", label: "虚假或误导性信息" },
  { value: "inappropriate", label: "其他不当内容" },
];

watch(() => props.show, open => {
  if (!open) return;
  reason.value = "";
  detail.value = "";
  error.value = "";
});

async function submit() {
  if (!reason.value || submitting.value) return;
  submitting.value = true;
  error.value = "";
  try {
    await api.report(props.targetType, props.targetId, reason.value, detail.value.trim());
    message.success("举报已提交，我们会尽快处理");
    visible.value = false;
    emit("reported");
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "举报提交失败，请稍后重试。";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    :title="title"
    :mask-closable="!submitting"
    :closable="!submitting"
    :close-on-esc="!submitting"
    class="report-modal"
  >
    <form class="report-form" @submit.prevent="submit">
      <p class="report-intro">请选择这条内容存在的问题，运营会结合你提供的信息进行处理。</p>
      <fieldset class="report-reasons">
        <legend>举报原因 <span>必选</span></legend>
        <label
          v-for="option in reasons"
          :key="option.value"
          class="report-reason"
          :class="{ 'is-selected': reason === option.value }"
        >
          <input v-model="reason" type="radio" name="report-reason" :value="option.value" :disabled="submitting" />
          <span>{{ option.label }}</span>
        </label>
      </fieldset>
      <label class="report-detail">
        <span>补充说明 <small>选填，最多 500 字</small></span>
        <textarea
          v-model="detail"
          maxlength="500"
          rows="4"
          placeholder="可以补充具体位置、问题描述或相关背景，帮助运营更快判断。"
          :disabled="submitting"
        />
        <small>{{ detail.length }} / 500</small>
      </label>
      <p class="report-privacy">举报记录仅用于平台处理，不会公开展示你的举报身份。</p>
      <p v-if="error" class="report-error" role="alert">{{ error }}</p>
      <div class="report-actions">
        <button type="button" :disabled="submitting" @click="visible = false">取消</button>
        <button type="submit" class="primary" :disabled="!reason || submitting">
          {{ submitting ? "提交中…" : "提交举报" }}
        </button>
      </div>
    </form>
  </n-modal>
</template>

<style scoped>
:global(.report-modal) {
  width: 540px;
  max-width: calc(100vw - 32px);
}

.report-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.report-intro,
.report-privacy {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 22px;
}

.report-reasons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  border: 0;
  padding: 0;
}

.report-reasons legend {
  grid-column: 1 / -1;
  margin-bottom: 8px;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.report-reasons legend span {
  margin-left: 5px;
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.report-reason {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--color-border-default);
  border-radius: 8px;
  padding: 9px 11px;
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 20px;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.report-reason:hover,
.report-reason.is-selected {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.report-reason input {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
  margin: 0;
  padding: 0;
  accent-color: var(--color-brand);
}

.report-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.report-detail > span {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.report-detail small {
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 400;
}

.report-detail > small {
  align-self: flex-end;
}

.report-detail textarea {
  min-height: 104px;
  font-weight: 400;
}

.report-error {
  margin: 0;
  border-radius: 7px;
  background: var(--color-danger-soft);
  padding: 9px 11px;
  color: var(--color-danger);
  font-size: 13px;
  line-height: 20px;
}

.report-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.report-actions button {
  min-width: 92px;
}

.report-actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 520px) {
  .report-reasons {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .report-reason {
    transition: none;
  }
}
</style>
