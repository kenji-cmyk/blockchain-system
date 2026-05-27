import { AlertTriangle, RefreshCw } from "lucide-react";
import { EmptyState } from "./EmptyState.jsx";

function ApiState({ loading, error, empty, emptyMessage, onRetry, children }) {
  if (loading) {
    return (
      <div className="rounded-xl border border-white/10 bg-white/5 p-6 text-sm font-semibold text-muted">
        Loading node data...
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-danger/30 bg-danger/10 p-5 text-sm text-danger">
        <div className="flex items-start gap-3">
          <AlertTriangle className="mt-0.5 shrink-0" size={18} />
          <div className="min-w-0 flex-1">
            <p className="font-bold">Request failed</p>
            <p className="mt-1 break-words text-danger/85">{error}</p>
          </div>
        </div>
        {onRetry && (
          <button className="btn-ghost mt-4 w-full" type="button" onClick={onRetry}>
            <RefreshCw size={18} />
            Retry
          </button>
        )}
      </div>
    );
  }

  if (empty) return <EmptyState message={emptyMessage || "No data available."} />;

  return children;
}

export { ApiState };
