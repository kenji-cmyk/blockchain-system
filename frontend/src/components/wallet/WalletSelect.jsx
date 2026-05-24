import { LabeledInput } from "../ui/index.js";
import { shortHash } from "../../lib/format.js";

function WalletSelect({ label, wallets, value, onChange }) {
  return (
    <LabeledInput label={label}>
      <select className="input" value={value} onChange={(event) => onChange(event.target.value)}>
        {wallets.length ? wallets.map((wallet, index) => <option key={wallet.publicKey} value={index}>{wallet.label} - {shortHash(wallet.publicKey)}</option>) : <option value="">Create wallet first</option>}
      </select>
    </LabeledInput>
  );
}

export { WalletSelect };
