<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useSlots, watch, type CSSProperties } from "vue";
import { api, type TagCategory, type TagItem } from "../api";

const props = withDefaults(defineProps<{ modelValue: string[]; max?: number; excludedTag?: string }>(), { max: 5 });
const emit = defineEmits<{ "update:modelValue": [value: string[]] }>();
const slots = useSlots();
const customTrigger = computed(() => Boolean(slots.trigger));

const RECOMMENDED = "推荐";
const MAX_POPOVER_WIDTH = 720;
const POPOVER_VIEWPORT_GAP = 16;
const MIN_POPOVER_HEIGHT = 160;
// naive-ui popover 根节点的 8px 上下内边距 + 6px 与触发元素的间距
const POPOVER_CHROME_HEIGHT = 22;
const message = useMessage();

const visible = ref(false);
const rowRef = ref<HTMLElement | null>(null);
// 弹层比标签行宽时 naive-ui 会改为居中对齐，这里限制为行宽以保证弹层固定从行首展开
const popoverWidth = ref(MAX_POPOVER_WIDTH);
// 弹层高度不能超过标签行上下任一方向的可用空间，否则绝对定位的弹层会撑大文档、触发滚动条
const popoverMaxHeight = ref(MIN_POPOVER_HEIGHT);
const categories = ref<TagCategory[]>([]);
const categoriesLoaded = ref(false);
const categoriesError = ref("");
const activeCategory = ref(RECOMMENDED);
const keyword = ref("");
const appliedKeyword = ref("");
const tags = ref<TagItem[]>([]);
const availableTags = computed(() => tags.value.filter(tag => tag.name !== props.excludedTag));
const draft = ref<string[]>([]);
const loading = ref(false);
const loadError = ref("");

const categoryOptions = computed(() => [{ name: RECOMMENDED, tagCount: 0 }, ...categories.value]);
const remaining = computed(() => Math.max(0, props.max - draft.value.length));
const countHint = computed(() =>
  remaining.value > 0 ? `还可添加 ${remaining.value} 个标签` : `最多选择 ${props.max} 个标签`,
);
const isSearching = computed(() => appliedKeyword.value.length > 0);
const popoverContentStyle = computed<CSSProperties>(() => ({
  padding: "16px",
  maxHeight: `${popoverMaxHeight.value}px`,
  display: "flex",
  flexDirection: "column",
  overflow: "hidden",
}));

let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let requestSeq = 0;

function removeFromModel(name: string) {
  emit(
    "update:modelValue",
    props.modelValue.filter((item) => item !== name),
  );
  draft.value = draft.value.filter((item) => item !== name);
}

watch(visible, (show) => {
  if (!show) return;
  syncPopoverMetrics();
  draft.value = [...props.modelValue];
  keyword.value = "";
  appliedKeyword.value = "";
  activeCategory.value = RECOMMENDED;
  if (!categoriesLoaded.value) void loadCategories();
  void loadTags();
});

function syncPopoverMetrics() {
  const row = rowRef.value;
  if (!row) return;
  popoverWidth.value = Math.min(MAX_POPOVER_WIDTH, row.clientWidth || MAX_POPOVER_WIDTH);
  const rect = row.getBoundingClientRect();
  const spaceAbove = rect.top - POPOVER_VIEWPORT_GAP;
  const spaceBelow = window.innerHeight - rect.bottom - POPOVER_VIEWPORT_GAP;
  const available = Math.max(MIN_POPOVER_HEIGHT, spaceAbove, spaceBelow);
  popoverMaxHeight.value = Math.round(available - POPOVER_CHROME_HEIGHT);
}

function open() {
  visible.value = true;
}

function close() {
  visible.value = false;
}

defineExpose({ open, close });

async function loadCategories() {
  categoriesError.value = "";
  try {
    categories.value = await api.tagCategories();
    categoriesLoaded.value = true;
  } catch (e: any) {
    categoriesError.value = e.message;
  }
}

async function loadTags() {
  const seq = ++requestSeq;
  loading.value = true;
  loadError.value = "";
  try {
    const result = isSearching.value
      ? await api.listTags({ keyword: appliedKeyword.value, limit: 50 })
      : activeCategory.value === RECOMMENDED
        ? await api.listTags({ limit: 12 })
        : await api.listTags({ category: activeCategory.value, limit: 50 });
    if (seq === requestSeq) tags.value = result;
  } catch (e: any) {
    if (seq === requestSeq) loadError.value = e.message;
  } finally {
    if (seq === requestSeq) loading.value = false;
  }
}

function selectCategory(name: string) {
  keyword.value = "";
  appliedKeyword.value = "";
  activeCategory.value = name;
  void loadTags();
}

function applySearch() {
  const next = keyword.value.trim();
  if (next === appliedKeyword.value) return;
  appliedKeyword.value = next;
  void loadTags();
}

watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(applySearch, 300);
});

onMounted(() => {
  window.addEventListener("resize", syncPopoverMetrics);
});

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer);
  window.removeEventListener("resize", syncPopoverMetrics);
});

function toggle(name: string) {
  const index = draft.value.indexOf(name);
  if (index >= 0) {
    draft.value.splice(index, 1);
  } else {
    if (draft.value.length >= props.max) {
      message.warning(`最多选择 ${props.max} 个标签`);
      return;
    }
    draft.value.push(name);
  }
  emit("update:modelValue", [...draft.value]);
}

function isSelected(name: string) {
  return draft.value.includes(name);
}

function isDisabled(tag: TagItem) {
  return !isSelected(tag.name) && draft.value.length >= props.max;
}
</script>

<template>
  <div class="tag-picker">
    <n-popover
      v-model:show="visible"
      :trigger="customTrigger ? 'manual' : 'click'"
      placement="bottom-start"
      :flip="true"
      :show-arrow="false"
      :width="popoverWidth"
      :style="{ maxWidth: '92vw' }"
      :content-style="popoverContentStyle"
      :on-clickoutside="close"
    >
      <template #trigger>
        <div ref="rowRef" :class="customTrigger ? 'tag-picker-anchor' : 'tag-picker-selected'">
          <slot name="trigger" :open="open">
            <span v-for="name in modelValue" :key="name" class="tag-chip" @click.stop>
              {{ name }}
              <button
                type="button"
                class="tag-chip-remove"
                :aria-label="`移除 ${name}`"
                @click.stop="removeFromModel(name)"
              >
                ×
              </button>
            </span>
            <button type="button" class="tag-picker-trigger">+ 添加文章标签</button>
          </slot>
        </div>
      </template>

      <div class="tag-picker-popover">
        <div class="tag-picker-search">
          <n-input
            v-model:value="keyword"
            clearable
            placeholder="请输入标签名称搜索，Enter 立即搜索"
            @keyup.enter="applySearch"
          />
        </div>

        <div class="tag-picker-body">
          <aside class="tag-picker-categories">
            <button
              v-for="option in categoryOptions"
              :key="option.name"
              type="button"
              class="tag-category"
              :class="{ 'is-active': !isSearching && activeCategory === option.name }"
              @click="selectCategory(option.name)"
            >
              {{ option.name }}
            </button>
            <p v-if="categoriesError" class="tag-picker-error">
              {{ categoriesError }}
              <button type="button" class="tag-picker-retry" @click="loadCategories">重试</button>
            </p>
          </aside>

          <section class="tag-picker-panel">
            <h4 class="tag-picker-panel-title">{{ isSearching ? "搜索结果" : "添加标签" }}</h4>
            <div v-if="loading" class="tag-picker-state">加载中…</div>
            <p v-else-if="loadError" class="tag-picker-error">
              {{ loadError }}
              <button type="button" class="tag-picker-retry" @click="loadTags">重试</button>
            </p>
            <p v-else-if="availableTags.length === 0" class="tag-picker-state">
              {{ isSearching ? "没有匹配的标签" : "该分类暂无标签" }}
            </p>
            <div v-else class="tag-picker-chips">
              <button
                v-for="tag in availableTags"
                :key="tag.id"
                type="button"
                class="tag-chip is-option"
                :class="{ 'is-selected': isSelected(tag.name), 'is-disabled': isDisabled(tag) }"
                :disabled="isDisabled(tag)"
                @click="toggle(tag.name)"
              >
                {{ tag.name }}
              </button>
            </div>
            <p v-if="isSearching" class="tag-picker-search-hint">Enter 只触发搜索，不支持自定义标签</p>
          </section>
        </div>

        <div class="tag-picker-footer">
          <span class="tag-picker-count">{{ countHint }}</span>
          <div class="tag-picker-actions">
            <n-button type="primary" size="small" @click="close">完成</n-button>
          </div>
        </div>
      </div>
    </n-popover>
  </div>
</template>

<style scoped>
.tag-picker {
  width: 100%;
}

.tag-picker-selected {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.tag-picker-anchor {
  display: block;
  width: 100%;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: var(--color-bg-hover);
  color: var(--color-text-regular);
  padding: 4px 10px;
  font-family: inherit;
  font-size: 14px;
  line-height: 20px;
  transition:
    background-color 0.15s ease,
    color 0.15s ease,
    opacity 0.15s ease;
}

.tag-chip.is-option {
  cursor: pointer;
}

.tag-chip.is-option:hover {
  color: var(--color-brand);
}

.tag-chip.is-selected {
  background: var(--color-brand);
  color: #fff;
}

.tag-chip.is-selected:hover {
  color: #fff;
}

.tag-chip.is-disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

button.tag-chip-remove {
  border: 0;
  background: none;
  padding: 0;
  color: inherit;
  font-family: inherit;
  font-size: 14px;
  line-height: 1;
}

button.tag-chip-remove:hover {
  background: none;
  color: var(--color-brand);
}

button.tag-picker-trigger {
  border: 1px dashed var(--color-border-default);
  border-radius: 4px;
  background: var(--color-bg-surface);
  color: var(--color-text-regular);
  padding: 4px 10px;
  font-family: inherit;
  font-size: 14px;
  line-height: 20px;
}

button.tag-picker-trigger:hover {
  border-color: var(--color-brand);
  background: var(--color-bg-surface);
  color: var(--color-brand);
}

.tag-picker-search {
  margin-bottom: 12px;
  flex-shrink: 0;
}

.tag-picker-popover {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  width: 100%;
}

.tag-picker-body {
  display: flex;
  flex: 0 1 320px;
  min-height: 96px;
}

.tag-picker-categories {
  width: 156px;
  flex-shrink: 0;
  overflow-y: auto;
  border-right: 1px solid var(--color-border-default);
  padding-right: 16px;
}

button.tag-category {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--color-text-regular);
  padding: 10px 8px;
  text-align: left;
  font-family: inherit;
  font-size: 15px;
  line-height: 22px;
}

button.tag-category:hover {
  background: var(--color-bg-hover);
}

button.tag-category.is-active {
  background: var(--color-bg-hover);
  color: var(--color-brand);
  font-weight: 600;
}

.tag-picker-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding-left: 24px;
}

.tag-picker-panel-title {
  margin: 0 0 12px;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.tag-picker-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tag-picker-state {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 22px;
}

.tag-picker-error {
  margin: 8px 0 0;
  color: var(--color-danger);
  font-size: 13px;
  line-height: 20px;
}

button.tag-picker-retry {
  border: 0;
  background: none;
  padding: 0 4px;
  color: var(--color-brand);
  font-family: inherit;
  font-size: 13px;
}

button.tag-picker-retry:hover {
  background: none;
  color: var(--color-brand-dark);
}

.tag-picker-search-hint {
  margin: 16px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.tag-picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-default);
}

.tag-picker-count {
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 20px;
}

.tag-picker-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 640px) {
  .tag-picker-body {
    flex-direction: column;
    flex-basis: min(320px, 50vh);
  }

  .tag-picker-categories {
    display: flex;
    width: 100%;
    gap: 4px;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: 0;
    border-bottom: 1px solid var(--color-border-default);
    padding-right: 0;
    padding-bottom: 8px;
  }

  button.tag-category {
    width: auto;
    white-space: nowrap;
  }

  .tag-picker-panel {
    padding-top: 12px;
    padding-left: 0;
  }
}
</style>
