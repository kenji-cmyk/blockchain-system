import { GlassPanel } from "../ui/index.js";

function NodeMap({ peers }) {
  return (
    <GlassPanel>
      <h2 className="panel-title">Node Topography</h2>
      <p className="mb-6 text-sm text-muted">Real-time peer mesh from the backend.</p>
      <div className="relative grid min-h-80 place-items-center overflow-hidden rounded-xl border border-white/10 bg-void">
        <div className="absolute inset-0 bg-grid bg-[length:24px_24px] opacity-25" />
        <div className="relative grid size-52 place-items-center rounded-full border border-lime/10">
          <div className="absolute inset-4 animate-[spin_20s_linear_infinite] rounded-full border border-lime/10">
            <span className="absolute -top-1 left-1/2 size-2 -translate-x-1/2 rounded-full bg-lime shadow-glow" />
          </div>
          <div className="absolute inset-10 animate-[spin_16s_linear_infinite_reverse] rounded-full border border-lime/10">
            <span className="absolute right-0 top-1/2 size-2 -translate-y-1/2 rounded-full bg-success shadow-glow" />
          </div>
          <div className="z-10 grid size-20 place-items-center rounded-2xl bg-lime font-black text-on-lime shadow-glow">ROOT</div>
        </div>
        <div className="absolute bottom-5 left-5 space-y-2 text-xs font-bold uppercase tracking-wide text-muted">
          {(peers.length ? peers : [{ peerId: "No peers", healthy: false }]).slice(0, 4).map((peer) => (
            <div className="flex items-center gap-2" key={peer.peerId}>
              <span className={`size-2 rounded-full ${peer.healthy ? "bg-lime shadow-glow" : "bg-white/25"}`} />
              {peer.peerId}
            </div>
          ))}
        </div>
      </div>
    </GlassPanel>
  );
}

export { NodeMap };
