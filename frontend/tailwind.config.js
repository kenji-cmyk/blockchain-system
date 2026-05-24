export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        background: "#10141a",
        void: "#0a0e14",
        surface: "#10141a",
        "surface-low": "#181c22",
        "surface-mid": "#1c2026",
        "surface-high": "#262a31",
        "surface-highest": "#31353c",
        lime: "#aef800",
        "lime-dim": "#98da00",
        "on-lime": "#131f00",
        text: "#dfe2eb",
        muted: "#94a3b8",
        "muted-warm": "#c1caad",
        success: "#6bfe9c",
        danger: "#ffb4ab"
      },
      fontFamily: {
        sans: ["Plus Jakarta Sans", "ui-sans-serif", "system-ui"],
        mono: ["JetBrains Mono", "ui-monospace", "SFMono-Regular", "monospace"]
      },
      boxShadow: {
        glow: "0 0 26px rgba(174, 248, 0, 0.24)",
        glass: "inset 0 1px 0 rgba(255,255,255,0.06), 0 18px 48px rgba(0,0,0,0.28)"
      },
      backgroundImage: {
        grid: "radial-gradient(circle at 1px 1px, rgba(174,248,0,0.12) 1px, transparent 0)"
      }
    }
  },
  plugins: []
};
