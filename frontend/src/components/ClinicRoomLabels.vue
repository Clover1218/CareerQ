<script setup lang="ts">
import { computed } from "vue";
import { clinicRoomLocation, clinicTopics } from "@/utils/clinicTopics";

const props = defineProps<{ tags: string[]; currentRoom?: string }>();
const rooms = computed(() => clinicTopics.filter(topic => props.tags.includes(topic.tag)));
</script>

<template>
  <template v-for="room in rooms" :key="room.id">
    <span v-if="room.id === currentRoom" class="clinic-room-label" :data-room="room.id">
      <span class="clinic-door-icon" aria-hidden="true"></span>{{ room.name }}诊室
    </span>
    <router-link v-else :to="clinicRoomLocation(room)" class="clinic-room-label" :data-room="room.id">
      <span class="clinic-door-icon" aria-hidden="true"></span>{{ room.name }}诊室
    </router-link>
  </template>
</template>
