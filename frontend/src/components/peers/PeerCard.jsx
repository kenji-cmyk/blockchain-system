import { api } from "../../lib/api.js";
import { shortHash } from "../../lib/format.js";
import { StatusChip } from "../ui/index.js";

function PeerCard({ peer, runAction }) {
  return (
    <article className="rounded-xl border border-white/10 bg-void/45 p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <h3 className="font-bold text-white">{peer.peerId}</h3>
          <p className="text-xs font-semibold uppercase tracking-wider text-muted">{peer.mode || "simulated"}</p>
        </div>
        <StatusChip tone={peer.valid && peer.healthy ? "success" : "danger"}>{peer.healthy ? "ONLINE" : "CHECK"}</StatusChip>
      </div>
      <p className="hash-text">height {peer.chainSize} - valid {String(peer.valid)}</p>
      <p className="hash-text">{peer.baseUrl || "in-process simulated peer"}</p>
      <button className="mt-4 text-xs font-bold uppercase tracking-wider text-danger hover:text-white" type="button" onClick={() => runAction(() => api.delete(`/api/peers/${encodeURIComponent(peer.peerId)}`), `Removed ${peer.peerId}.`)}>
        Remove peer
      </button>
    </article>
  );
}

export { PeerCard };
