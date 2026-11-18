import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";
import LoginView from "./views/LoginView.vue";
import RegisterView from "./views/RegisterView.vue";
import QuestionListView from "./views/QuestionListView.vue";
import QuestionDetailView from "./views/QuestionDetailView.vue";
import AskView from "./views/AskView.vue";
import MyQaView from "./views/MyQaView.vue";
import PrivatePoolView from "./views/PrivatePoolView.vue";

export const router = createRouter({
  history: createWebHistory("/careerq/"),
  routes: [
    // 公开页面：未注册也能浏览
    { path: "/", component: QuestionListView },
    { path: "/questions/:id", component: QuestionDetailView },
    // 未注册可进入登录/注册
    { path: "/login", component: LoginView },
    { path: "/register", component: RegisterView },
    // 需要登录：我的问答 / 发帖 / 私密工单
    { path: "/me", name: "my-qa", component: MyQaView, meta: { requiresAuth: true } },
    { path: "/ask", component: AskView, meta: { requiresAuth: true } },
    { path: "/hr/private-pool", component: PrivatePoolView, meta: { requiresAuth: true } },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});

const PUBLIC_PATHS = ["/", "/login", "/register"];

router.beforeEach(async (to) => {
  const isPublic = PUBLIC_PATHS.includes(to.path) || to.path.startsWith("/questions/");
  if (isPublic) {
    return true;
  }

  // 未登录时强制向后端确认一次，避免退出登录后仍停留在受保护页面
  const userStore = useUserStore();
  const user = userStore.isAuthenticated ? userStore.user : await userStore.fetchUserInfo(true);
  if (!user) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  return true;
});
