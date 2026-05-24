import { EmptyState } from "../ui/index.js";
import { shortHash } from "../../lib/format.js";

function ConflictList({ forks, orphans }) {
  const items = [...forks.map((block) => ["Fork", block]), ...orphans.map((block) => ["Orphan", block])];
  if (!items.length) return <EmptyState message="No forks or orphan blocks tracked." />;
  return (
    <div className="mt-3 space-y-2">
      {items.map(([type, block]) => (
        <div className="rounded-lg border border-white/10 bg-void/45 p-3 font-mono text-xs text-muted" key={`${type}-${block.hash}`}>
          {type} #{block.index} - {shortHash(block.hash)}
        </div>
      ))}
    </div>
  );
}

export { ConflictList };
