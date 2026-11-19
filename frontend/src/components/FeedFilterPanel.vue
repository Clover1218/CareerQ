<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { type FeedFilterValue, type FeedPeriod, type FeedSort } from "../api";
import FeedIcon from "./FeedIcon.vue";
import TagPicker from "./TagPicker.vue";

const props = withDefaults(defineProps<{ sort?: FeedSort }>(), { sort: "recommend" });
const emit = defineEmits<{
  change: [value: FeedFilterValue];
  refresh: [];
}>();

const message = useMessage();

const panelOpen = ref(false);
const keyword = ref("");
const tags = ref<string[]>([]);
const period = ref<FeedPeriod>("all");
const range = ref<[number, number] | null>(null);
const sort = ref<FeedSort>(props.sort);
const dateOpen = ref(false);
const composing = ref(false);
const pickingStart = ref<number | null>(null);

let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let lastSignature = "";

const fromText = computed(() => {
  if (pickingStart.value) return formatDate(pickingStart.value);
  return range.value ? formatDate(range.value[0]) : "";
});
const toText = computed(() => {
  if (pickingStart.value) return "";
  return range.value ? formatDate(range.value[1]) : "";
});
const activePeriod = computed<FeedPeriod | "custom">(() => (range.value ? "custom" : period.value));
// 换一换只在没有任何筛选条件时可用
const canRefresh = computed(
  () =>
    !keyword.value.trim() &&
    tags.value.length === 0 &&
    period.value === "all" &&
    range.value === null &&
    pickingStart.value === null,
);

function formatDate(timestamp: number) {
  const date = new Date(timestamp);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function emitChange() {
  const value: FeedFilterValue = {
    q: keyword.value.trim(),
    tags: [...tags.value],
    period: period.value,
    from: range.value ? formatDate(range.value[0]) : null,
    to: range.value ? formatDate(range.value[1]) : null,
    sort: sort.value,
  };
  const signature = JSON.stringify(value);
  if (signature === lastSignature) return;
  lastSignature = signature;
  emit("change", value);
}

function scheduleChange(delay = 350) {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    debounceTimer = null;
    emitChange();
  }, delay);
}

function onKeywordInput() {
  if (composing.value) return;
  scheduleChange();
}

function onCompositionEnd() {
  composing.value = false;
  scheduleChange();
}

function commitKeyword() {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  emitChange();
}

function clearTags() {
  if (tags.value.length === 0) return;
  tags.value = [];
}

function removeTag(name: string) {
  tags.value = tags.value.filter((tag) => tag !== name);
}

function setPeriod(next: FeedPeriod) {
  period.value = next;
  range.value = null;
  pickingStart.value = null;
  dateOpen.value = false;
  emitChange();
}

function onRangeChange(value: number | [number, number] | null) {
  if (!Array.isArray(value)) {
    pickingStart.value = null;
    if (!range.value) return;
    range.value = null;
    emitChange();
    return;
  }
  const [start, end] = value;
  // 日期面板第一次点击就会下发 [start, start]，需要再点一次才算完整区间。
  if (pickingStart.value === null) {
    pickingStart.value = start;
    return;
  }
  pickingStart.value = null;
  const from = Math.min(start, end);
  const to = Math.max(start, end);
  if (to - from > 366 * 86_400_000) {
    message.warning("时间范围不能超过 366 天");
    return;
  }
  range.value = [from, to];
  dateOpen.value = false;
  emitChange();
}

function setSort(next: FeedSort) {
  if (sort.value === next) return;
  sort.value = next;
  emitChange();
}

function refresh() {
  emit("refresh");
}

watch(tags, () => emitChange());
watch(panelOpen, (open) => {
  if (!open) {
    dateOpen.value = false;
    pickingStart.value = null;
  }
});
watch(dateOpen, (open) => {
  if (!open) pickingStart.value = null;
});

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
});
</script>

<template>
  <div class="feed-filter">
    <div class="filter-search-shell" :class="{ 'is-open': panelOpen }">
      <div class="filter-searchbar">
        <label class="filter-search-left">
          <FeedIcon name="search" :size="24" />
          <input
            v-model="keyword"
            type="text"
            maxlength="50"
            autocomplete="off"
            placeholder="搜索问题标题或内容"
            aria-label="搜索问题标题或内容"
            @input="onKeywordInput"
            @keydown.enter="commitKeyword"
            @compositionstart="composing = true"
            @compositionend="onCompositionEnd"
          />
        </label>
        <button
          type="button"
          class="filter-toggle"
          :class="{ 'is-active': panelOpen }"
          :aria-expanded="panelOpen"
          :aria-label="panelOpen ? '收起筛选' : '展开筛选'"
          @click="panelOpen = !panelOpen"
        >
          <FeedIcon name="filter" :size="24" />
        </button>
      </div>

      <div v-show="panelOpen" class="filter-body">
        <TagPicker v-model="tags" :max="10">
          <template #trigger="{ open }">
            <div class="filter-row filter-tag-row">
              <button
                type="button"
                class="filter-tag"
                :class="{ 'is-active': tags.length === 0 }"
                :aria-pressed="tags.length === 0"
                @click="clearTags"
              >
                全部标签
              </button>
              <button
                v-for="name in tags"
                :key="name"
                type="button"
                class="filter-tag is-selected"
                :title="`移除 ${name}`"
                aria-pressed="true"
                @click="removeTag(name)"
              >
                {{ name }}
              </button>
              <button type="button" class="filter-add" @click="open()">
                添加
                <FeedIcon name="plus" :size="16" />
              </button>
            </div>
          </template>
        </TagPicker>

        <n-popover
          :show="dateOpen"
          trigger="manual"
          placement="bottom-start"
          :show-arrow="false"
          :content-style="{ padding: '8px', maxWidth: 'calc(100vw - 32px)', overflowX: 'auto' }"
          :on-clickoutside="() => (dateOpen = false)"
        >
          <template #trigger>
            <div class="filter-row filter-time-row">
              <button
                type="button"
                class="filter-time"
                :class="{ 'is-active': activePeriod === 'all' }"
                @click="setPeriod('all')"
              >
                全部日期
              </button>
              <button
                type="button"
                class="filter-time"
                :class="{ 'is-active': activePeriod === 'day' }"
                @click="setPeriod('day')"
              >
                最近一天
              </button>
              <button
                type="button"
                class="filter-time"
                :class="{ 'is-active': activePeriod === 'week' }"
                @click="setPeriod('week')"
              >
                最近一周
              </button>
              <button
                type="button"
                class="filter-time"
                :class="{ 'is-active': activePeriod === 'month' }"
                @click="setPeriod('month')"
              >
                最近一月
              </button>

              <div class="filter-custom-timer">
                <button
                  type="button"
                  class="filter-date"
                  :class="{ 'has-value': fromText }"
                  @click="dateOpen = true"
                >
                  {{ fromText || "开始日期" }}
                </button>
                <span class="filter-date-sep">至</span>
                <button
                  type="button"
                  class="filter-date"
                  :class="{ 'has-value': toText }"
                  @click="dateOpen = true"
                >
                  {{ toText || "结束日期" }}
                </button>
              </div>
            </div>
          </template>
          <n-date-picker
            type="daterange"
            panel
            :value="range"
            :actions="null"
            :first-day-of-week="1"
            @update:value="onRangeChange"
          />
        </n-popover>
      </div>
    </div>

    <div class="filter-sortbar">
      <div class="filter-sort">
        <span class="filter-sort-label">当前排序：</span>
        <div class="filter-sort-options">
          <button
            type="button"
            class="filter-sort-btn"
            :class="{ 'is-active': sort === 'recommend' }"
            @click="setSort('recommend')"
          >
            个性推荐
          </button>
          <span class="filter-sep">|</span>
          <button
            type="button"
            class="filter-sort-btn"
            :class="{ 'is-active': sort === 'latest' }"
            @click="setSort('latest')"
          >
            最新
          </button>
          <span class="filter-sep">|</span>
          <button
            type="button"
            class="filter-sort-btn"
            :class="{ 'is-active': sort === 'hot' }"
            @click="setSort('hot')"
          >
            热门
          </button>
        </div>
      </div>
      <button v-if="canRefresh" type="button" class="filter-refresh" @click="refresh">
        <FeedIcon name="refresh" :size="20" />
        换一换
      </button>
    </div>
  </div>
</template>

<style scoped>
.feed-filter {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-search-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-search-shell.is-open {
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  background: var(--color-bg-surface);
  padding-bottom: 8px;
  overflow: hidden;
}

.filter-searchbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 40px;
  padding: 8px 16px;
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  background: var(--color-bg-surface);
  box-shadow: 0 4px 16px -8px rgba(0, 0, 0, 0.25);
}

.filter-search-left {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 16px;
  color: var(--color-text-primary);
}

.filter-search-left input {
  flex: 1;
  min-width: 0;
  width: auto;
  border: 0;
  background: none;
  padding: 0;
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 16px;
  line-height: 24px;
}

.filter-search-left input:focus {
  outline: none;
  border: 0;
  box-shadow: none;
}

.filter-search-left input::placeholder {
  color: var(--color-text-muted);
}

.filter-toggle {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 4px;
  background: none;
  padding: 0;
  color: var(--color-text-primary);
}

.filter-toggle:hover {
  background: none;
  color: #3874d0;
}

.filter-toggle.is-active {
  color: #3874d0;
}

.filter-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 4px 8px;
}

.filter-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: none;
  padding: 4px 8px;
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
}

.filter-tag:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.filter-tag.is-active,
.filter-tag.is-selected {
  color: #3874d0;
}

.filter-tag.is-selected:hover {
  color: #3874d0;
  text-decoration: line-through;
}

.filter-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: var(--color-bg-main);
  padding: 4px 8px;
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
}

.filter-add:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.filter-time {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: none;
  padding: 4px 8px;
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
}

.filter-time:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.filter-time.is-active {
  color: #3874d0;
}

.filter-custom-timer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
}

.filter-date {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 90px;
  height: 32px;
  border: 1px solid var(--color-border-default);
  border-radius: 6px;
  background: var(--color-bg-surface);
  padding: 4px 16px;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
}

.filter-date:hover {
  border-color: var(--color-brand);
  background: var(--color-bg-surface);
  color: var(--color-text-regular);
}

.filter-date.has-value {
  color: var(--color-text-primary);
}

.filter-date-sep {
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 22px;
}

.filter-sortbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 16px;
}

.filter-sort {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.filter-sort-label {
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 22px;
}

.filter-sort-options {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-sort-btn {
  border: 0;
  background: none;
  padding: 0;
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
}

.filter-sort-btn:hover {
  background: none;
  color: #3874d0;
}

.filter-sort-btn.is-active {
  color: #5b94f5;
}

.filter-sep {
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 22px;
}

.filter-refresh {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  padding: 0;
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
}

.filter-refresh:hover {
  background: none;
  color: #3874d0;
}

@media (max-width: 640px) {
  .filter-searchbar {
    padding: 8px 12px;
  }

  .filter-sortbar {
    padding: 10px 8px;
  }

  .filter-custom-timer {
    padding: 0;
  }

  .filter-date {
    min-width: 82px;
    padding: 4px 10px;
  }
}
</style>
