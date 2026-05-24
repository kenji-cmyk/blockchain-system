import { useCallback, useEffect, useMemo, useState } from "react";
import { Header, Sidebar } from "./components/layout/index.js";
import { Toast } from "./components/ui/index.js";
import { Dashboard, Explorer, MiningView, PeersView, WalletView } from "./views/index.js";
import { api } from "./lib/api.js";
import { useLocalStorage } from "./hooks/useLocalStorage.js";

function App() {
  const [activeView, setActiveView] = useState("dashboard");
  const [data, setData] = useState({
    status: null,
    health: null,
    metrics: null,
    blocks: [],
    pending: [],
    peers: [],
    forks: [],
    orphans: []
  });
  const [wallets, setWallets] = useLocalStorage("luminousWallets", []);
  const [toast, setToast] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const notify = useCallback((message, tone = "success") => {
    setToast({ message, tone, id: Date.now() });
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [status, health, metrics, blocks, pending, peers, forks, orphans] = await Promise.all([
        api.get("/api/chain/status"),
        api.get("/api/ops/health"),
        api.get("/api/ops/metrics"),
        api.get("/api/blocks"),
        api.get("/api/transactions/pending"),
        api.get("/api/peers"),
        api.get("/api/chain/forks"),
        api.get("/api/chain/orphans")
      ]);
      setData({ status, health, metrics, blocks, pending, peers, forks, orphans });
    } catch (error) {
      notify(error.message, "danger");
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const runAction = async (action, successMessage) => {
    setBusy(true);
    try {
      const result = await action();
      if (successMessage) notify(typeof successMessage === "function" ? successMessage(result) : successMessage);
      await refresh();
      return result;
    } catch (error) {
      notify(error.message, "danger");
      return null;
    } finally {
      setBusy(false);
    }
  };

  const derived = useMemo(() => {
    const head = data.blocks.at(-1);
    const latestBlocks = data.blocks.slice(-8).reverse();
    const totalFees = data.pending.reduce((sum, transaction) => sum + Number(transaction.fee || 0), 0);
    const rewardBlocks = data.blocks.filter((block) => block.transactions?.every((tx) => tx.sender === "SYSTEM")).length;
    return { head, latestBlocks, totalFees, rewardBlocks };
  }, [data.blocks, data.pending]);

  const createWallet = () =>
    runAction(
      async () => {
        const wallet = await api.get("/api/wallets/new");
        setWallets((current) => [
          ...current,
          {
            label: `Wallet ${current.length + 1}`,
            publicKey: wallet.publicKey,
            privateKey: wallet.privateKey
          }
        ]);
        return wallet;
      },
      "Wallet created and stored in this browser."
    );

  const resetChain = () => {
    if (!window.confirm("Reset chain, mempool, and peers?")) return Promise.resolve(null);
    return runAction(() => api.post("/api/chain/reset"), "Chain reset complete.");
  };

  const viewProps = { data, wallets, setWallets, derived, busy, runAction, notify, refresh, createWallet };

  return (
    <div className="min-h-screen bg-void text-text">
      <div className="fixed inset-0 -z-10 bg-[radial-gradient(circle_at_18%_10%,rgba(174,248,0,0.11),transparent_27rem),radial-gradient(circle_at_78%_2%,rgba(107,254,156,0.08),transparent_24rem)]" />
      <div className="fixed inset-0 -z-10 bg-grid bg-[length:32px_32px] opacity-40" />
      <div className="flex min-h-screen">
        <Sidebar activeView={activeView} setActiveView={setActiveView} health={data.health} onRefresh={refresh} busy={busy || loading} />
        <main className="min-w-0 flex-1 lg:pl-[260px]">
          <Header
            activeView={activeView}
            valid={data.status?.valid}
            loading={loading}
            onReset={resetChain}
            onRefresh={refresh}
            busy={busy}
          />
          <div className="mx-auto max-w-[1440px] px-4 pb-24 pt-24 sm:px-6 lg:px-8">
            {activeView === "dashboard" && <Dashboard {...viewProps} />}
            {activeView === "explorer" && <Explorer {...viewProps} />}
            {activeView === "wallet" && <WalletView {...viewProps} />}
            {activeView === "mining" && <MiningView {...viewProps} />}
            {activeView === "peers" && <PeersView {...viewProps} />}
          </div>
        </main>
      </div>
      <Toast toast={toast} />
    </div>
  );
}

export default App;
