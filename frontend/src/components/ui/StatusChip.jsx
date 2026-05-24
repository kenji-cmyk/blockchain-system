function StatusChip({ children, tone = "success" }) {
  const toneClass = tone === "danger" ? "border-danger/30 bg-danger/10 text-danger" : "border-success/30 bg-success/10 text-success";
  return <span className={`inline-flex min-h-7 items-center rounded-full border px-3 text-xs font-extrabold uppercase tracking-wide ${toneClass}`}>{children}</span>;
}

export { StatusChip };
