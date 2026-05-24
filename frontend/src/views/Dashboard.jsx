import { Activity, ChevronRight, FileJson, HardDrive, KeyRound, Layers3, RefreshCw, ShieldCheck } from "lucide-react";
import { BlockList, NodeMap, StatCard } from "../components/blockchain/index.js";
import { GlassPanel, StatusChip } from "../components/ui/index.js";
import { formatNumber, shortHash } from "../lib/format.js";

function Dashboard({ data, derived, refresh, createWallet, runAction, busy }) {
  const stats = [
    { label: "Block Height", value: data.status?.size ?? "-", note: derived.head ? `Head ${shortHash(derived.head.hash)}` : "Waiting for chain", icon: Layers3 },
    { label: "Difficulty", value: `${data.status?.difficulty ?? "-"} zeros`, note: `Target ${"0".repeat(data.status?.difficulty || 0)}...`, icon: HardDrive },
    { label: "Pending TXs", value: data.pending.length, note: `${formatNumber(derived.totalFees)} total fees`, icon: Activity },
    { label: "Cumulative Work", value: data.status?.cumulativeDifficulty ?? "-", note: `${data.metrics?.peers ?? 0} peers registered`, icon: ShieldCheck }
  ];
  const chartPoints = data.blocks.slice(-10).map((block, index) => ({ x: index * 80, y: 150 - Math.min(110, (block.nonce || 1) % 120) }));
  const path = chartPoints.length ? chartPoints.map((point, index) => `${index === 0 ? "M" : "L"}${point.x} ${point.y}`).join(" ") : "M0 150 L800 120";

  return (
    <div className="space-y-4">
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => (
          <StatCard key={stat.label} {...stat} />
        ))}
      </section>
      <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="space-y-4">
          <GlassPanel>
            <div className="mb-6 flex items-center justify-between gap-4">
              <div>
                <p className="eyebrow">Proof-of-Work Pulse</p>
                <h2 className="panel-title">Mining Nonce Signal</h2>
              </div>
              <StatusChip>{data.health?.status || "UP"}</StatusChip>
            </div>
            <div className="h-64 overflow-hidden rounded-xl border border-white/5 bg-void/50 p-4">
              <svg className="h-full w-full overflow-visible" viewBox="0 0 800 200" preserveAspectRatio="none" role="img" aria-label="Mining nonce chart">
                <defs>
                  <linearGradient id="nonceArea" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stopColor="#aef800" stopOpacity="0.33" />
                    <stop offset="100%" stopColor="#aef800" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <path d="M0 50 L800 50 M0 100 L800 100 M0 150 L800 150" stroke="rgba(255,255,255,.08)" strokeWidth="1" />
                <path d={`${path} L800 190 L0 190 Z`} fill="url(#nonceArea)" />
                <path d={path} fill="none" stroke="#aef800" strokeWidth="4" className="drop-shadow-[0_0_8px_rgba(174,248,0,.6)]" />
              </svg>
            </div>
          </GlassPanel>
          <GlassPanel>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="panel-title">Recent Blocks</h2>
              <span className="text-xs font-bold uppercase tracking-wider text-muted">{data.blocks.length} total</span>
            </div>
            <BlockList blocks={derived.latestBlocks} compact />
          </GlassPanel>
        </div>
        <div className="space-y-4">
          <GlassPanel>
            <h2 className="panel-title mb-5">Quick Controls</h2>
            <div className="space-y-3">
              <button className="control-row" type="button" onClick={createWallet} disabled={busy}>
                <span className="control-icon"><KeyRound size={18} /></span>
                Create Wallet
                <ChevronRight size={18} className="ml-auto text-muted" />
              </button>
              <button className="control-row" type="button" onClick={() => runAction(refresh, "Node refreshed.")} disabled={busy}>
                <span className="control-icon"><RefreshCw size={18} /></span>
                Refresh Node
                <ChevronRight size={18} className="ml-auto text-muted" />
              </button>
              <a className="control-row" href="/api/docs/openapi" target="_blank" rel="noreferrer">
                <span className="control-icon"><FileJson size={18} /></span>
                OpenAPI Spec
                <ChevronRight size={18} className="ml-auto text-muted" />
              </a>
            </div>
          </GlassPanel>
          <NodeMap peers={data.peers} />
        </div>
      </section>
    </div>
  );
}

export { Dashboard };
