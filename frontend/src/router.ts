import { createRouter, createWebHistory } from "vue-router";
import { api } from "./api";
import LoginView from "./views/LoginView.vue";
import RegisterView from "./views/RegisterView.vue";
import QuestionListView from "./views/QuestionListView.vue";
import QuestionDetailView from "./views/QuestionDetailView.vue";
import AskView from "./views/AskView.vue";
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
    // 需要登录：发帖 / 私密工单
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
  try {
    await api.me();
    return true;
  } catch {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
});
