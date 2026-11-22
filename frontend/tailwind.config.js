/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{vue,ts,tsx}"],
  corePlugins: {
    // 关闭 preflight：它会把 naive-ui 依赖的默认样式（margin、border、button 外观等）重置掉
    preflight: false,
  },
  theme: {
    extend: {
      // 颜色全部指向 style.css 中的 CSS 变量，一处改全局生效
      colors: {
        brand: {
          DEFAULT: "var(--color-brand)",
          dark: "var(--color-brand-dark)",
          soft: "var(--color-brand-soft)",
          line: "var(--color-brand-border)",
        },
        ink: "var(--color-text-primary)",
        regular: "var(--color-text-regular)",
        muted: "var(--color-text-muted)",
        faint: "var(--color-text-subtle)",
        surface: "var(--color-bg-surface)",
        canvas: "var(--color-bg-main)",
        hover: "var(--color-bg-hover)",
        line: "var(--color-border-default)",
        "line-subtle": "var(--color-border-subtle)",
        danger: "var(--color-danger)",
      },
      // 与 Figma 的 MiSans 文字样式保持一致，缺字体时落到系统中文字体
      fontFamily: {
        sans: ['"MiSans"', '"PingFang SC"', '"Microsoft YaHei"', "system-ui", "sans-serif"],
      },
      // CareerQ/12-Small、14-Regular、16-Regular、18-SemiBold、24-Bold
      fontSize: {
        12: ["12px", { lineHeight: "18px" }],
        14: ["14px", { lineHeight: "22px" }],
        16: ["16px", { lineHeight: "24px" }],
        18: ["18px", { lineHeight: "26px" }],
        24: ["24px", { lineHeight: "32px" }],
      },
      borderRadius: {
        card: "6px",
        "card-lg": "8px",
      },
      // Figma 卡片抬升效果：0 4 16 -8 rgba(0,0,0,.25)
      boxShadow: {
        float: "0 4px 16px -8px rgba(0, 0, 0, 0.25)",
      },
      maxWidth: {
        feed: "720px",
      },
    },
  },
  plugins: [],
};
