<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    nickname?: string | null;
    avatarUrl?: string | null;
    size?: number;
  }>(),
  { nickname: "职", avatarUrl: null, size: 40 },
);

const initial = computed(() => (props.nickname?.trim()?.[0] ?? "职").toUpperCase());

const color = computed(() => {
  const palette = ["#2563eb", "#0d9488", "#7c3aed", "#db2777", "#ea580c", "#0891b2", "#4f46e5"];
  const text = props.nickname ?? "careerq";
  let hash = 0;
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) % 100000;
  }
  return palette[hash % palette.length];
});
</script>

<template>
  <img
    v-if="avatarUrl"
    :src="avatarUrl"
    class="avatar"
    :style="{ width: `${size}px`, height: `${size}px` }"
    alt="头像"
  />
  <span
    v-else
    class="avatar avatar-fallback"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      background: color,
      fontSize: `${Math.round(size * 0.45)}px`,
    }"
  >{{ initial }}</span>
</template>
