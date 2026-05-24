import { useState } from "react";
import { Pickaxe, Send, Sparkles } from "lucide-react";
import { TransactionTable } from "../components/blockchain/index.js";
import { WalletSelect } from "../components/wallet/WalletSelect.jsx";
import { GlassPanel, LabeledInput, StatusChip } from "../components/ui/index.js";
import { api } from "../lib/api.js";
import { shortHash } from "../lib/format.js";

function MiningView({ data, wallets, runAction, busy }) {
  const [senderIndex, setSenderIndex] = useState("0");
  const [receiverIndex, setReceiverIndex] = useState("1");
  const [minerIndex, setMinerIndex] = useState("0");
  const [amount, setAmount] = useState("1");
  const [fee, setFee] = useState("0.25");
  const [result, setResult] = useState("Mining results will appear here.");

  const createTransaction = async () => {
    const sender = wallets[Number(senderIndex)];
    const receiver = wallets[Number(receiverIndex)];
    if (!sender || !receiver) throw new Error("Create at least two wallets first.");
    return api.post("/api/transactions", {
      sender: sender.publicKey,
      receiver: receiver.publicKey,
      amount: Number(amount),
      fee: Number(fee),
      privateKey: sender.privateKey
    });
  };

  const minePending = async () => {
    const miner = wallets[Number(minerIndex)];
    if (!miner) throw new Error("Create a miner wallet first.");
    const started = performance.now();
    const block = await api.post("/api/transactions/mine", { rewardAddress: miner.publicKey });
    setResult(`Block #${block.index} mined in ${Math.round(performance.now() - started)} ms with nonce ${block.nonce}.`);
    return block;
  };

  const fundSender = async () => {
    const sender = wallets[Number(senderIndex)];
    if (!sender) throw new Error("Create a sender wallet first.");
    return api.post("/api/blocks", { data: sender.publicKey });
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
      <div className="space-y-4">
        <GlassPanel>
          <h2 className="panel-title mb-4">Transaction Console</h2>
          <form
            className="space-y-3"
            onSubmit={(event) => {
              event.preventDefault();
              runAction(createTransaction, (transaction) => `Transaction queued: ${shortHash(transaction.transactionId)}`);
            }}
          >
            <WalletSelect label="Sender" wallets={wallets} value={senderIndex} onChange={setSenderIndex} />
            <WalletSelect label="Receiver" wallets={wallets} value={receiverIndex} onChange={setReceiverIndex} />
            <div className="grid grid-cols-2 gap-3">
              <LabeledInput label="Amount"><input className="input" type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} /></LabeledInput>
              <LabeledInput label="Fee"><input className="input" type="number" min="0" step="0.01" value={fee} onChange={(event) => setFee(event.target.value)} /></LabeledInput>
            </div>
            <button className="btn-primary w-full" type="submit" disabled={busy}>
              <Send size={18} />
              Submit Transaction
            </button>
          </form>
        </GlassPanel>
        <GlassPanel>
          <h2 className="panel-title mb-4">Mining Hub</h2>
          <div className="space-y-3">
            <WalletSelect label="Reward Address" wallets={wallets} value={minerIndex} onChange={setMinerIndex} />
            <button className="btn-primary w-full" type="button" onClick={() => runAction(minePending, (block) => `Mined block #${block.index}.`)} disabled={busy}>
              <Pickaxe size={18} />
              Mine Pending
            </button>
            <button className="btn-ghost w-full" type="button" onClick={() => runAction(fundSender, (block) => `Demo funding block #${block.index} mined.`)} disabled={busy}>
              <Sparkles size={18} />
              Fund Sender Demo
            </button>
            <p className="rounded-xl border border-white/10 bg-void/50 p-3 text-sm text-muted">{result}</p>
          </div>
        </GlassPanel>
      </div>
      <GlassPanel>
        <div className="mb-5 flex items-center justify-between">
          <div>
            <p className="eyebrow">Mempool</p>
            <h2 className="panel-title">Pending Transactions</h2>
          </div>
          <StatusChip>{data.pending.length} TX</StatusChip>
        </div>
        <TransactionTable transactions={data.pending} />
      </GlassPanel>
    </div>
  );
}

export { MiningView };
