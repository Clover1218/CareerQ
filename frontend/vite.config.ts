import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { NaiveUiResolver } from "unplugin-vue-components/resolvers";

const apiProxyTarget = process.env.CAREERQ_API_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
  base: "/careerq/",
  plugins: [
    vue(),
    // 自动引入 vue / vue-router / pinia 的 API，以及 Naive UI 的 useMessage 等组合式函数
    AutoImport({
      imports: [
        "vue",
        "vue-router",
        "pinia",
        {
          "naive-ui": ["useDialog", "useMessage", "useNotification", "useLoadingBar"],
        },
      ],
      dts: "src/auto-imports.d.ts",
    }),
    // 按需自动注册 Naive UI 组件（模板里直接写 <n-button>，无需手动 import）
    Components({
      resolvers: [NaiveUiResolver()],
      dts: "src/components.d.ts",
    }),
  ],
  resolve: {
    alias: {
      // TS 侧对应配置在 tsconfig.json 的 paths 中
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // Windows 下文件监听事件偶发丢失，开启轮询保证改动后 HMR 生效
    watch: {
      usePolling: true,
      interval: 300,
    },
    proxy: {
      "/api": {
        target: apiProxyTarget,
        changeOrigin: true,
        configure(proxy) {
          proxy.on("proxyReq", (proxyRequest) => {
            proxyRequest.removeHeader("origin");
          });
        },
      },
    },
  },
});
