<script setup lang="ts">
import { computed, ref } from "vue";

const props = withDefaults(defineProps<{
  userId: number;
  nickname: string;
  avatarUrl?: string | null;
  size?: number;
}>(), { size: 32 });
const failedUrl = ref<string | null>(null);
const initial = computed(() => Array.from(props.nickname.trim() || "职")[0]);
</script>

<template>
  <router-link
    :to="{ name: 'public-profile', params: { id: userId } }"
    class="public-author-link author-profile-link"
    :aria-label="'查看 ' + nickname + ' 的公开主页'"
    :title="'查看 ' + nickname + ' 的公开主页'"
    @click.stop
  >
    <span class="author-profile-avatar" :style="{ '--avatar-size': size + 'px' }" aria-hidden="true">
      <img v-if="avatarUrl && failedUrl !== avatarUrl" :src="avatarUrl" alt="" loading="lazy" @error="failedUrl = avatarUrl" />
      <span v-else>{{ initial }}</span>
    </span>
    <span class="author-profile-name">{{ nickname }}</span>
    <span class="author-profile-arrow" aria-hidden="true">›</span>
  </router-link>
</template>

<style scoped>
.author-profile-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  max-width: 100%;
  border-radius: 999px;
  padding: 3px 8px 3px 3px;
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
  font-weight: 600;
  line-height: 1.5;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.author-profile-link:hover,
.author-profile-link:focus-visible {
  background: var(--color-brand-soft);
  color: var(--color-brand-dark);
  text-decoration: none;
}

.author-profile-link:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.author-profile-avatar {
  display: grid;
  width: var(--avatar-size);
  height: var(--avatar-size);
  flex: 0 0 var(--avatar-size);
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: calc(var(--avatar-size) * 0.43);
  font-weight: 650;
}

.author-profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-profile-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.author-profile-arrow {
  flex-shrink: 0;
  color: var(--color-brand);
  font-size: 18px;
  line-height: 1;
}
</style>
