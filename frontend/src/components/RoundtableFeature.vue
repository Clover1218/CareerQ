<script setup lang="ts">
import { computed, onActivated, ref } from "vue";
import { api, type RoundtableFeatured } from "@/api";

const featured = ref<RoundtableFeatured | null>(null);
const discussionLink = computed(() => ({
  path: "/questions/" + featured.value?.questionId,
  query: { from: "feed", focus: "discussion" },
}));

// 首页由 KeepAlive 缓存，回到首页时同步新一期议题和新增回答。
onActivated(async () => {
  try {
    featured.value = (await api.roundtable()).featured ?? null;
  } catch {
    // 公开问题列表有独立的数据源，圆桌暂不可用时仍可正常浏览。
    featured.value = null;
  }
});
</script>

<template>
  <aside class="roundtable-column" aria-label="HR 圆桌">
    <section v-if="featured" class="roundtable-feature" aria-labelledby="featured-title">
      <p class="roundtable-label">本期 HR 圆桌</p>
      <h2 id="featured-title">{{ featured.title }}</h2>
      <div class="roundtable-art">
        <span class="roundtable-disc" aria-hidden="true"></span>
        <span class="roundtable-cup" aria-hidden="true"></span>
        <span class="roundtable-book" aria-hidden="true"></span>
        <span class="roundtable-notes" aria-hidden="true"></span>
        <router-link
          v-for="seat in featured.seats"
          :key="seat.answerId"
          class="roundtable-seat"
          :to="{ path: '/questions/' + featured.questionId, query: { from: 'feed', answer: seat.answerId } }"
          :aria-label="'查看 ' + seat.author.nickname + ' 的回答'"
        >
          <img v-if="seat.author.avatarUrl" :src="seat.author.avatarUrl" alt="" />
          <span v-else class="roundtable-initial" aria-hidden="true">{{ Array.from(seat.author.nickname || 'H')[0] }}</span>
          <span>{{ seat.author.company || seat.author.nickname }}</span>
        </router-link>
      </div>
      <div class="roundtable-footer">
        <span>{{ featured.authorCount ? featured.authorCount + ' 位 HR 参与' : '待回答' }}</span>
        <router-link class="editorial-button" :to="featured.answerCount ? discussionLink : { path: '/questions/' + featured.questionId, query: { from: 'feed' } }">
          {{ featured.answerCount ? '全部 ' + featured.answerCount + ' 条回答' : '查看议题' }} <span aria-hidden="true">→</span>
        </router-link>
      </div>
    </section>
    <section class="roundtable-invite">
      <div><h2>下期想聊什么？</h2><p>为你想看的学生提问投一票</p></div>
      <router-link class="editorial-button" :to="{ name: 'roundtable' }">给话题投票 <span aria-hidden="true">→</span></router-link>
    </section>
  </aside>
</template>

<style scoped>
.roundtable-column { min-width: 0; display: grid; gap: 12px; }
.roundtable-feature { padding: 18px; border: 1px solid #dfcbbb; border-radius: 17px; background: #efe1d5; }
.roundtable-label { margin: 0 0 5px; font-size: 12px; font-weight: 700; color: var(--color-brand-dark); }
.roundtable-feature h2 { margin: 0; font-size: 22px; line-height: 1.35; letter-spacing: -.02em; text-wrap: balance; overflow-wrap: anywhere; }
.roundtable-art { position: relative; isolation: isolate; height: 215px; overflow: hidden; margin: 12px 0; border-radius: 11px; background: radial-gradient(ellipse at 51% 48%, #f9e9d8 0 22%, #efd7c6 64%, #e7cab9 100%); }
.roundtable-art::before { content: ""; position: absolute; inset: 15px; border: 1px solid #6e34251c; border-radius: 48% 38%; transform: rotate(-22deg); }
.roundtable-disc { position: absolute; width: 151px; aspect-ratio: 1; left: calc(50% - 75px); top: 30px; border-radius: 50%; background: repeating-linear-gradient(163deg,#ffffff20 0 3px,#ffffff00 3px 8px,#923d1820 8px 9px,#ffffff00 9px 15px), radial-gradient(circle at 35% 26%,#e9b991,#bf805d); border: 4px solid #f2c59e; box-shadow: 0 5px 0 #a46349, 0 11px 18px #793b3a48, inset 0 0 0 1px #9c5637; }
.roundtable-disc::before { content: ""; position: absolute; inset: 13px; border-radius: 50%; border: 1px solid #92533458; }
.roundtable-disc::after { content: ""; position: absolute; top: 50%; left: calc(50% - 12px); width: 24px; height: 11px; border: 3px solid #92624d; border-bottom: 0; border-radius: 18px 18px 0 0; }
.roundtable-cup, .roundtable-book, .roundtable-notes { position: absolute; z-index: 1; }
.roundtable-cup { top: 91px; left: 40%; width: 17px; height: 17px; border-radius: 50%; background: #835341; border: 3px solid #fff2de; box-shadow: 0 0 0 3px #e9c9af, 0 5px 5px #55291f66; }
.roundtable-book { top: 76px; right: 31%; width: 19px; height: 25px; transform: rotate(22deg); background: #8d4a51; border-radius: 2px; border-right: 3px solid #edcfbc; box-shadow: 1px 3px 3px #532d24a0; }
.roundtable-notes { top: 130px; right: 34%; width: 25px; height: 19px; transform: rotate(-12deg); background: repeating-linear-gradient(#f7e3bf 0 4px, #d2ae84 4px 5px); box-shadow: 1px 2px 2px #532d2450; }
.roundtable-seat { position: absolute; z-index: 2; max-width: 43%; min-height: 32px; display: flex; align-items: center; gap: 5px; padding: 4px 7px; border-radius: 7px; background: #fff9f0; border: 1px solid #d6ae9b; box-shadow: 0 2px 0 #c19784; font-size: 12px; font-weight: 700; color: var(--color-text-primary); }
.roundtable-seat:hover { color: var(--color-brand); background: #fffdf9; }
.roundtable-seat span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.roundtable-seat:nth-of-type(1) { top: 34px; left: 9px; transform: rotate(-5deg); }
.roundtable-seat:nth-of-type(2) { top: 43px; right: 9px; transform: rotate(6deg); }
.roundtable-seat:nth-of-type(3) { bottom: 14px; left: 50%; transform: translateX(-50%); }
.roundtable-seat img, .roundtable-initial { width: 21px; height: 21px; flex: 0 0 21px; border-radius: 50%; object-fit: cover; }
.roundtable-initial { display: grid; place-items: center; background: var(--color-brand-soft); color: var(--color-brand-dark); }
.roundtable-seat:nth-of-type(2) .roundtable-initial { background: var(--color-success-soft); color: var(--color-success); }
.roundtable-seat:nth-of-type(3) .roundtable-initial { background: var(--color-warning-soft); color: var(--color-warning); }
.roundtable-footer, .roundtable-invite { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.roundtable-footer { padding-top: 10px; border-top: 1px solid #d9c0af; font-size: 12px; color: #634d42; }
.roundtable-invite { padding: 13px; background: var(--color-bg-surface); border: 1px solid var(--color-border-default); border-radius: 13px; }
.roundtable-invite h2 { margin: 0; font-size: 14px; }
.roundtable-invite p { margin: 4px 0 0; color: var(--color-text-muted); font-size: 12px; }
@media (max-width: 390px) { .roundtable-invite { padding: 10px; } }
</style>
