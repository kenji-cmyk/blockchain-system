import { EmptyState } from "../ui/index.js";
import { formatNumber, shortHash } from "../../lib/format.js";
import { detailHash } from "../../lib/routes.js";

function TransactionTable({ transactions }) {
  if (!transactions.length) return <EmptyState message="No pending transactions." />;
  return (
    <div className="custom-scrollbar overflow-x-auto rounded-xl border border-white/10">
      <table className="w-full min-w-[760px] text-left">
        <thead className="bg-white/5 text-xs uppercase tracking-wider text-muted-warm">
          <tr>
            <th className="px-4 py-3">TX ID</th>
            <th className="px-4 py-3">Sender</th>
            <th className="px-4 py-3">Receiver</th>
            <th className="px-4 py-3">Amount</th>
            <th className="px-4 py-3">Fee</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((transaction) => (
            <tr key={transaction.transactionId} className="border-t border-white/10 font-mono text-xs">
              <td className="px-4 py-3">
                <a className="text-lime hover:text-white" href={detailHash("transactions", transaction.transactionId)}>
                  {shortHash(transaction.transactionId)}
                </a>
              </td>
              <td className="px-4 py-3">{shortHash(transaction.sender)}</td>
              <td className="px-4 py-3">{shortHash(transaction.receiver)}</td>
              <td className="px-4 py-3">{formatNumber(transaction.amount)}</td>
              <td className="px-4 py-3">{formatNumber(transaction.fee)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export { TransactionTable };
