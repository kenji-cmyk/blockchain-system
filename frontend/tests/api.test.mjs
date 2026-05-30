import assert from "node:assert/strict";
import test from "node:test";
import { api } from "../src/lib/api.js";

test("api helper returns JSON payloads for successful responses", async () => {
  globalThis.fetch = async (path, options) => {
    assert.equal(path, "/api/chain/status");
    assert.deepEqual(options, {});
    return response(200, { valid: true, size: 1 });
  };

  assert.deepEqual(await api.get("/api/chain/status"), { valid: true, size: 1 });
});

test("api helper serializes POST bodies and content type", async () => {
  globalThis.fetch = async (path, options) => {
    assert.equal(path, "/api/peers");
    assert.equal(options.method, "POST");
    assert.deepEqual(options.headers, { "Content-Type": "application/json" });
    assert.equal(options.body, JSON.stringify({ peerId: "node-b" }));
    return response(201, { peerId: "node-b" });
  };

  assert.deepEqual(await api.post("/api/peers", { peerId: "node-b" }), { peerId: "node-b" });
});

test("api helper surfaces API error messages", async () => {
  globalThis.fetch = async () => response(400, { message: "Sender balance is insufficient" });

  await assert.rejects(
    () => api.post("/api/transactions", {}),
    /Sender balance is insufficient/
  );
});

function response(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name) => name.toLowerCase() === "content-type" ? "application/json" : null
    },
    json: async () => payload,
    text: async () => JSON.stringify(payload)
  };
}
