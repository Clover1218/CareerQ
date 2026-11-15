/** @type {import('tailwindcss').Config} */
export default {
  // 只扫描业务代码，避免把 naive-ui 内部类名纳入生成
  content: ["./index.html", "./src/**/*.{vue,ts,tsx}"],
  corePlugins: {
    // 关闭 preflight：它会把 naive-ui 依赖的默认样式（margin、border、button 外观等）重置掉
    preflight: false,
  },
  theme: {
    extend: {
      colors: {
        // 与 style.css 里的品牌色保持同一套变量
        primary: {
          DEFAULT: "var(--primary)",
          dark: "var(--primary-dark)",
        },
      },
    },
  },
  plugins: [],
};
