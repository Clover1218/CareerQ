import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";
import QuestionListView from "./views/QuestionListView.vue";

const LoginView = () => import("./views/LoginView.vue");
const RegisterView = () => import("./views/RegisterView.vue");
const QuestionDetailView = () => import("./views/QuestionDetailView.vue");
const AskView = () => import("./views/AskView.vue");
const MyQaView = () => import("./views/MyQaView.vue");
const HrWorkspaceView = () => import("./views/HrWorkspaceView.vue");
const HrProfileView = () => import("./views/HrProfileView.vue");
const HrMessagesView = () => import("./views/HrMessagesView.vue");
const StudentMessagesView = () => import("./views/StudentMessagesView.vue");
const HrPostsView = () => import("./views/HrPostsView.vue");
const HrPostEditorView = () => import("./views/HrPostEditorView.vue");
const PublicHrSharesView = () => import("./views/PublicHrSharesView.vue");
const PublicHrShareDetailView = () => import("./views/PublicHrShareDetailView.vue");

export const router = createRouter({
  history: createWebHistory("/careerq/"),
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 };
  },
  routes: [
    // 公开页面：未注册也能浏览
    { path: "/", component: QuestionListView },
    { path: "/questions/:id", component: QuestionDetailView },
    { path: "/hr-shares", name: "hr-shares", component: PublicHrSharesView },
    { path: "/hr-shares/:id", name: "hr-share-detail", component: PublicHrShareDetailView },
    // 未注册可进入登录/注册
    { path: "/login", component: LoginView },
    { path: "/register", component: RegisterView },
    // 需要登录：学生问答 / 提问 / HR 工作台
    { path: "/me", name: "my-qa", component: MyQaView, meta: { requiresAuth: true } },
    { path: "/ask", component: AskView, meta: { requiresAuth: true, role: "student" } },
    { path: "/messages", name: "student-messages", component: StudentMessagesView, meta: { requiresAuth: true, role: "student" } },
    { path: "/hr", name: "hr-workbench", component: HrWorkspaceView, meta: { requiresAuth: true, role: "hr" } },
    { path: "/hr/profile", name: "hr-profile", component: HrProfileView, meta: { requiresAuth: true, role: "hr" } },
    { path: "/hr/messages", name: "hr-messages", component: HrMessagesView, meta: { requiresAuth: true, role: "hr" } },
    { path: "/hr/posts", name: "hr-posts", component: HrPostsView, meta: { requiresAuth: true, role: "hr" } },
    { path: "/hr/posts/new", name: "hr-post-new", component: HrPostEditorView, meta: { requiresAuth: true, role: "hr" } },
    { path: "/hr/posts/:id", name: "hr-post-editor", component: HrPostEditorView, meta: { requiresAuth: true, role: "hr" } },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});

const PUBLIC_PATHS = ["/", "/login", "/register"];

router.beforeEach(async (to) => {
  const isPublic = PUBLIC_PATHS.includes(to.path) || to.path.startsWith("/questions/")
    || to.name === "hr-shares" || to.name === "hr-share-detail";
  if (isPublic) {
    return true;
  }

  // 未登录时强制向后端确认一次，避免退出登录后仍停留在受保护页面
  const userStore = useUserStore();
  const user = userStore.user?.role === "student" || !userStore.isAuthenticated
    ? await userStore.fetchUserInfo(true) : userStore.user;
  if (!user) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (user.role === "hr" && to.path === "/me") {
    return { path: "/hr/profile", replace: true };
  }
  if (to.meta.role && user.role !== to.meta.role) {
    return "/";
  }
  return true;
});
