<script setup lang="ts">
import AdminIcon from "./AdminIcon.vue";

defineProps<{
  nickname?: string | null;
}>();

const emit = defineEmits<{
  home: [];
}>();

function initial(name?: string | null): string {
  return (name ?? "运").trim().slice(0, 1);
}
</script>

<template>
  <aside
    class="sticky top-0 flex h-screen w-60 flex-none flex-col justify-between border-0 border-r border-solid border-line bg-surface p-4"
  >
    <div class="flex flex-col">
      <button
        class="flex h-16 w-[207px] cursor-pointer items-center gap-2.5 border-0 bg-transparent px-4 py-0 hover:bg-transparent"
        type="button"
        aria-label="返回公开问答"
        @click="emit('home')"
      >
        <AdminIcon name="logo" :size="32" />
        <span class="whitespace-nowrap text-24 font-bold leading-8 text-[#5b94f5]">CareerQ</span>
      </button>

      <nav class="flex w-[207px] flex-col gap-1" aria-label="后台导航">
        <button
          class="flex h-[38px] w-full cursor-pointer items-center gap-2 rounded-card border-0 bg-transparent px-4 py-2 text-left text-14 font-normal leading-[22px] text-ink hover:bg-hover"
          type="button"
          @click="emit('home')"
        >
          <AdminIcon name="home" :size="20" />
          <span>主页</span>
        </button>

        <div class="flex flex-col gap-1">
          <div class="flex h-[38px] w-full cursor-default items-center gap-2 px-4 py-2 text-14 leading-[22px] text-ink">
            <AdminIcon name="inbox" :size="20" />
            <span>审核队列</span>
          </div>
          <div class="flex flex-col gap-1 px-10">
            <button
              class="flex h-[38px] w-full cursor-pointer items-center whitespace-nowrap rounded-card border-0 bg-canvas px-4 py-2 text-left text-14 leading-[22px] text-ink hover:bg-canvas"
              type="button"
            >
              <span>问答审核</span>
            </button>
            <button
              class="flex h-[38px] w-full cursor-default items-center whitespace-nowrap rounded-card border-0 bg-transparent px-4 py-2 text-left text-14 leading-[22px] text-ink hover:bg-transparent"
              type="button"
              disabled
            >
              <span>举报处理</span>
            </button>
          </div>
        </div>

        <button
          class="flex h-[38px] w-full cursor-default items-center gap-2 rounded-card border-0 bg-transparent px-4 py-2 text-left text-14 leading-[22px] text-ink hover:bg-transparent"
          type="button"
          disabled
        >
          <AdminIcon name="user" :size="20" />
          <span>用户管理</span>
        </button>
        <button
          class="flex h-[38px] w-full cursor-default items-center gap-2 rounded-card border-0 bg-transparent px-4 py-2 text-left text-14 leading-[22px] text-ink hover:bg-transparent"
          type="button"
          disabled
        >
          <AdminIcon name="message" :size="20" />
          <span>内容管理</span>
        </button>
        <button
          class="flex h-[38px] w-full cursor-default items-center gap-2 rounded-card border-0 bg-transparent px-4 py-2 text-left text-14 leading-[22px] text-ink hover:bg-transparent"
          type="button"
          disabled
        >
          <AdminIcon name="settings" :size="20" />
          <span>基础设置</span>
        </button>
      </nav>
    </div>

    <div class="flex h-[42px] w-[207px] items-center gap-2 px-4 py-2.5">
      <span
        class="inline-flex h-5 w-5 items-center justify-center rounded-full border border-solid border-line bg-hover text-[11px] leading-none text-regular"
        >{{ initial(nickname) }}</span
      >
      <span class="text-14 leading-[22px] text-ink">{{ nickname ?? "运营" }}</span>
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
