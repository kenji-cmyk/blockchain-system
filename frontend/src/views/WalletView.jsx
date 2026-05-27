import { useState } from "react";
import { CircleDollarSign, Plus, Search } from "lucide-react";
import { ApiState, EmptyState, GlassPanel, LabeledInput } from "../components/ui/index.js";
import { api } from "../lib/api.js";
import { formatNumber } from "../lib/format.js";
import { detailHash } from "../lib/routes.js";

function WalletView({ wallets, setWallets, createWallet, runAction, busy, loading, loadError, refresh }) {
  const [lookupAddress, setLookupAddress] = useState("");
  const [lookupResult, setLookupResult] = useState(null);

  const checkBalance = async (address) => {
    const result = await api.get(`/api/wallets/${encodeURIComponent(address)}/balance`);
    setLookupAddress(address);
    setLookupResult(result);
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
      <GlassPanel>
        <div className="mb-5 flex items-center justify-between gap-4">
          <div>
            <p className="eyebrow">Wallets</p>
            <h2 className="panel-title">Keys and Balances</h2>
          </div>
          <button className="btn-primary" type="button" onClick={createWallet} disabled={busy}>
            <Plus size={18} />
            Create
          </button>
        </div>
        <ApiState loading={loading} error={loadError} onRetry={refresh}>
          <div className="grid gap-3 md:grid-cols-2">
            {wallets.length === 0 ? (
              <EmptyState message="Create a wallet to submit transactions and collect mining rewards." />
            ) : (
              wallets.map((wallet, index) => (
              <article key={wallet.publicKey} className="rounded-xl border border-white/10 bg-void/45 p-4">
                <div className="mb-3 flex items-start justify-between gap-3">
                  <div>
                    <a className="font-bold text-white hover:text-lime" href={detailHash("wallets", wallet.publicKey)}>{wallet.label}</a>
                    <p className="text-xs font-semibold uppercase tracking-wider text-muted">Browser stored keypair</p>
                  </div>
                  <button className="icon-button" type="button" onClick={() => runAction(() => checkBalance(wallet.publicKey), "Balance loaded.")}>
                    <CircleDollarSign size={18} />
                  </button>
                </div>
                <p className="hash-text">{wallet.publicKey}</p>
                <button
                  className="mt-4 text-xs font-bold uppercase tracking-wider text-danger hover:text-white"
                  type="button"
                  onClick={() => setWallets((current) => current.filter((_, itemIndex) => itemIndex !== index))}
                >
                  Remove local wallet
                </button>
              </article>
              ))
            )}
          </div>
        </ApiState>
      </GlassPanel>
      <GlassPanel>
        <h2 className="panel-title mb-4">Balance Lookup</h2>
        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault();
            runAction(() => checkBalance(lookupAddress.trim()), "Balance loaded.");
          }}
        >
          <LabeledInput label="Public Key">
            <textarea className="input min-h-32 resize-y" value={lookupAddress} onChange={(event) => setLookupAddress(event.target.value)} />
          </LabeledInput>
          <button className="btn-ghost w-full" type="submit" disabled={busy || !lookupAddress.trim()}>
            <Search size={18} />
            Check Balance
          </button>
        </form>
        <div className="mt-5 rounded-xl border border-lime/20 bg-lime/5 p-4">
          <p className="eyebrow">Available Balance</p>
          <strong className="mt-2 block text-3xl text-lime">{formatNumber(lookupResult?.balance || 0)}</strong>
          <p className="hash-text mt-3">{lookupResult?.address || "No wallet selected."}</p>
        </div>
      </GlassPanel>
    </div>
  );
}

export { WalletView };
