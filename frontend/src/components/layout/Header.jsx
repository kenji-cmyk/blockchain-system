import { RefreshCw, RotateCcw } from "lucide-react";
import { navItems } from "../../lib/navigation.jsx";
import { StatusChip } from "../ui/index.js";

function Header({ activeView, valid, loading, onReset, onRefresh, busy }) {
  const title = navItems.find((item) => item.id === activeView)?.label || "Dashboard";
  return (
    <header className="fixed left-0 right-0 top-[78px] z-30 border-b border-white/10 bg-surface/78 px-4 py-3 backdrop-blur-2xl lg:left-[260px] lg:top-0 lg:px-8">
      <div className="mx-auto flex max-w-[1440px] items-center justify-between gap-3">
        <div>
          <p className="eyebrow">Network Control Room</p>
          <h1 className="text-xl font-bold text-white sm:text-2xl">{title}</h1>
        </div>
        <div className="flex items-center gap-2">
          <StatusChip tone={valid ? "success" : "danger"}>{loading ? "SYNCING" : valid ? "CHAIN VALID" : "DEGRADED"}</StatusChip>
          <button type="button" className="icon-button lg:hidden" onClick={onRefresh} disabled={busy}>
            <RefreshCw size={18} className={busy ? "animate-spin" : ""} />
          </button>
          <button type="button" className="btn-danger hidden sm:inline-flex" onClick={onReset} disabled={busy}>
            <RotateCcw size={18} />
            Reset
          </button>
        </div>
      </div>
    </header>
  );
}

export { Header };
