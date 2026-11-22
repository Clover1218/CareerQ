<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { api, type AdminReviewEntry, type ReviewTargetType } from "@/api";

const props = defineProps<{
  targetType: ReviewTargetType;
  targetId: number;
  entries: AdminReviewEntry[];
  beforeRemaining: number;
  afterRemaining: number;
  pending: boolean;
}>();

const loaded = ref<AdminReviewEntry[]>([]);
const beforeRemaining = ref(0);
const afterRemaining = ref(0);
const loadingBefore = ref(false);
const loadingAfter = ref(false);
const error = ref("");
const scroller = ref<HTMLElement | null>(null);

const targetTag = computed(() => (props.pending ? "[待审核的]" : "[我审核的]"));
const beforeLabel = computed(() =>
  loadingBefore.value ? "加载中…" : `加载更早的${Math.min(2, beforeRemaining.value)}条`,
);
const afterLabel = computed(() =>
  loadingAfter.value ? "加载中…" : `加载更新的${Math.min(2, afterRemaining.value)}条`,
);

function keyOf(entry: AdminReviewEntry): string {
  return `${entry.kind}:${entry.id}`;
}

function formatTime(value?: string | null): string {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

function reset(entries: AdminReviewEntry[]) {
  loaded.value = entries.slice();
  beforeRemaining.value = props.beforeRemaining;
  afterRemaining.value = props.afterRemaining;
  loadingBefore.value = false;
  loadingAfter.value = false;
  error.value = "";
  void nextTick(scrollTargetIntoView);
}

watch(() => props.entries, reset, { immediate: true });

function scrollTargetIntoView() {
  const box = scroller.value;
  if (!box) return;
  const target = box.querySelector<HTMLElement>('[data-target="true"]');
  if (!target) {
    box.scrollTop = 0;
    return;
  }
  const boxRect = box.getBoundingClientRect();
  const targetRect = target.getBoundingClientRect();
  box.scrollTop += targetRect.top - boxRect.top - (box.clientHeight - targetRect.height) / 2;
}

async function loadOlder() {
  if (loadingBefore.value || beforeRemaining.value <= 0 || loaded.value.length === 0) return;
  const box = scroller.value;
  const previousHeight = box?.scrollHeight ?? 0;
  const previousTop = box?.scrollTop ?? 0;
  loadingBefore.value = true;
  error.value = "";
  try {
    const page = await api.adminContextOlder(
      props.targetType,
      props.targetId,
      keyOf(loaded.value[0]),
    );
    const seen = new Set(loaded.value.map(keyOf));
    loaded.value = [...page.entries.filter((entry) => !seen.has(keyOf(entry))), ...loaded.value];
    beforeRemaining.value = page.remaining;
    await nextTick();
    if (box) box.scrollTop = previousTop + (box.scrollHeight - previousHeight);
  } catch (e: any) {
    error.value = e?.message ?? "加载更早的上下文失败";
  } finally {
    loadingBefore.value = false;
  }
}

async function loadNewer() {
  if (loadingAfter.value || afterRemaining.value <= 0 || loaded.value.length === 0) return;
  loadingAfter.value = true;
  error.value = "";
  try {
    const page = await api.adminContextNewer(
      props.targetType,
      props.targetId,
      keyOf(loaded.value[loaded.value.length - 1]),
    );
    const seen = new Set(loaded.value.map(keyOf));
    loaded.value = [...loaded.value, ...page.entries.filter((entry) => !seen.has(keyOf(entry)))];
    afterRemaining.value = page.remaining;
    await nextTick();
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight;
  } catch (e: any) {
    error.value = e?.message ?? "加载更新的上下文失败";
  } finally {
    loadingAfter.value = false;
  }
}

function retry() {
  error.value = "";
  if (beforeRemaining.value > 0) void loadOlder();
  else if (afterRemaining.value > 0) void loadNewer();
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div
      ref="scroller"
      class="h-[406px] overflow-y-auto rounded-[6px] border border-solid border-black"
    >
      <div class="flex flex-col gap-4">
        <button
          v-if="beforeRemaining > 0"
          class="box-border flex h-[23px] w-full cursor-pointer items-center justify-center border border-dashed border-black bg-transparent p-0 text-14 font-normal leading-[22px] text-black enabled:hover:bg-hover disabled:cursor-not-allowed disabled:opacity-60"
          type="button"
          :disabled="loadingBefore"
          @click="loadOlder"
        >
          {{ beforeLabel }}
        </button>

        <div class="flex flex-col gap-2.5 px-2">
          <article
            v-for="entry in loaded"
            :key="keyOf(entry)"
            class="flex flex-col gap-2"
            :class="entry.target ? 'items-end' : 'items-start'"
            :data-target="entry.target ? 'true' : undefined"
          >
            <header
              class="flex min-h-[22px] w-full items-center gap-2 text-14 leading-[22px]"
              :class="{ 'justify-end': entry.target }"
            >
              <template v-if="entry.target">
                <span class="whitespace-nowrap text-[#5b94f5]">{{ targetTag }}</span>
                <span class="whitespace-nowrap text-muted">{{ formatTime(entry.createdAt) }}</span>
                <span class="whitespace-nowrap text-regular">{{ entry.authorNickname }}</span>
              </template>
              <template v-else>
                <span class="whitespace-nowrap text-regular">{{ entry.authorNickname }}</span>
                <span class="whitespace-nowrap text-muted">{{ formatTime(entry.createdAt) }}</span>
              </template>
            </header>
            <p
              class="m-0 w-full max-w-[500px] whitespace-pre-wrap break-words rounded-card p-2 text-16 font-normal leading-6 text-black"
              :class="entry.target ? 'bg-[#e6f0ff]' : 'bg-hover'"
            >
              {{ entry.body }}
            </p>
          </article>
        </div>

        <button
          v-if="afterRemaining > 0"
          class="box-border flex h-[23px] w-full cursor-pointer items-center justify-center border border-dashed border-black bg-transparent p-0 text-14 font-normal leading-[22px] text-black enabled:hover:bg-hover disabled:cursor-not-allowed disabled:opacity-60"
          type="button"
          :disabled="loadingAfter"
          @click="loadNewer"
        >
          {{ afterLabel }}
        </button>
      </div>
    </div>

    <p v-if="error" class="m-0 flex items-center gap-2 text-12 leading-[18px] text-danger">
      {{ error }}
      <button
        class="cursor-pointer border-0 bg-transparent p-0 text-12 leading-[18px] text-[#3874d0]"
        type="button"
        @click="retry"
      >
        重试
      </button>
    </p>
  </div>
</template>