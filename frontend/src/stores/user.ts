import { computed, ref, watch } from "vue";
import { defineStore } from "pinia";
import { api, type User } from "@/api";
import { resetCsrfToken, setStudentRequestIdentity } from "@/api/request";

const ANONYMOUS_STORAGE_KEY = "careerq:anonymous-name";
const ANONYMOUS_PREFIXES = ["匿名", "低调", "路过", "好奇", "热心"] as const;
const ANONYMOUS_SUFFIXES = ["同学", "学长", "学姐", "求职者", "打工人"] as const;

function pick<T>(list: readonly T[]): T {
  return list[Math.floor(Math.random() * list.length)];
}

function createAnonymousName(): string {
  return `${pick(ANONYMOUS_PREFIXES)}${pick(ANONYMOUS_SUFFIXES)}${Math.floor(1000 + Math.random() * 9000)}`;
}

/** 匿名名落在 localStorage，刷新/重新进入保持同一个身份 */
function loadAnonymousName(): string {
  try {
    const cached = window.localStorage.getItem(ANONYMOUS_STORAGE_KEY);
    if (cached) return cached;
    const name = createAnonymousName();
    window.localStorage.setItem(ANONYMOUS_STORAGE_KEY, name);
    return name;
  } catch {
    // 无痕模式下 localStorage 不可写，退回内存态随机名
    return createAnonymousName();
  }
}

/**
 * 用户会话 Store（Setup Store 写法）。
 * 登录态的唯一来源：组件与路由守卫都从这里读，不再各自调 api.me()。
 */
export const useUserStore = defineStore("user", () => {
  const user = ref<User | null>(null);
  const loading = ref(false);
  const anonymousName = ref(loadAnonymousName());

  // 是否已经从后端同步过状态，用于避免每次路由跳转都请求 /me
  let initialized = false;
  let epoch = 0;
  let pending: Promise<User | null> | null = null;
  watch(user, value => setStudentRequestIdentity(value?.role === "student" ? value.id : null), { immediate: true, flush: "sync" });

  const userId = computed<number | null>(() => user.value?.id ?? null);

  const role = computed(() => user.value?.role ?? null);

  const isAuthenticated = computed(() => user.value !== null);
  const nickname = computed(() => user.value?.nickname ?? anonymousName.value);

  async function fetchUserInfo(force = false): Promise<User | null> {
    if (pending) return pending;
    if (!force && initialized) return user.value;
    const current = epoch;
    loading.value = true;
    pending = (async () => {
      try {
        const result = await api.me();
        if (current !== epoch) return user.value;
        user.value = result;
        initialized = true;
        return result;
      } catch {
        if (current !== epoch) return user.value;
        user.value = null;
        initialized = true;
        return null;
      } finally {
        if (current === epoch) { loading.value = false; pending = null; }
      }
    })();
    return pending;
  }

  async function syncSession() {
    ++epoch;
    pending = null;
    resetCsrfToken();
    return fetchUserInfo(true);
  }

  async function login(phone: string, code: string): Promise<User | null> {
    loading.value = true;
    try {
      ++epoch;
      pending = null;
      await api.login(phone, code);
      // 登录会轮换会话 cookie，必须强制重新拉取用户信息
      return await fetchUserInfo(true);
    } finally {
      loading.value = false;
    }
  }

  async function logout(): Promise<void> {
    try {
      await api.logout();
    } finally {
      // 即使服务端登出失败，本地也要清空登录态
      ++epoch;
      pending = null;
      user.value = null;
      initialized = true;
    }
  }

  return {
    user,
    userId,
    role,
    isAuthenticated,
    anonymousName,
    nickname,
    loading,
    fetchUserInfo,
    syncSession,
    login,
    logout,
  };
});
