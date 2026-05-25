# Blockchain Learning Backend

This Spring Boot backend demonstrates a simple in-memory blockchain for learning core concepts: blocks, hashes, previous hashes, nonce, proof-of-work mining, chain validation, signed transactions, wallets, UTXO ledger balances, transaction fees, a pending transaction pool, mining rewards, simulated peers, HTTP peers, and basic chain synchronization.

## Current Status

- Runtime: Java 25, Spring Boot 4.0.6, Maven.
- Storage: in-memory by default. Optional file persistence or normalized H2 database persistence can be enabled with configuration.
- Block data: each block contains a list of `Transaction` objects.
- Wallets: generated RSA public/private key pairs returned as URL-safe Base64 strings.
- Transactions: sender, receiver, amount, optional fee, timestamp, transaction id, nonce, UTXO inputs, outputs, and digital signature.
- Balances: derived by replaying committed unspent transaction outputs, with pending outgoing transactions subtracted from the available balance.
- Consensus checks: each block hash must match its content, each `previousHash` must link to the previous block, every hash must satisfy the configured proof-of-work difficulty, every transaction signature must be valid, spent outputs must not be reused, same-block dependencies must be valid, and each block must stay within the configured transaction count limit.
- Genesis block: created automatically on startup and when the reset API is called.
- Peer sync: simulated peers run inside the same application, and HTTP peers can point to other running backend instances for multi-instance demos.
- Peer networking: HTTP peers support node-info handshakes, capability metadata, health checks, discovery by URL, removal, scoring, automatic unhealthy-peer eviction, transaction gossip, block gossip, and configurable timeout/retry handling.
- Error handling: API errors return a unified JSON format.
- Security: operator routes require bearer-token credentials when security is enabled, and expensive endpoints are rate limited.
- Observability: mining logs include block source, index, difficulty, nonce count, elapsed time, and hash.
- Operations: health and metrics APIs expose chain state, validity, persistence status, peer count, and cumulative difficulty.
- Request tracing: every HTTP request gets an `X-Request-Id` response header and structured request log entry.
- Client: Spring Boot serves built React/Tailwind assets for exploring the chain, wallets, transactions, mining, and peer sync.
- Default difficulty: `blockchain.difficulty=3`.
- Default mining reward: `blockchain.mining-reward=10`.
- Default transaction count limit: `blockchain.max-transactions-per-block=5`.
- Default node id: generated at startup unless `blockchain.node.id` is set.
- Default peer timeout: `blockchain.peer.timeout-ms=1500`.
- Default peer retry attempts: `blockchain.peer.retry-attempts=2`.
- Default peer eviction score: `blockchain.peer.eviction-score=-3`.
- Default peer message size limit: `blockchain.peer.max-message-bytes=65536`.
- Scheduled peer sync is disabled by default with `blockchain.peer.scheduled-sync.enabled=false`.
- Security is enabled by default with `blockchain.security.enabled=true`.
- Default operator token: `blockchain.security.operator-token=operator-token`.
- Default read-only token: `blockchain.security.read-only-token=read-only-token`.
- Default API request size limit: `blockchain.security.max-request-bytes=65536`.
- Default expensive endpoint rate limit: `blockchain.rate-limit.expensive-limit=20` per `blockchain.rate-limit.window-ms=60000`.

## Running the Project

Create a local application config from the example:

```bash
cp src/main/resources/application-example.properties src/main/resources/application.properties
```

`application.properties` is ignored by Git so local settings can be changed safely.

```bash
mvn spring-boot:run
```

By default, the API runs at:

```text
http://localhost:8080
```

The browser client is served from the same app:

```text
http://localhost:8080/
```

Run tests:

```bash
mvn test
```

Run with Docker Compose from the repository root:

```bash
docker compose up --build
```

The Docker Compose setup enables H2 database persistence and stores data in a Docker volume.

## API

### Authentication

When `blockchain.security.enabled=true`, operator routes require:

```http
Authorization: Bearer OPERATOR_TOKEN
```

The operator token can change difficulty, reset or tamper with the chain, mine demo/pending blocks, and manage peers. The read-only token is accepted as an identity for read-only use but cannot perform operator actions. The test profile disables security by default so older learning tests can set up state directly; Phase 8 tests enable security explicitly.

### View the Entire Chain

```http
GET /api/blocks
```

### View a Block by Index

```http
GET /api/blocks/{index}
```

### Create a Wallet

```http
GET /api/wallets/new
```

The response contains a `publicKey` and `privateKey`. The `publicKey` is also used as the wallet address in this demo.

### Create a Signed Transaction

This creates a transaction, signs it with the sender private key, verifies the signature, and adds it to the pending transaction pool.

```http
POST /api/transactions
Content-Type: application/json

{
  "sender": "BASE64_PUBLIC_KEY",
  "receiver": "BASE64_PUBLIC_KEY",
  "amount": 5,
  "fee": 0.25,
  "privateKey": "BASE64_PRIVATE_KEY"
}
```

The sender must have enough available balance to cover `amount + fee`. The `fee` field is optional and defaults to `0`. The service selects spendable outputs and creates a change output automatically, so clients do not need to submit UTXO inputs directly.

### View Wallet Balance

```http
GET /api/wallets/{address}/balance
```

The response returns available balance, which is committed balance minus pending outgoing transactions.

### View Pending Transactions

```http
GET /api/transactions/pending
```

### Mine Pending Transactions

This mines pending transactions into a new block and adds a mining reward transaction for the given reward address. The reward amount is `blockchain.mining-reward` plus the fees collected from the transactions included in that block.

```http
POST /api/transactions/mine
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "rewardAddress": "BASE64_PUBLIC_KEY"
}
```

### Validate the Chain

```http
GET /api/chain/validate
```

Example response:

```json
{
  "size": 2,
  "difficulty": 3,
  "pendingTransactions": 0,
  "valid": true
}
```

### View Chain Status

```http
GET /api/chain/status
```

### Adjust Difficulty

```http
PUT /api/chain/difficulty
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "difficulty": 2
}
```

The difficulty is limited from `0` to `6` to avoid very long mining times in this demo. If the difficulty is increased above the level used for previously mined blocks, the existing chain may become invalid. This is intentional because it shows how changing consensus rules affects validation.

### Tamper With the Chain

This endpoint intentionally changes a block marker without re-mining it. After calling it, validation should usually return `false`.

```http
POST /api/chain/tamper
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "index": 1,
  "data": "Tampered data"
}
```

### Reset the Chain

This clears the chain and pending transaction pool, then creates a new genesis block.

```http
POST /api/chain/reset
Authorization: Bearer OPERATOR_TOKEN
```

### Operations Health

```http
GET /api/ops/health
```

Example response:

```json
{
  "status": "UP",
  "chainValid": true,
  "chainSize": 1,
  "pendingTransactions": 0,
  "persistenceEnabled": false,
  "persistenceType": "file"
}
```

### Operations Metrics

```http
GET /api/ops/metrics
```

Example response:

```json
{
  "chainSize": 1,
  "pendingTransactions": 0,
  "cumulativeDifficulty": 4,
  "forkBlocks": 0,
  "orphanBlocks": 0,
  "peers": 0
}
```

### OpenAPI Document

```http
GET /api/docs/openapi
```

This returns an OpenAPI 3.0 JSON document for the available learning APIs.

### View Node Info

```http
GET /api/node/info
```

This returns the local node identity, backend version, advertised capabilities, chain size, and cumulative difficulty. HTTP peer registration uses this endpoint as a handshake when the remote node supports it.

### Register a Peer

Without `baseUrl`, this creates a simulated peer inside the same application and initializes its chain from the current local chain.

```http
POST /api/peers
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "peerId": "node-b"
}
```

With `baseUrl`, this registers an HTTP peer that points to another running backend instance.

```http
POST /api/peers
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "peerId": "node-b",
  "baseUrl": "http://localhost:8081"
}
```

### View Registered Peers

```http
GET /api/peers
```

Example response:

```json
[
  {
    "peerId": "node-b",
    "chainSize": 1,
    "valid": true
  }
]
```

HTTP peer summaries include `baseUrl`, `healthy`, and `mode`. Phase 7 summaries also include `nodeId`, `capabilities`, `score`, `failureCount`, and `lastSeenAt`.

### Discover Peers

This registers HTTP peers from base URLs. Peer IDs are derived from the URL host and port.

```http
POST /api/peers/discover
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "peerUrls": [
    "http://localhost:8081",
    "http://localhost:8082"
  ]
}
```

### Check Peer Health

```http
GET /api/peers/{peerId}/health
```

For HTTP peers, health calls the peer's `GET /api/chain/status` endpoint with the configured timeout and retry settings. Successful checks increase peer score; failed checks decrease score and can evict the peer when it reaches `blockchain.peer.eviction-score`.

### Remove a Peer

```http
DELETE /api/peers/{peerId}
Authorization: Bearer OPERATOR_TOKEN
```

### Fetch a Peer Chain

```http
GET /api/peers/{peerId}/chain
```

### Mine a Demo Block on a Peer

This mines a reward-only block on the selected simulated peer. It is useful for making the peer chain longer than the local chain so conflict resolution can be tested.

```http
POST /api/peers/{peerId}/blocks
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "minerAddress": "BASE64_PUBLIC_KEY"
}
```

### Sync From a Peer

This compares the local chain with the selected peer chain. The local chain is replaced only when the peer chain is both valid and longer.

```http
POST /api/peers/{peerId}/sync
Authorization: Bearer OPERATOR_TOKEN
```

Example response:

```json
{
  "peerId": "node-b",
  "peerChainSize": 3,
  "localChainSizeBefore": 1,
  "localChainSizeAfter": 3,
  "peerValid": true,
  "adopted": true,
  "message": "Local chain replaced with longer valid peer chain"
}
```

### Broadcast Pending Transactions

```http
POST /api/peers/broadcast/transactions
Authorization: Bearer OPERATOR_TOKEN
```

This sends every local pending transaction to registered HTTP peers through `POST /api/transactions/broadcast`. Outbound peer messages include `X-Node-Id` and `X-Gossip-Id` headers so peers can deduplicate and relay messages safely.

### Accept a Broadcast Transaction

```http
POST /api/transactions/broadcast
Content-Type: application/json
```

The request body is a signed `Transaction`. The local node validates the signature and available sender balance before adding it to the pending transaction pool. Accepted peer transactions are relayed to other HTTP peers with the same gossip id when one is supplied.

### Accept a Broadcast Block

```http
POST /api/blocks/broadcast
Content-Type: application/json
```

The request body is a mined `Block`. The local node accepts it only when it extends the current chain and passes chain validation. Accepted peer blocks are relayed to other HTTP peers with the same gossip id when one is supplied. Duplicate gossip ids, stale blocks, malformed JSON, and oversized bodies are rejected or ignored before they can alter local chain state.

### Legacy Demo Block Endpoint

This endpoint is kept for the basic learning flow from section A. It mines a new reward-only block whose receiver is the supplied text or wallet address. In tests and demos it can be used to fund a wallet before sending transactions.

```http
POST /api/blocks
Authorization: Bearer OPERATOR_TOKEN
Content-Type: application/json

{
  "data": "Learn block hash"
}
```

## Error Format

All handled API errors use the same response shape:

```json
{
  "timestamp": "2026-05-24T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "data: Block data must not be blank",
  "path": "/api/blocks"
}
```

## Persistence

Persistence is disabled by default:

```properties
blockchain.persistence.enabled=false
```

Enable JSON file persistence:

```properties
blockchain.persistence.enabled=true
blockchain.persistence.type=file
blockchain.persistence.file=data/blockchain-chain.json
```

Enable H2 database persistence:

```properties
blockchain.persistence.enabled=true
blockchain.persistence.type=database
spring.datasource.url=jdbc:h2:file:./data/blockchain-db
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
```

Database persistence writes normalized tables for blocks, transactions, wallets, peers, and pending transactions. A simple schema migration table records the current schema version. File persistence still stores only the chain JSON and is intended for lightweight local demos.

## Application Profiles

- `local`: local development defaults with persistence disabled.
- `test`: faster proof-of-work settings, in-memory H2 datasource, and persistence disabled unless a test overrides it.
- `docker`: database persistence enabled with H2 data stored under `/data`.

Run with a profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Code Structure

- `entity/Block.java`: block model, transaction list, hash calculation, mining, and tamper marker.
- `entity/Transaction.java`: transaction model, UTXO inputs/outputs, signing, signature verification, and mining reward transactions.
- `entity/TransactionInput.java`, `TransactionOutput.java`, `UtxoEntry.java`, `UtxoKey.java`: immutable ledger value objects.
- `entity/Wallet.java`: public/private key pair response model.
- `service/BlockchainService.java`: in-memory chain, pending transaction pool, UTXO coin selection, balances, fees, block mining, validation, difficulty, and reset logic.
- `service/ChainPersistenceService.java`: optional file or H2 database persistence for the chain.
- `service/PeerNodeService.java`: simulated peers, HTTP peers, node-info handshakes, capability metadata, scoring, eviction, scheduled sync, gossip broadcasts, peer mining, and conflict resolution.
- `controller/BlockchainController.java`: REST API for blocks, wallets, transactions, mining, and chain operations.
- `controller/ApiExceptionHandler.java`: unified API error responses.
- `controller/OpenApiController.java`: OpenAPI document endpoint.
- `config/ApiSecurityFilter.java`: bearer-token operator/read-only checks and request-size enforcement.
- `config/RateLimitingFilter.java`: per-client rate limiting for expensive mining and broadcast endpoints.
- `resources/static/`: built React/Tailwind client assets generated from the root `frontend/` project.
- `pkg/utils/StringUtil.java`: SHA-256 helper.
- `pkg/utils/CryptoUtil.java`: RSA wallet generation, signing, and signature verification.
- `pkg/validate/Validator.java`: chain, proof-of-work, previous hash, and transaction validation.
- `pkg/validate/UtxoLedger.java`: UTXO replay, spent-output validation, same-block dependency checks, fee accounting, and balance calculation.
- `dto/*`: request/response records for the API.
- `BackendApplicationTests.java`: MockMvc tests for block, chain, wallet, transaction, and mining workflows.
- `DatabasePersistenceTests.java`: database-mode persistence tests for normalized tables.
- `LedgerValidationTests.java`: ledger-level tests for UTXO dependencies, double-spend rejection, and invalid output canonicalization.
- `PeerNetworkPhase7Tests.java`: peer-network tests for node info, handshake metadata, gossip headers, unhealthy-peer eviction, and hostile peer payload rejection.
- `SecurityPhase8Tests.java`: authentication, role enforcement, malformed-key, replay, hostile payload, and full secured system smoke tests.
- `RateLimitPhase8Tests.java`: rate-limit tests for expensive endpoints.

## Roadmap

### Section A: Basic Blockchain

- [x] In-memory blockchain.
- [x] Genesis block.
- [x] Add block with demo text data.
- [x] SHA-256 hash.
- [x] Nonce and proof-of-work mining.
- [x] Validate hash, previous hash, and difficulty.
- [x] API to view blocks, add a demo block, validate, tamper, and reset.
- [x] API to adjust difficulty.
- [x] Main API tests.

### Section B: Transactions and Wallets

- [x] Create a `Transaction` model with sender, receiver, and amount.
- [x] Create wallets with public/private keys.
- [x] Sign transactions with private keys.
- [x] Verify signatures with public keys.
- [x] Change block data from a string to a transaction list.
- [x] Add a pending transaction pool.
- [x] Add a basic mining reward.

### Phase 1: More Realistic Blockchain Model

- [x] Add transaction fees and miner fee collection.
- [x] Add wallet balances derived from transaction history.
- [x] Reject transactions when the sender balance is insufficient.
- [x] Use an account-state balance model and document the tradeoffs.
- [x] Add block transaction count limits.

### Section C: Simple Nodes and Sync

- [x] Simulate multiple nodes in the same app or through multiple instances.
- [x] API to register peers.
- [x] API to fetch a chain from a peer.
- [x] Rule to choose the longer valid chain.
- [x] Conflict resolution demo.

### Phase 2: Improve Peer Networking

- [x] Move from in-app-only simulated peers to HTTP-based multi-instance peers.
- [x] Add peer health checks.
- [x] Add peer discovery and peer removal.
- [x] Broadcast pending transactions to HTTP peers.
- [x] Broadcast newly mined blocks to HTTP peers.
- [x] Add sync retry and timeout handling.

### Phase 3: Strengthen Consensus and Validation

- [x] Validate complete transaction history across candidate chains, including account-state replay.
- [x] Add chain cumulative difficulty instead of simple chain length.
- [x] Add fork handling and orphan block tracking.
- [x] Add mempool rules for duplicate transactions.
- [x] Add deterministic serialization for block hashes.

### Phase 4: Improve Persistence and Operations

- [x] Persist blocks, transactions, wallets, peers, and mempool state in normalized tables.
- [x] Add database migration tracking.
- [x] Add application profiles for local, test, and docker.
- [x] Add health endpoints and metrics.
- [x] Add structured logs and request tracing.
- [x] Add CI workflow for tests and Docker build.

### Phase 5: Add Client Experience

- [x] Build a small web UI for chain browsing.
- [x] Add wallet creation and transaction submission screens.
- [x] Show wallet balances, pending outgoing transactions, fees, and miner rewards.
- [x] Show mining progress, nonce count, and chain validity.
- [x] Visualize peer chains and conflict resolution.
- [x] Rebuild the client with React, Vite, Tailwind CSS, and reusable components.

### Phase 6: UTXO and Ledger Hardening

- [x] Add a UTXO model alongside or instead of the current account-state balance model.
- [x] Add coin selection, change outputs, and spent-output validation.
- [x] Validate transaction dependencies within the same block.
- [x] Harden transaction canonicalization and replay protection.
- [x] Add ledger-level tests for double-spend and invalid-output scenarios.

### Phase 7: Peer-to-Peer Network Upgrade

- [x] Add node identity, peer handshake, and peer capability metadata.
- [x] Add peer scoring and automatic unhealthy peer eviction.
- [x] Add scheduled peer discovery and background sync.
- [x] Add gossip-style block and transaction propagation.
- [x] Reject duplicate, stale, malformed, or oversized peer messages.

### Phase 8: Security and Admin Controls

- [x] Protect reset, tamper, difficulty, and peer-management endpoints with authentication.
- [x] Add role-based access for read-only and operator actions.
- [x] Add rate limiting for expensive or state-changing APIs.
- [x] Add stricter request size limits and validation at system boundaries.
- [x] Add security tests for malformed keys, replayed transactions, and hostile peer payloads.

### Phase 9: Frontend Productization

- [ ] Add route-based navigation for blocks, transactions, wallets, and peers.
- [ ] Add detail views for individual blocks, transactions, wallets, and peers.
- [ ] Add consistent loading, empty, error, and retry states.
- [ ] Add browser smoke tests for the main client workflows.
- [ ] Add a frontend CI step for build and UI verification.

### Phase 10: Observability and Release Readiness

- [ ] Add richer metrics for mining, validation, peer sync, and broadcasts.
- [ ] Add structured event logs for rejected blocks and rejected transactions.
- [ ] Add multi-node Docker Compose demo profiles.
- [ ] Package backend releases only after frontend assets are built.
- [ ] Add production-style configuration notes and runbooks.

### Section D: Code Quality and Observability

- [x] Add a unified exception response format.
- [x] Add request validation annotations.
- [x] Add OpenAPI/Swagger.
- [x] Add mining time and nonce count logs.
- [x] Add optional database persistence.
- [x] Add Dockerfile and docker-compose.
