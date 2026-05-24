import { FileJson, RefreshCw } from "lucide-react";
import { navItems } from "../../lib/navigation.jsx";

function Sidebar({ activeView, setActiveView, health, onRefresh, busy }) {
  return (
    <aside className="fixed inset-x-0 top-0 z-40 border-b border-white/10 bg-surface/90 backdrop-blur-2xl lg:inset-y-0 lg:left-0 lg:h-screen lg:w-[260px] lg:border-b-0 lg:border-r">
      <div className="flex h-full flex-col gap-4 p-4">
        <div className="flex items-center gap-3 px-2 py-3 lg:block lg:space-y-1 lg:px-3 lg:py-6">
          <div className="grid size-11 place-items-center rounded-2xl bg-lime font-black text-on-lime shadow-glow lg:hidden">LC</div>
          <div>
            <div className="text-xl font-extrabold text-lime">LuminousChain</div>
            <div className="mt-1 text-xs font-semibold text-muted-warm">
              {health?.status || "BOOTING"} - {health?.persistenceEnabled ? health.persistenceType : "memory"} persistence
            </div>
          </div>
        </div>
        <nav className="flex gap-2 overflow-x-auto lg:flex-col lg:overflow-visible">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = activeView === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveView(item.id)}
                className={`nav-button ${active ? "nav-button-active" : ""}`}
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
        <div className="mt-auto hidden space-y-3 lg:block">
          <button type="button" className="btn-primary w-full" onClick={onRefresh} disabled={busy}>
            <RefreshCw size={18} className={busy ? "animate-spin" : ""} />
            Refresh Node
          </button>
          <a className="btn-ghost w-full" href="/api/docs/openapi" target="_blank" rel="noreferrer">
            <FileJson size={18} />
            OpenAPI
          </a>
        </div>
      </div>
    </aside>
  );
}

export { Sidebar };
