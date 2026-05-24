function MiniMetric({ label, value }) {
  return (
    <div className="rounded-xl border border-white/10 bg-void/45 p-4">
      <span className="text-xs font-bold uppercase tracking-wider text-muted">{label}</span>
      <strong className="mt-1 block text-2xl text-lime">{value}</strong>
    </div>
  );
}

export { MiniMetric };
