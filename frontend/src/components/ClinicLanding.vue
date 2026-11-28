<script setup lang="ts">
import type { User } from "@/api";
import { clinicRoomLocation, clinicTopics } from "@/utils/clinicTopics";

defineProps<{ viewerRole: User["role"] | null }>();
</script>

<template>
  <section class="hero">
    <div class="hero-copy">
      <h1>职场门诊</h1>
      <p v-if="viewerRole === 'student'">选择一个诊室，查看相关问答或提出你的问题。</p>
      <p v-else-if="viewerRole === null">选择一个诊室查看问答，登录学生账号后可以提问。</p>
      <p v-else>选择一个诊室，查看相关问答。</p>
    </div>
    <div class="scene" aria-hidden="true"><span class="door"></span><span class="sign"><span class="symbol"></span></span><span class="plant"></span></div>
  </section>
  <section id="clinic-rooms" class="clinic-rooms" aria-labelledby="rooms-title">
    <h2 id="rooms-title">选择诊室</h2>
    <p class="rooms-guide">按你现在想解决的问题选。比如找实习时想改简历，就选简历优化诊室。</p>
    <div class="room-grid">
      <router-link v-for="topic in clinicTopics" :key="topic.id" class="room-card" :data-room="topic.id"
        :to="clinicRoomLocation(topic)">
        <span class="room-entrance">
          <span class="room-door">
            <strong class="room-plaque">{{ topic.name }}诊室</strong>
          </span>
        </span>
        <span class="room-hint">{{ topic.hint }}</span>
        <span class="room-example">「{{ topic.example }}」</span>
        <span class="room-action">进入诊室 <span aria-hidden="true">></span></span>
      </router-link>
    </div>
  </section>
</template>

<style scoped>
.hero { position: relative; isolation: isolate; display: grid; grid-template-columns: minmax(0, 1fr) minmax(230px, 36%); min-height: 192px; border: 1px solid #e2cdbf; border-radius: 17px; overflow: hidden; background: #efe1d5; }
.hero-copy { padding: 25px 34px; align-self: center; }
.hero h1 { font-size: clamp(33px, 4.5vw, 54px); font-weight: 800; letter-spacing: -.055em; line-height: 1.18; }
.hero p { max-width: 34em; color: #5c4c42; line-height: 1.65; }
.scene { position: relative; min-height: 100%; overflow: hidden; background: radial-gradient(ellipse at 56% 53%, #fdf0de 0, #ebd3be 72%, #efe1d5 90%); }
.scene::before { content: ""; position: absolute; inset: 0; background: linear-gradient(90deg,#efe1d5,transparent 32%), linear-gradient(0deg,#b07d5718 0 15%, transparent 15%); }
.door { position: absolute; width: 128px; height: 153px; left: 43%; bottom: 15%; border: 6px solid #d8a48c; border-bottom: 0; border-radius: 64px 64px 0 0; background: linear-gradient(105deg,#a75d50,#83473f); box-shadow: 12px 7px 18px #794e3a32, inset 0 0 0 2px #744039; }
.door::before { content: ""; position: absolute; inset: 13px 16px 35px; border-radius: 52px 52px 4px 4px; background: repeating-linear-gradient(90deg,#fff5df16 0 1px,transparent 1px 20px), linear-gradient(155deg,#fbe7c8,#d4a588); border: 2px solid #f3cdb0; }
.door::after { content: ""; position: absolute; width: 15px; height: 4px; border-radius: 3px; background: #f5d7ac; right: 12px; bottom: 25px; }
.sign { position: absolute; left: 14%; top: 28%; display: grid; place-items: center; width: 63px; height: 37px; border-radius: 10px; background: #fff7e9; border: 1px solid #d0aa93; box-shadow: 0 3px 0 #c79e87; transform: rotate(-6deg); color: var(--color-brand); }
.sign .symbol { width: 20px; height: 15px; border: 2px solid; border-radius: 5px; position: relative; }
.sign .symbol::after { content:""; height:5px; width:5px; border-bottom:2px solid; border-left:2px solid; background:#fff7e9; position:absolute; bottom:-4px; left:3px; transform:skewY(-29deg); }
.plant { position: absolute; right: 13%; bottom: 15%; width: 26px; height: 25px; border-radius: 3px 3px 9px 9px; background: #c18362; border-top: 4px solid #dea78a; }
.plant::before,.plant::after { content: ""; position: absolute; bottom: 22px; width: 16px; height: 32px; background: #728570; border-radius: 85% 10% 85% 10%; left: 13px; transform: rotate(21deg); }
.plant::after { left: 0; height: 27px; background: #8e9779; transform: rotate(-55deg); }
.hero h1,.hero p { margin: 0; }
.hero p { margin-top: 8px; }
.clinic-rooms { margin: 24px 0 30px; scroll-margin-top: calc(var(--topbar-height, 85px) + 14px); }
.clinic-rooms h2 { margin: 0 0 4px; font-size: 22px; }
.rooms-guide { margin: 0 0 12px; color: var(--color-text-regular); font-size: 14px; line-height: 1.6; }
.room-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.room-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 15px;
  border: 1px solid var(--color-border-default);
  border-radius: 15px;
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  text-decoration: none;
}
.room-card:hover { border-color: var(--room-accent); }
.room-card:focus-visible { outline: 2px solid var(--room-accent); outline-offset: 3px; }
.room-entrance {
  display: grid;
  justify-items: center;
  align-items: end;
  width: 100%;
  margin-bottom: 5px;
  padding: 12px 6px 13px;
  border-radius: 9px;
  background:
    linear-gradient(to top, #85604323 0 13px, transparent 13px),
    radial-gradient(ellipse at 48% 28%, #fff9ee, var(--room-surface) 78%);
}
.room-door {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  width: min(138px, 100%);
  height: 134px;
  padding: 53px 3px 0;
  border: 4px solid color-mix(in srgb, var(--room-accent) 46%, #e3c2a4);
  border-bottom: 0;
  border-radius: 50% 50% 2px 2px / 47px 47px 2px 2px;
  background: linear-gradient(115deg, color-mix(in srgb, var(--room-accent) 72%, #e3c2a4), var(--room-accent));
  box-shadow: 5px 3px 9px #442d2529, inset 0 0 0 1px #fff4df55;
}
.room-door::before {
  content: "";
  position: absolute;
  inset: 7px 12px auto;
  height: 38px;
  border: 1px solid #ffedccaa;
  border-radius: 50% 50% 3px 3px / 33px 33px 3px 3px;
  background: linear-gradient(135deg, #fff1d1, #dcc4a9);
}
.room-door::after {
  content: "";
  position: absolute;
  right: 8px;
  bottom: 24px;
  width: 10px;
  height: 3px;
  border-radius: 2px;
  background: #f1d199;
  box-shadow: 0 1px 2px #34271a60;
}
.room-plaque {
  padding: 5px 6px;
  border: 1px solid #e4c29e;
  border-radius: 4px;
  background: #fff7e9;
  color: var(--room-accent);
  box-shadow: 0 2px 4px #37221f38;
  font-size: 14px;
  line-height: 1.25;
  white-space: nowrap;
}
.room-hint { color: var(--color-text-primary); font-size: 14px; font-weight: 600; line-height: 1.55; }
.room-example { margin-bottom: 8px; color: var(--color-text-muted); font-size: 13px; line-height: 1.55; }
.room-action { display: flex; justify-content: space-between; width: 100%; margin-top: auto; color: var(--room-accent); font-size: 13px; font-weight: 700; }
.room-card:hover .room-action { text-decoration: underline; text-underline-offset: 3px; }
@media (max-width: 850px) {
  .room-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .hero { grid-template-columns: minmax(0, 1fr) 26%; min-height: 130px; }
  .hero-copy { padding: 13px 14px; }
  .hero h1 { font-size: 32px; }
  .hero p { font-size: 14px; margin-top: 5px; }
  .door { width: 69px; height: 90px; left: 10%; border-width: 4px; }
  .door::before { inset: 8px 8px 20px; }
  .door::after { width: 8px; right: 6px; bottom: 13px; }
  .sign,.plant { display: none; }
  .clinic-rooms { margin: 18px 0 23px; }
  .clinic-rooms h2 { font-size: 19px; }
  .room-grid { gap: 9px; }
  .room-card { padding: 10px; }
  .room-door { height: 119px; padding-top: 49px; }
  .room-plaque { font-size: 12px; }
}
</style>
