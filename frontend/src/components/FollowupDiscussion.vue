<script setup lang="ts">
import type { Answer, Followup, User } from "@/api";
import { canWithdrawAnswer } from "@/composables/useAnswerWithdrawal";

defineProps<{
  followup: Followup;
  replies: Answer[];
  viewer: User | null;
  targetName: string;
  canReply: boolean;
  replyState: "published" | "reviewing" | "pending";
  highlighted: boolean;
  withdrawingAnswerId: number | null;
  likingAnswerId: number | null;
  highlightedAnswerId?: number | null;
  withdrawingFollowupId?: number | null;
}>();
defineEmits<{
  reply: [id: number];
  useful: [id: number];
  like: [id: number];
  report: [id: number];
  withdraw: [answer: Answer];
  withdrawFollowup: [followup: Followup];
}>();

const statusLabels: Record<string, string> = {
  pending_machine: "待审核", pending_human: "待审核", rejected: "审核未通过",
};
</script>

<template>
  <div :id="`followup-${followup.id}`" class="followup-thread" :class="{ highlighted }">
    <div class="followup">
      <div class="followup-heading">
        <strong>学生追问</strong><span>→ {{ targetName }}</span>
        <span v-if="canReply && replyState === 'pending'" class="tag">待你回复</span>
        <span v-else-if="canReply && replyState === 'reviewing'" class="tag">你的回复审核中</span>
      </div>
      <p class="followup-body">{{ followup.status === 'removed' ? '这条追问已撤销或下架。' : followup.body }}</p>
      <span v-if="statusLabels[followup.status]" class="tag">{{ statusLabels[followup.status] }}</span>
      <button
        v-if="viewer?.role === 'student' && viewer.id === followup.author.id && ['published', 'pending_machine', 'pending_human'].includes(followup.status)"
        type="button" class="withdraw-answer" :disabled="withdrawingFollowupId != null"
        @click="$emit('withdrawFollowup', followup)"
      >{{ withdrawingFollowupId === followup.id ? '撤销中…' : '撤销追问' }}</button>
      <button v-if="canReply" type="button" class="link muted" @click="$emit('reply', followup.id)">回答这条追问</button>
    </div>
    <div v-for="reply in replies" :id="`reply-${reply.id}`" :key="reply.id" class="followup-answer" :class="{ highlighted: highlightedAnswerId === reply.id }">
      <div class="answer-head">
        <span>HR · {{ reply.author.nickname }}</span>
        <span v-if="viewer?.id === reply.author.id">（我）</span>
        <span>回复</span>
        <span>{{ reply.createdAt.replace('T', ' ').slice(0, 16) }}</span>
      </div>
      <p class="question-body">{{ reply.body }}</p>
      <span v-if="statusLabels[reply.status]" class="tag">{{ statusLabels[reply.status] }}</span>
      <div class="reply-actions">
        <template v-if="reply.status === 'published'">
          <button v-if="viewer" type="button" @click="$emit('useful', reply.id)">有用（{{ reply.usefulCount }}）</button>
          <button type="button" :aria-pressed="reply.liked" :disabled="likingAnswerId !== null" @click="$emit('like', reply.id)">
            {{ reply.liked ? '已点赞' : '点赞' }}（{{ reply.likeCount }}）
          </button>
          <button type="button" @click="$emit('report', reply.id)">举报</button>
        </template>
        <button v-if="viewer?.role === 'hr' && viewer.id === reply.author.id && canWithdrawAnswer(reply)" type="button" class="withdraw-answer" :disabled="withdrawingAnswerId !== null" @click="$emit('withdraw', reply)">
          {{ withdrawingAnswerId === reply.id ? '撤销中…' : '撤销这条回复' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.followup-thread { scroll-margin-top: 80px; }
.followup-thread + .followup-thread { margin-top: 18px; }
.highlighted { border-radius: 6px; outline: 2px solid #c6d8f4; outline-offset: 5px; background: #f6f9ff; }
.followup-heading { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; }
.followup-body { margin: 6px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.followup-answer { margin: 10px 0 0 14px; padding: 4px 0 4px 12px; border-left: 2px solid #9bc4ac; }
.answer-head, .followup-heading, .question-body { overflow-wrap: anywhere; }
.answer-head, .reply-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.withdraw-answer { margin-left: 8px; color: #9b4141; border-color: #e4c9c9; }
</style>
