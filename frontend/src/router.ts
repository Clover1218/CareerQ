import { createRouter, createWebHistory } from "vue-router";
import { api } from "./api";
import LoginView from "./views/LoginView.vue";
import RegisterView from "./views/RegisterView.vue";
import QuestionListView from "./views/QuestionListView.vue";
import QuestionDetailView from "./views/QuestionDetailView.vue";
import AskView from "./views/AskView.vue";
import HrQuestionPoolView from "./views/HrQuestionPoolView.vue";
import HrAnswerView from "./views/HrAnswerView.vue";
import ModerationView from "./views/ModerationView.vue";

export const router = createRouter({
  history: createWebHistory("/careerq/"),
  routes: [
    // 公开页面：未注册也能浏览
    { path: "/", component: QuestionListView },
    { path: "/questions/:id", component: QuestionDetailView },
    // 未注册可进入登录/注册
    { path: "/login", component: LoginView },
    { path: "/register", component: RegisterView },
    // 学生工作区
    { path: "/ask", component: AskView, meta: { requiresAuth: true, requiresStudent: true } },
    { path: "/hr/questions", component: HrQuestionPoolView, meta: { requiresAuth: true, requiresHr: true } },
    { path: "/hr/questions/:id/answer", component: HrAnswerView, meta: { requiresAuth: true, requiresHr: true } },
    { path: "/admin/moderation", component: ModerationView, meta: { requiresAuth: true, requiresOperator: true } },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});

const PUBLIC_PATHS = ["/", "/login", "/register"];

router.beforeEach(async (to) => {
  const isPublic = PUBLIC_PATHS.includes(to.path) || to.path.startsWith("/questions/");
  const user = await api.me().catch(() => null);

  if (to.path === "/" && user?.role === "hr") {
    return "/hr/questions";
  }
  if (to.path === "/" && (user?.role === "operator" || user?.role === "admin")) {
    return "/admin/moderation";
  }
  if (isPublic || !to.meta.requiresAuth) return true;
  if (!user) return { path: "/login", query: { redirect: to.fullPath } };
  if (to.meta.requiresHr && user.role !== "hr") return "/";
  if (to.meta.requiresOperator && user.role !== "operator" && user.role !== "admin") return "/";
  if (to.meta.requiresStudent && user.role !== "student") {
    return user.role === "hr" ? "/hr/questions" : "/";
  }
  return true;
});
