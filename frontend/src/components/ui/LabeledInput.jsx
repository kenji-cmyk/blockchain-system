function LabeledInput({ label, children }) {
  return (
    <label className="block">
      <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-muted-warm">{label}</span>
      {children}
    </label>
  );
}

export { LabeledInput };
