<script setup lang="ts">
import { NDatePicker } from "naive-ui";

defineProps<{ value: [number, number] | null }>();
const emit = defineEmits<{ "update:value": [value: number | [number, number] | null] }>();
</script>

<template>
  <NDatePicker
    type="daterange"
    panel
    :value="value"
    :actions="null"
    :first-day-of-week="1"
    @update:value="emit('update:value', $event)"
  />
</template>
