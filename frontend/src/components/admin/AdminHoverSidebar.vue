<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import AdminIcon from "./AdminIcon.vue";

defineProps<{
  nickname?: string | null;
  avatarUrl?: string | null;
}>();

const emit = defineEmits<{
  home: [];
  review: [];
}>();

const root = ref<HTMLElement | null>(null);
const expanded = ref(true);
const hovering = ref(false);
let collapseTimer: number | undefined;

function initial(name?: string | null): string {
  return (name ?? "运").trim().slice(0, 1) || "运";
}

function onEnter() {
  hovering.value = true;
  expanded.value = true;
}

function onLeave() {
  hovering.value = false;
  expanded.value = false;
}

function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget as Node | null;
  if (!next || !root.value?.contains(next)) expanded.value = false;
}

onMounted(() => {
  collapseTimer = window.setTimeout(() => {
    if (!hovering.value) expanded.value = false;
  }, 80);
});

onBeforeUnmount(() => {
  window.clearTimeout(collapseTimer);
});
</script>

<template>
  <aside
    ref="root"
    class="flex h-screen flex-none flex-col justify-between overflow-hidden border-0 border-r border-solid border-line bg-surface p-4 transition-[width] duration-200 ease-out"
    :class="expanded ? 'w-60' : 'w-[66px]'"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
    @focusin="expanded = true"
    @focusout="onFocusOut"
  >
    <div class="flex w-full flex-col">
      <button
        class="flex h-16 w-full cursor-pointer items-center gap-2.5 overflow-hidden border-0 bg-transparent py-0 transition-[padding] duration-200 ease-out hover:bg-transparent"
        :class="expanded ? 'px-4' : 'px-0'"
        type="button"
        title="返回公开问答"
        aria-label="返回公开问答"
        @click="emit('home')"
      >
        <AdminIcon name="logo" :size="32" />
        <span class="whitespace-nowrap text-24 font-bold leading-8 text-[#5b94f5]">CareerQ</span>
      </button>

      <nav class="flex w-full flex-col gap-1 overflow-hidden" aria-label="后台导航">
        <button
          class="flex h-[38px] w-full cursor-pointer items-center gap-2 overflow-hidden rounded-card border-0 bg-[#e6f0ff] py-2 text-left text-14 font-normal leading-[22px] text-[#3874d0] transition-[padding] duration-200 ease-out hover:bg-[#e6f0ff]"
          :class="expanded ? 'px-4' : 'px-2'"
          type="button"
          @click="emit('home')"
        >
          <AdminIcon name="home" :size="20" />
          <span class="whitespace-nowrap">主页</span>
        </button>

        <div class="flex w-full flex-col gap-1 overflow-hidden">
          <button
            class="flex h-[38px] w-full cursor-pointer items-center gap-2 overflow-hidden rounded-card border-0 bg-transparent py-2 text-left text-14 font-normal leading-[22px] text-ink transition-[padding] duration-200 ease-out hover:bg-hover"
            :class="expanded ? 'px-4' : 'px-2'"
            type="button"
            @click="emit('review')"
          >
            <AdminIcon name="inbox" :size="20" />
            <span class="whitespace-nowrap">审核队列</span>
          </button>
          <div class="flex w-full flex-col gap-1 overflow-hidden px-10">
            <button
              class="flex h-[38px] w-full cursor-pointer items-center overflow-hidden whitespace-nowrap rounded-card border-0 bg-transparent px-4 py-2 text-left text-14 font-normal leading-[22px] text-ink hover:bg-hover"
              type="button"
              @click="emit('review')"
            >
              <span>问答审核</span>
            </button>
            <button
              class="flex h-[38px] w-full cursor-default items-center overflow-hidden whitespace-nowrap rounded-card border-0 bg-transparent px-4 py-2 text-left text-14 font-normal leading-[22px] text-ink hover:bg-transparent"
              type="button"
              disabled
            >
              <span>举报处理</span>
            </button>
          </div>
        </div>

        <button
          class="flex h-[38px] w-full cursor-default items-center gap-2 overflow-hidden rounded-card border-0 bg-transparent py-2 text-left text-14 font-normal leading-[22px] text-ink transition-[padding] duration-200 ease-out hover:bg-transparent"
          :class="expanded ? 'px-4' : 'px-2'"
          type="button"
          disabled
        >
          <AdminIcon name="user" :size="20" />
          <span class="whitespace-nowrap">用户管理</span>
        </button>
        <button
          class="flex h-[38px] w-full cursor-default items-center gap-2 overflow-hidden rounded-card border-0 bg-transparent py-2 text-left text-14 font-normal leading-[22px] text-ink transition-[padding] duration-200 ease-out hover:bg-transparent"
          :class="expanded ? 'px-4' : 'px-2'"
          type="button"
          disabled
        >
          <AdminIcon name="message" :size="20" />
          <span class="whitespace-nowrap">内容管理</span>
        </button>
        <button
          class="flex h-[38px] w-full cursor-default items-center gap-2 overflow-hidden rounded-card border-0 bg-transparent py-2 text-left text-14 font-normal leading-[22px] text-ink transition-[padding] duration-200 ease-out hover:bg-transparent"
          :class="expanded ? 'px-4' : 'px-2'"
          type="button"
          disabled
        >
          <AdminIcon name="settings" :size="20" />
          <span class="whitespace-nowrap">基础设置</span>
        </button>
      </nav>
    </div>

    <div
      class="flex h-[42px] w-full items-center gap-2 overflow-hidden py-2.5 transition-[padding] duration-200 ease-out"
      :class="expanded ? 'px-4' : 'px-2'"
    >
      <span
        class="inline-flex h-5 w-5 flex-none items-center justify-center overflow-hidden rounded-full border border-solid border-line bg-hover text-[11px] leading-none text-regular"
        :title="nickname ?? '运营'"
        :aria-label="nickname ?? '运营'"
      >
        <img v-if="avatarUrl" class="h-full w-full object-cover" :src="avatarUrl" alt="" />
        <template v-else>{{ initial(nickname) }}</template>
      </span>
      <span class="whitespace-nowrap text-14 leading-[22px] text-ink">{{ nickname ?? "运营" }}</span>
      <button
        class="cursor-pointer border-0 bg-transparent p-0 text-14 leading-[22px] text-black hover:bg-transparent hover:text-[#3874d0]"
        type="button"
        title="返回公开问答"
        aria-label="返回公开问答"
        @click="emit('home')"
      >
        ←
      </button>
    </div>
  </aside>
</template>