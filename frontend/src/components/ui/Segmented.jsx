function Segmented({ value, onChange, options }) {
  return (
    <div className="inline-flex rounded-full border border-white/10 bg-void/60 p-1">
      {options.map(([id, label]) => (
        <button key={id} type="button" className={`rounded-full px-4 py-2 text-xs font-bold ${value === id ? "bg-lime text-on-lime shadow-glow" : "text-muted hover:text-white"}`} onClick={() => onChange(id)}>
          {label}
        </button>
      ))}
    </div>
  );
}

export { Segmented };
