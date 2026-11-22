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
.followup-thread + .followup-thread { margin-top: 14px; }
.highlighted { border-radius: 8px; outline: 2px solid #c6d8f4; outline-offset: 3px; background: #f6f9ff; }

.followup-thread > .followup {
  margin: 0;
  border: 1px solid var(--color-border-subtle);
  border-left: 3px solid #a8b7ca;
  border-radius: 9px;
  background: #fff;
  padding: 12px 14px;
}

.followup-heading { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.followup-heading strong { color: var(--color-text-primary); font-size: 13px; font-weight: 600; }
.followup-heading > span { color: var(--color-text-muted); font-size: 12px; }
.followup-body { margin: 8px 0 0; color: var(--color-text-regular); font-size: 14px; line-height: 25px; white-space: pre-wrap; overflow-wrap: anywhere; }
.followup-thread > .followup > .tag { display: inline-block; margin-top: 7px; }

.followup-answer {
  margin: 11px 0 0;
  border: 1px solid var(--color-border-subtle);
  border-left: 3px solid var(--color-brand);
  border-radius: 9px;
  background: #fff;
  padding: 12px 14px;
}

.answer-head, .followup-heading, .question-body { overflow-wrap: anywhere; }
.answer-head { color: var(--color-text-muted); font-size: 11px; line-height: 18px; }
.answer-head, .reply-actions { display: flex; flex-wrap: wrap; gap: 7px; }
.followup-answer .question-body { margin: 8px 0 0; color: var(--color-text-regular); font-size: 14px; line-height: 25px; }
.reply-actions { margin-top: 9px; }
.reply-actions button,
.followup-thread .link {
  min-height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  padding: 3px 7px;
  color: var(--color-text-regular);
  font-size: 12px;
}
.reply-actions button:hover,
.followup-thread .link:hover { background: var(--color-brand-soft); color: var(--color-brand); }
.reply-actions .withdraw-answer { margin-left: 0; color: var(--color-danger); }
.followup-thread > .followup > .link { margin-top: 7px; color: var(--color-brand); }
</style>
