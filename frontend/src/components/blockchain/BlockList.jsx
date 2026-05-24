import { EmptyState, StatusChip } from "../ui/index.js";
import { formatNumber, formatTime, shortHash } from "../../lib/format.js";

function BlockList({ blocks, compact = false }) {
  if (!blocks.length) return <EmptyState message="No blocks match this view." />;
  return (
    <div className={`custom-scrollbar grid gap-3 overflow-auto pr-1 ${compact ? "max-h-[410px]" : "max-h-[calc(100vh-220px)]"}`}>
      {blocks.map((block) => (
        <article key={`${block.index}-${block.hash}`} className="grid gap-3 rounded-xl border border-white/10 bg-void/45 p-4 md:grid-cols-[88px_minmax(0,1fr)_auto]">
          <div className="grid min-h-16 place-items-center rounded-lg border border-lime/20 bg-lime/10 text-2xl font-black text-lime">#{block.index}</div>
          <div className="min-w-0">
            <h3 className="font-bold text-white">{block.index === 0 ? "Genesis Block" : "Mined Block"}</h3>
            <p className="hash-text">Hash {block.hash}</p>
            <p className="hash-text">Previous {block.previousHash}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <StatusChip>{block.transactions?.length || 0} tx</StatusChip>
              <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-bold text-muted">Nonce {block.nonce}</span>
              <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-bold text-muted">{formatTime(block.timeStamp)}</span>
            </div>
            <div className="mt-3 space-y-1">
              {(block.transactions || []).slice(0, compact ? 1 : 4).map((transaction) => (
                <p className="hash-text" key={transaction.transactionId}>
                  {transaction.sender === "SYSTEM" ? "SYSTEM" : shortHash(transaction.sender)} -&gt; {shortHash(transaction.receiver)} - {formatNumber(transaction.amount)}
                </p>
              ))}
            </div>
          </div>
          <StatusChip>{block.hash?.startsWith("0") ? "POW" : "CHECK"}</StatusChip>
        </article>
      ))}
    </div>
  );
}

export { BlockList };
