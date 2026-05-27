import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { ApiState, GlassPanel, MiniMetric, StatusChip } from "../components/ui/index.js";
import { api } from "../lib/api.js";
import { detailHash, routeToHash } from "../lib/routes.js";
import { formatNumber, formatTime, shortHash } from "../lib/format.js";

function DetailView({ route, data, wallets, loading, loadError, refresh }) {
  const [remote, setRemote] = useState({ loading: false, error: "", value: null });
  const { detailType, detailId } = route;

  const block = useMemo(
    () => (detailType === "blocks" ? data.blocks.find((item) => String(item.index) === String(detailId)) : null),
    [data.blocks, detailId, detailType]
  );
  const transaction = useMemo(() => {
    if (detailType !== "transactions") return null;
    return [...data.pending, ...data.blocks.flatMap((item) => item.transactions || [])].find((item) => item.transactionId === detailId);
  }, [data.blocks, data.pending, detailId, detailType]);
  const peer = useMemo(
    () => (detailType === "peers" ? data.peers.find((item) => item.peerId === detailId) : null),
    [data.peers, detailId, detailType]
  );
  const localWallet = useMemo(
    () => (detailType === "wallets" ? wallets.find((item) => item.publicKey === detailId) : null),
    [wallets, detailId, detailType]
  );

  const loadRemote = useCallback(async () => {
    if (!detailType || !detailId) return;
    if (detailType !== "wallets" && detailType !== "peers") return;
    setRemote({ loading: true, error: "", value: null });
    try {
      const value =
        detailType === "wallets"
          ? await api.get(`/api/wallets/${encodeURIComponent(detailId)}/balance`)
          : await api.get(`/api/peers/${encodeURIComponent(detailId)}/health`);
      setRemote({ loading: false, error: "", value });
    } catch (error) {
      setRemote({ loading: false, error: error.message, value: null });
    }
  }, [detailId, detailType]);

  useEffect(() => {
    loadRemote();
  }, [loadRemote]);

  const backHash = detailType === "wallets" ? routeToHash("wallet") : detailType === "peers" ? routeToHash("peers") : routeToHash("explorer");
  const title = {
    blocks: "Block Detail",
    transactions: "Transaction Detail",
    wallets: "Wallet Detail",
    peers: "Peer Detail"
  }[detailType] || "Detail";

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <a className="btn-ghost w-fit" href={backHash}>
          <ArrowLeft size={18} />
          Back
        </a>
        <button className="btn-primary w-fit" type="button" onClick={refresh} disabled={loading}>
          <RefreshCw size={18} className={loading ? "animate-spin" : ""} />
          Refresh
        </button>
      </div>
      <GlassPanel>
        <div className="mb-5">
          <p className="eyebrow">{title}</p>
          <h2 className="panel-title break-words">{shortHash(detailId)}</h2>
        </div>
        <ApiState loading={loading} error={loadError} onRetry={refresh}>
          {detailType === "blocks" && <BlockDetail block={block} />}
          {detailType === "transactions" && <TransactionDetail transaction={transaction} />}
          {detailType === "wallets" && <WalletDetail wallet={localWallet} address={detailId} remote={remote} retry={loadRemote} />}
          {detailType === "peers" && <PeerDetail peer={peer} remote={remote} retry={loadRemote} />}
        </ApiState>
      </GlassPanel>
    </div>
  );
}

function BlockDetail({ block }) {
  if (!block) return <ApiState empty emptyMessage="Block not found on the canonical chain." />;
  return (
    <div className="space-y-5">
      <div className="grid gap-3 md:grid-cols-4">
        <MiniMetric label="Index" value={`#${block.index}`} />
        <MiniMetric label="Transactions" value={block.transactions?.length || 0} />
        <MiniMetric label="Nonce" value={block.nonce} />
        <MiniMetric label="Mined" value={formatTime(block.timeStamp)} />
      </div>
      <HashRow label="Hash" value={block.hash} />
      <HashRow label="Previous Hash" value={block.previousHash} />
      <div className="space-y-3">
        {(block.transactions || []).map((transaction) => (
          <TransactionSummary key={transaction.transactionId} transaction={transaction} />
        ))}
      </div>
    </div>
  );
}

function TransactionDetail({ transaction }) {
  if (!transaction) return <ApiState empty emptyMessage="Transaction not found in pending transactions or mined blocks." />;
  return (
    <div className="space-y-5">
      <div className="grid gap-3 md:grid-cols-3">
        <MiniMetric label="Amount" value={formatNumber(transaction.amount)} />
        <MiniMetric label="Fee" value={formatNumber(transaction.fee)} />
        <MiniMetric label="Inputs" value={transaction.inputs?.length || 0} />
      </div>
      <HashRow label="Transaction ID" value={transaction.transactionId} />
      <HashRow label="Sender" value={transaction.sender} />
      <HashRow label="Receiver" value={transaction.receiver} />
      <HashRow label="Signature" value={transaction.signature || "Unsigned reward transaction"} />
    </div>
  );
}

function WalletDetail({ wallet, address, remote, retry }) {
  return (
    <ApiState loading={remote.loading} error={remote.error} onRetry={retry}>
      <div className="space-y-5">
        <div className="grid gap-3 md:grid-cols-2">
          <MiniMetric label="Available Balance" value={formatNumber(remote.value?.balance || 0)} />
          <MiniMetric label="Local Keypair" value={wallet ? wallet.label : "Not stored"} />
        </div>
        <HashRow label="Public Key" value={address} />
      </div>
    </ApiState>
  );
}

function PeerDetail({ peer, remote, retry }) {
  if (!peer) return <ApiState empty emptyMessage="Peer not found in the local registry." />;
  const healthy = remote.value?.healthy ?? peer.healthy;
  return (
    <ApiState loading={remote.loading} error={remote.error} onRetry={retry}>
      <div className="space-y-5">
        <div className="grid gap-3 md:grid-cols-4">
          <MiniMetric label="Height" value={peer.chainSize} />
          <MiniMetric label="Mode" value={peer.mode || "simulated"} />
          <MiniMetric label="Score" value={peer.score ?? 0} />
          <MiniMetric label="Failures" value={peer.failureCount ?? 0} />
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusChip tone={peer.valid ? "success" : "danger"}>{peer.valid ? "valid" : "invalid"}</StatusChip>
          <StatusChip tone={healthy ? "success" : "danger"}>{healthy ? "healthy" : "check"}</StatusChip>
        </div>
        <HashRow label="Peer ID" value={peer.peerId} />
        <HashRow label="Node ID" value={peer.nodeId || "No remote node identity reported."} />
        <HashRow label="Base URL" value={peer.baseUrl || "In-process simulated peer"} />
        <HashRow label="Capabilities" value={(peer.capabilities || []).join(", ") || "None reported"} />
      </div>
    </ApiState>
  );
}

function TransactionSummary({ transaction }) {
  return (
    <a className="block rounded-xl border border-white/10 bg-void/45 p-4 hover:border-lime/40" href={detailHash("transactions", transaction.transactionId)}>
      <div className="mb-2 flex flex-wrap items-center gap-2">
        <StatusChip>{transaction.sender === "SYSTEM" ? "reward" : "transfer"}</StatusChip>
        <span className="font-mono text-xs text-muted">{shortHash(transaction.transactionId)}</span>
      </div>
      <p className="hash-text">{transaction.sender === "SYSTEM" ? "SYSTEM" : shortHash(transaction.sender)} -&gt; {shortHash(transaction.receiver)}</p>
      <p className="mt-2 text-sm font-bold text-white">{formatNumber(transaction.amount)} plus {formatNumber(transaction.fee)} fee</p>
    </a>
  );
}

function HashRow({ label, value }) {
  return (
    <div>
      <p className="eyebrow">{label}</p>
      <p className="mt-2 break-all rounded-xl border border-white/10 bg-void/45 p-3 font-mono text-xs text-muted">{value}</p>
    </div>
  );
}

export { DetailView };
