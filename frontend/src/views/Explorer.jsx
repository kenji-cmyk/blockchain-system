import { useEffect, useState } from "react";
import { AlertTriangle, Check } from "lucide-react";
import { BlockList, ConflictList } from "../components/blockchain/index.js";
import { ApiState, GlassPanel, LabeledInput, MiniMetric, Segmented } from "../components/ui/index.js";
import { api } from "../lib/api.js";

function Explorer({ data, runAction, busy, loading, loadError, refresh }) {
  const [filter, setFilter] = useState("all");
  const [difficulty, setDifficulty] = useState(data.status?.difficulty || 2);
  const [tamperIndex, setTamperIndex] = useState(1);
  const [tamperData, setTamperData] = useState("manual audit marker");

  useEffect(() => {
    if (data.status?.difficulty !== undefined) setDifficulty(data.status.difficulty);
  }, [data.status?.difficulty]);

  const blocks = data.blocks
    .filter((block) => {
      const hasUserTx = block.transactions?.some((tx) => tx.sender !== "SYSTEM");
      if (filter === "transactions") return hasUserTx;
      if (filter === "rewards") return !hasUserTx;
      return true;
    })
    .slice()
    .reverse();

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
      <GlassPanel>
        <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="eyebrow">Explorer</p>
            <h2 className="panel-title">Canonical Chain</h2>
          </div>
          <Segmented value={filter} onChange={setFilter} options={[["all", "All"], ["rewards", "Rewards"], ["transactions", "Transactions"]]} />
        </div>
        <ApiState loading={loading} error={loadError} empty={!blocks.length} emptyMessage="No blocks match this view." onRetry={refresh}>
          <BlockList blocks={blocks} />
        </ApiState>
      </GlassPanel>
      <div className="space-y-4">
        <GlassPanel>
          <h2 className="panel-title mb-4">Consensus Controls</h2>
          <form
            className="space-y-3"
            onSubmit={(event) => {
              event.preventDefault();
              runAction(() => api.put("/api/chain/difficulty", { difficulty: Number(difficulty) }), `Difficulty set to ${difficulty}.`);
            }}
          >
            <LabeledInput label="Difficulty">
              <input className="input" type="number" min="0" max="6" value={difficulty} onChange={(event) => setDifficulty(event.target.value)} />
            </LabeledInput>
            <button className="btn-primary w-full" type="submit" disabled={busy}>
              <Check size={18} />
              Update Difficulty
            </button>
          </form>
        </GlassPanel>
        <GlassPanel>
          <h2 className="panel-title mb-4">Fork Intelligence</h2>
          <div className="grid grid-cols-2 gap-3">
            <MiniMetric label="Forks" value={data.forks.length} />
            <MiniMetric label="Orphans" value={data.orphans.length} />
          </div>
          <ApiState loading={loading} error={loadError} onRetry={refresh}>
            <ConflictList forks={data.forks} orphans={data.orphans} />
          </ApiState>
        </GlassPanel>
        <GlassPanel>
          <h2 className="panel-title mb-4">Tamper Lab</h2>
          <form
            className="space-y-3"
            onSubmit={(event) => {
              event.preventDefault();
              runAction(() => api.post("/api/chain/tamper", { index: Number(tamperIndex), data: tamperData }), "Tamper marker applied.");
            }}
          >
            <LabeledInput label="Block Index">
              <input className="input" type="number" min="1" value={tamperIndex} onChange={(event) => setTamperIndex(event.target.value)} />
            </LabeledInput>
            <LabeledInput label="Marker">
              <input className="input" value={tamperData} onChange={(event) => setTamperData(event.target.value)} />
            </LabeledInput>
            <button className="btn-danger w-full" type="submit" disabled={busy}>
              <AlertTriangle size={18} />
              Tamper Block
            </button>
          </form>
        </GlassPanel>
      </div>
    </div>
  );
}

export { Explorer };
