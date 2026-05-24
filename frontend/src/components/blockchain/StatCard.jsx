function StatCard({ label, value, note, icon: Icon }) {
  return (
    <article className="glass-card min-h-36 p-5">
      <div className="mb-4 flex items-center justify-between">
        <span className="eyebrow">{label}</span>
        <Icon size={20} className="text-lime" />
      </div>
      <strong className="block truncate text-3xl font-extrabold text-lime">{value}</strong>
      <p className="mt-3 truncate font-mono text-xs text-muted">{note}</p>
    </article>
  );
}

export { StatCard };
