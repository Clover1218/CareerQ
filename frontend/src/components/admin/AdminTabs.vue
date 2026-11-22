<script setup lang="ts">
export type AdminTab = "pending" | "reviewed";

defineProps<{
  modelValue: AdminTab;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: AdminTab];
}>();
</script>

<template>
  <div class="flex h-[33px] items-start justify-between border-0 border-b border-solid border-line">
    <div class="flex h-full items-start" role="tablist">
      <button
        class="inline-flex h-8 w-[72px] cursor-pointer items-center justify-center whitespace-nowrap border-0 bg-transparent px-2 text-14 font-semibold leading-[22px] hover:bg-transparent"
        :class="modelValue === 'pending' ? 'text-black' : 'text-muted hover:text-regular'"
        type="button"
        role="tab"
        :aria-selected="modelValue === 'pending'"
        @click="emit('update:modelValue', 'pending')"
      >
        待审核的
      </button>
      <button
        class="inline-flex h-8 w-[72px] cursor-pointer items-center justify-center whitespace-nowrap border-0 bg-transparent px-2 text-14 font-semibold leading-[22px] hover:bg-transparent"
        :class="modelValue === 'reviewed' ? 'text-black' : 'text-muted hover:text-regular'"
        type="button"
        role="tab"
        :aria-selected="modelValue === 'reviewed'"
        @click="emit('update:modelValue', 'reviewed')"
      >
        我审核的
      </button>
    </div>
    <div class="flex h-8 items-center">
      <slot />
    </div>
  </div>
</template>
