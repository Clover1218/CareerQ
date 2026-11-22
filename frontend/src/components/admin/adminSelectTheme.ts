import type { GlobalThemeOverrides } from "naive-ui";

export function adminSelectTheme(options: {
  padding: string;
  radius: string;
  textColor?: string;
}): GlobalThemeOverrides {
  return {
    Select: {
      peers: {
        InternalSelection: {
          heightSmall: "32px",
          fontSizeSmall: "14px",
          borderRadius: options.radius,
          color: "#f3f4f6",
          colorActive: "#f3f4f6",
          textColor: options.textColor ?? "#111827",
          placeholderColor: "#9ca3af",
          arrowColor: "#4e5969",
          arrowSize: "12px",
          border: "1px solid transparent",
          borderHover: "1px solid transparent",
          borderActive: "1px solid transparent",
          borderFocus: "1px solid transparent",
          boxShadowHover: "none",
          boxShadowActive: "0 0 0 2px #e6f0ff",
          boxShadowFocus: "0 0 0 2px #e6f0ff",
          paddingSingle: options.padding,
        },
      },
    },
  };
}
