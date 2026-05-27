import { useEffect, useState } from "react";
import { Link, Network, Pickaxe, RefreshCw } from "lucide-react";
import { PeerCard } from "../components/peers/PeerCard.jsx";
import { WalletSelect } from "../components/wallet/WalletSelect.jsx";
import { ApiState, GlassPanel, LabeledInput, StatusChip } from "../components/ui/index.js";
import { api } from "../lib/api.js";

function PeersView({ data, wallets, runAction, busy, loading, loadError, refresh }) {
  const [peerId, setPeerId] = useState("node-b");
  const [baseUrl, setBaseUrl] = useState("");
  const [peerUrls, setPeerUrls] = useState("http://localhost:8081");
  const [selectedPeer, setSelectedPeer] = useState("");
  const [minerIndex, setMinerIndex] = useState("0");
  const [syncResult, setSyncResult] = useState("Peer sync results appear here.");

  useEffect(() => {
    if (!selectedPeer && data.peers[0]) setSelectedPeer(data.peers[0].peerId);
  }, [data.peers, selectedPeer]);

  const registerPeer = () => api.post("/api/peers", baseUrl.trim() ? { peerId, baseUrl: baseUrl.trim() } : { peerId });
  const discoverPeers = () => api.post("/api/peers/discover", { peerUrls: peerUrls.split(/\s|,/).map((url) => url.trim()).filter(Boolean) });
  const minePeer = () => {
    const miner = wallets[Number(minerIndex)];
    if (!selectedPeer || !miner) throw new Error("Select a peer and miner wallet first.");
    return api.post(`/api/peers/${encodeURIComponent(selectedPeer)}/blocks`, { minerAddress: miner.publicKey });
  };
  const syncPeer = async () => {
    if (!selectedPeer) throw new Error("Register a peer first.");
    const result = await api.post(`/api/peers/${encodeURIComponent(selectedPeer)}/sync`);
    setSyncResult(`${result.message}. Local ${result.localChainSizeBefore} -> ${result.localChainSizeAfter}. Adopted: ${result.adopted}.`);
    return result;
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
      <div className="space-y-4">
        <GlassPanel>
          <h2 className="panel-title mb-4">Register Peer</h2>
          <form
            className="space-y-3"
            onSubmit={(event) => {
              event.preventDefault();
              runAction(registerPeer, (peer) => `Registered ${peer.peerId}.`);
            }}
          >
            <LabeledInput label="Peer ID"><input className="input" value={peerId} onChange={(event) => setPeerId(event.target.value)} /></LabeledInput>
            <LabeledInput label="Base URL Optional"><input className="input" type="url" placeholder="http://localhost:8081" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} /></LabeledInput>
            <button className="btn-primary w-full" type="submit" disabled={busy}>
              <Link size={18} />
              Register Peer
            </button>
          </form>
        </GlassPanel>
        <GlassPanel>
          <h2 className="panel-title mb-4">Peer Operations</h2>
          <div className="space-y-3">
            <LabeledInput label="Selected Peer">
              <select className="input" value={selectedPeer} onChange={(event) => setSelectedPeer(event.target.value)}>
                {data.peers.length ? data.peers.map((peer) => <option key={peer.peerId}>{peer.peerId}</option>) : <option value="">No peers</option>}
              </select>
            </LabeledInput>
            <WalletSelect label="Miner Wallet" wallets={wallets} value={minerIndex} onChange={setMinerIndex} />
            <button className="btn-ghost w-full" type="button" onClick={() => runAction(minePeer, (block) => `Peer mined block #${block.index}.`)} disabled={busy}>
              <Pickaxe size={18} />
              Mine Peer Block
            </button>
            <button className="btn-ghost w-full" type="button" onClick={() => runAction(syncPeer, "Sync completed.")} disabled={busy}>
              <RefreshCw size={18} />
              Sync From Peer
            </button>
            <p className="rounded-xl border border-white/10 bg-void/50 p-3 text-sm text-muted">{syncResult}</p>
          </div>
        </GlassPanel>
      </div>
      <div className="space-y-4">
        <GlassPanel>
          <h2 className="panel-title mb-4">Discover HTTP Peers</h2>
          <form
            className="flex flex-col gap-3 md:flex-row"
            onSubmit={(event) => {
              event.preventDefault();
              runAction(discoverPeers, (peers) => `Discovered ${peers.length} peers.`);
            }}
          >
            <input className="input" value={peerUrls} onChange={(event) => setPeerUrls(event.target.value)} />
            <button className="btn-primary shrink-0" type="submit" disabled={busy}>
              <Network size={18} />
              Discover
            </button>
          </form>
        </GlassPanel>
        <GlassPanel>
          <div className="mb-5 flex items-center justify-between">
            <div>
              <p className="eyebrow">Mesh</p>
              <h2 className="panel-title">Registered Peers</h2>
            </div>
            <StatusChip>{data.peers.length} peers</StatusChip>
          </div>
          <ApiState loading={loading} error={loadError} empty={!data.peers.length} emptyMessage="Register a simulated or HTTP peer to test conflict resolution." onRetry={refresh}>
            <div className="grid gap-3 md:grid-cols-2">
              {data.peers.map((peer) => <PeerCard key={peer.peerId} peer={peer} runAction={runAction} />)}
            </div>
          </ApiState>
        </GlassPanel>
      </div>
    </div>
  );
}

export { PeersView };
