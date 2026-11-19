<script setup lang="ts">
const excerptWidths = ["100%", "100%", "92%", "66%"];
const actionWidths = ["w-14", "w-10", "w-10", "w-5"];
</script>

<template>
  <article
    class="feed-skeleton flex flex-col gap-4 rounded-card-lg border border-solid border-line bg-surface py-4"
    aria-hidden="true"
  >
    <div class="flex flex-col gap-2 px-4">
      <div class="skeleton-bar h-[30px] w-3/4 rounded sm:h-8" />
      <div class="flex flex-col gap-2.5">
        <div
          v-for="(width, index) in excerptWidths"
          :key="index"
          class="skeleton-bar h-4 rounded"
          :style="{ width }"
        />
        <div class="flex justify-end">
          <div class="skeleton-bar h-[22px] w-16 rounded" />
        </div>
      </div>
      <div class="flex justify-end gap-4">
        <div
          v-for="(width, index) in actionWidths"
          :key="index"
          class="skeleton-bar h-[22px] rounded"
          :class="width"
        />
      </div>
    </div>
  </article>
</template>

<style scoped>
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