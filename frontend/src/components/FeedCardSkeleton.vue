<script setup lang="ts">
const excerptWidths = ["100%", "96%", "68%"];
const actionWidths = ["w-12", "w-12", "w-8"];
</script>

<template>
  <article class="feed-skeleton" aria-hidden="true">
    <div class="flex items-center justify-between">
      <div class="skeleton-bar h-6 w-28 rounded" />
      <div class="skeleton-bar h-4 w-16 rounded" />
    </div>
    <div class="skeleton-bar mt-3 h-[30px] w-3/4 rounded" />
    <div class="mt-3 flex flex-col gap-2.5">
      <div
        v-for="(width, index) in excerptWidths"
        :key="index"
        class="skeleton-bar h-4 rounded"
        :style="{ width }"
      />
    </div>
    <div class="mt-3 flex items-center justify-between border-t border-line-subtle pt-3">
      <div class="skeleton-bar h-9 w-32 rounded-lg" />
      <div class="flex gap-2">
        <div
          v-for="(width, index) in actionWidths"
          :key="index"
          class="skeleton-bar h-9 rounded-lg"
          :class="width"
        />
      </div>
    </div>
  </article>
</template>

<style scoped>
.feed-skeleton {
  border: 1px solid var(--color-border-default);
  border-radius: 12px;
  background: var(--color-bg-surface);
  padding: 20px 22px;
}

.skeleton-bar {
  background: linear-gradient(90deg, #eef0f3 25%, #f6f7f9 37%, #eef0f3 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  from {
    background-position: 100% 50%;
  }

  to {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-bar {
    animation: none;
  }
}
</style>
