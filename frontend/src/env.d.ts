/// <reference types="vite/client" />

// 让 TypeScript 认识 .vue 单文件组件，解决 "Cannot find module './App.vue'" 之类的报错
declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}

// Vite 环境变量类型提示；变量需以 VITE_ 开头才会注入客户端
interface ImportMetaEnv {
  /** API 基础路径，缺省使用 /api/v1（开发环境由 Vite 代理转发到 8080） */
  readonly VITE_API_BASE?: string;
  readonly VITE_APP_TITLE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
