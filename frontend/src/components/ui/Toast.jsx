import { Check, X } from "lucide-react";

function Toast({ toast }) {
  if (!toast) return null;
  return (
    <div className={`fixed bottom-5 right-5 z-50 flex max-w-sm items-start gap-3 rounded-xl border p-4 shadow-glass backdrop-blur-2xl ${toast.tone === "danger" ? "border-danger/30 bg-danger/10 text-danger" : "border-lime/30 bg-surface/95 text-text"}`}>
      {toast.tone === "danger" ? <X size={18} /> : <Check size={18} className="text-lime" />}
      <p className="text-sm font-semibold">{toast.message}</p>
    </div>
  );
}

export { Toast };
