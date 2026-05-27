import assert from "node:assert/strict";
import test from "node:test";
import { detailHash, parseRoute, routeToHash } from "../src/lib/routes.js";

test("top-level navigation routes map to stable hash links", () => {
  assert.equal(routeToHash("dashboard"), "#/");
  assert.equal(routeToHash("explorer"), "#/blocks");
  assert.equal(routeToHash("wallet"), "#/wallets");
  assert.equal(routeToHash("mining"), "#/mining");
  assert.equal(routeToHash("peers"), "#/peers");
});

test("detail routes preserve page ownership and decode identifiers", () => {
  assert.deepEqual(parseRoute("#/blocks/7"), { view: "explorer", detailType: "blocks", detailId: "7" });
  assert.deepEqual(parseRoute("#/transactions/tx%2Fabc"), { view: "explorer", detailType: "transactions", detailId: "tx/abc" });
  assert.deepEqual(parseRoute("#/wallets/key%2Bvalue"), { view: "wallet", detailType: "wallets", detailId: "key+value" });
  assert.deepEqual(parseRoute("#/peers/node-b"), { view: "peers", detailType: "peers", detailId: "node-b" });
});

test("detail hash encodes route identifiers for browser smoke navigation", () => {
  assert.equal(detailHash("transactions", "tx/abc"), "#/transactions/tx%2Fabc");
  assert.equal(detailHash("wallets", "key+value"), "#/wallets/key%2Bvalue");
});
