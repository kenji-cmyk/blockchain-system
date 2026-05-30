import assert from "node:assert/strict";
import test from "node:test";

let chromium;
try {
  ({ chromium } = await import("playwright"));
} catch {
  test("Playwright critical browser flow", { skip: "playwright package is not installed in this workspace" }, () => {});
}

if (chromium && process.env.RUN_PLAYWRIGHT_E2E !== "true") {
  test("Playwright critical browser flow", { skip: "set RUN_PLAYWRIGHT_E2E=true with a running frontend preview to execute" }, () => {});
}

if (chromium && process.env.RUN_PLAYWRIGHT_E2E === "true") {
  test("Playwright critical browser flow", async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage();
    const apiState = {
      wallets: [],
      pending: [],
      blocks: [{ index: 0, hash: "genesis", previousHash: "0", transactions: [], nonce: 0 }],
      peers: []
    };

    await page.route("**/api/**", async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const method = request.method();

      if (method === "GET" && url.pathname === "/api/blocks") {
        return json(route, apiState.blocks);
      }
      if (method === "GET" && url.pathname === "/api/transactions/pending") {
        return json(route, apiState.pending);
      }
      if (method === "GET" && url.pathname === "/api/peers") {
        return json(route, apiState.peers);
      }
      if (method === "GET" && url.pathname === "/api/chain/status") {
        return json(route, { size: apiState.blocks.length, difficulty: 0, pendingTransactions: apiState.pending.length, valid: true });
      }
      if (method === "GET" && url.pathname === "/api/chain/forks") {
        return json(route, []);
      }
      if (method === "GET" && url.pathname === "/api/chain/orphans") {
        return json(route, []);
      }
      if (method === "GET" && url.pathname === "/api/ops/metrics") {
        return json(route, { chainSize: apiState.blocks.length, pendingTransactions: apiState.pending.length, peers: apiState.peers.length });
      }
      if (method === "GET" && url.pathname === "/api/wallets/new") {
        const wallet = { publicKey: `wallet-${apiState.wallets.length + 1}`, privateKey: `private-${apiState.wallets.length + 1}` };
        apiState.wallets.push(wallet);
        return json(route, wallet);
      }
      if (method === "POST" && url.pathname === "/api/blocks") {
        const block = nextBlock(apiState, []);
        return json(route, block, 201);
      }
      if (method === "POST" && url.pathname === "/api/transactions") {
        const transaction = { transactionId: "tx-1", sender: "wallet-1", receiver: "wallet-2", amount: 1, fee: 0.25 };
        apiState.pending.push(transaction);
        return json(route, transaction, 201);
      }
      if (method === "POST" && url.pathname === "/api/transactions/mine") {
        const block = nextBlock(apiState, apiState.pending.splice(0));
        return json(route, block, 201);
      }

      return json(route, {});
    });

    await page.goto("http://127.0.0.1:4173/#/wallets");
    await page.getByRole("button", { name: /Create/i }).click();
    await page.getByRole("button", { name: /Create/i }).click();
    await page.getByRole("link", { name: /Mining/i }).click();
    await page.getByRole("button", { name: /Fund Sender Demo/i }).click();
    await page.getByRole("button", { name: /Submit Transaction/i }).click();
    await page.getByRole("button", { name: /Mine Pending/i }).click();
    await page.getByRole("link", { name: /Explorer/i }).click();

    await assert.doesNotReject(() => page.getByText(/Block #/i).first().waitFor({ timeout: 3000 }));
    await browser.close();
  });
}

function nextBlock(apiState, transactions) {
  const previous = apiState.blocks.at(-1);
  const block = {
    index: apiState.blocks.length,
    hash: `block-${apiState.blocks.length}`,
    previousHash: previous.hash,
    transactions,
    nonce: apiState.blocks.length
  };
  apiState.blocks.push(block);
  return block;
}

function json(route, payload, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(payload)
  });
}
