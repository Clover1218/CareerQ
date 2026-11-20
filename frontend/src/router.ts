import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";
import LoginView from "./views/LoginView.vue";
import RegisterView from "./views/RegisterView.vue";
import QuestionListView from "./views/QuestionListView.vue";
import QuestionDetailView from "./views/QuestionDetailView.vue";
import AskView from "./views/AskView.vue";
import MyQaView from "./views/MyQaView.vue";
import HrWorkspaceView from "./views/HrWorkspaceView.vue";

export const router = createRouter({
  history: createWebHistory("/careerq/"),
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 };
  },
  routes: [
    // 公开页面：未注册也能浏览
    { path: "/", component: QuestionListView },
    { path: "/questions/:id", component: QuestionDetailView },
    // 未注册可进入登录/注册
    { path: "/login", component: LoginView },
    { path: "/register", component: RegisterView },
    // 需要登录：学生问答 / 提问 / HR 工作台
    { path: "/me", name: "my-qa", component: MyQaView, meta: { requiresAuth: true } },
    { path: "/ask", component: AskView, meta: { requiresAuth: true, role: "student" } },
    { path: "/hr", name: "hr-workbench", component: HrWorkspaceView, meta: { requiresAuth: true, role: "hr" } },
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
  if (to.meta.role && user.role !== to.meta.role) {
    return "/";
  }
  return true;
});
