import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("wallet, mining, and peer views keep expected empty and action states", async () => {
  const [walletView, miningView, peersView] = await Promise.all([
    readFile(new URL("../src/views/WalletView.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/views/MiningView.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/views/PeersView.jsx", import.meta.url), "utf8")
  ]);

  assert.match(walletView, /Create a wallet to submit transactions and collect mining rewards\./);
  assert.match(walletView, /Check Balance/);
  assert.match(miningView, /No pending transactions\./);
  assert.match(miningView, /Fund Sender Demo/);
  assert.match(peersView, /Register a simulated or HTTP peer to test conflict resolution\./);
  assert.match(peersView, /Sync From Peer/);
});

test("API-backed views expose retryable loading wrappers", async () => {
  const viewFiles = await Promise.all([
    "../src/views/Dashboard.jsx",
    "../src/views/Explorer.jsx",
    "../src/views/WalletView.jsx",
    "../src/views/MiningView.jsx",
    "../src/views/PeersView.jsx",
    "../src/views/DetailView.jsx"
  ].map((path) => readFile(new URL(path, import.meta.url), "utf8")));

  for (const source of viewFiles) {
    assert.match(source, /ApiState/);
    assert.match(source, /loading=/);
    assert.match(source, /onRetry=\{refresh\}/);
  }
});
