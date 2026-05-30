# Blockchain System

Blockchain System is a learning project that demonstrates a simple blockchain built with Spring Boot and a React/Tailwind client. The goal is to make core blockchain concepts easy to inspect through REST APIs and an interactive web UI: blocks, hashes, previous hashes, proof-of-work, signed transactions, wallets, balances, fees, mining rewards, pending transactions, simulated peers, HTTP peers, basic chain synchronization, persistence, and operational health.

This is not a production blockchain. The project intentionally keeps the architecture small and observable so each concept can be tested, broken, reset, and extended step by step.

## Features

- Creates a genesis block on startup and after chain resets.
- Mines blocks with proof-of-work and configurable difficulty.
- Validates block hashes, previous hash links, difficulty, transaction signatures, and transaction count limits.
- Replays complete transaction history during chain validation to reject overspending chains.
- Selects peer chains by cumulative difficulty instead of simple length.
- Tracks fork and orphan blocks received from peers.
- Uses deterministic block hash serialization.
- Generates URL-safe RSA wallet public/private key pairs.
- Creates and signs transactions.
- Supports optional transaction fees.
- Derives wallet balances from committed unspent transaction outputs.
- Selects spendable outputs, creates change outputs, and rejects spent-output reuse.
- Validates transaction dependencies inside each block.
- Rejects transactions when the sender cannot cover amount plus fee.
- Stores new transactions in a pending transaction pool.
- Shows available wallet balance after subtracting pending outgoing transactions.
- Mines pending transactions into new blocks with miner rewards plus collected fees.
- Limits the number of transactions that can be included in one block.
- Supports simulated peer nodes inside the same app.
- Supports HTTP-based peer registration for multi-instance demos.
- Exposes node identity and capability metadata for peer handshakes.
- Checks peer health and removes peers from the local registry.
- Scores peers and evicts unhealthy HTTP peers after repeated failures.
- Discovers peers from configured peer URLs.
- Gossips pending transactions and newly mined blocks to HTTP peers with duplicate-message guards.
- Rejects stale, malformed, and oversized peer messages.
- Uses configurable peer request timeout and retry attempts.
- Resolves conflicts by adopting a longer valid peer chain.
- Exposes a tamper API for validation demos.
- Returns unified JSON error responses.
- Exposes an OpenAPI JSON document at `GET /api/docs/openapi`.
- Logs mining time and nonce count.
- Supports optional file persistence and normalized H2 database persistence.
- Provides local, test, and docker application profiles.
- Exposes operations health and metrics APIs.
- Adds request tracing with `X-Request-Id`.
- Protects operator routes with bearer-token roles and rate-limits expensive mining/broadcast APIs.
- Enforces request-size limits and rejects malformed, replayed, or oversized peer payloads.
- Serves a responsive luminous dark React/Tailwind web UI with route-based navigation, deep links, and detail pages for blocks, transactions, wallets, and peers.
- Provides consistent loading, empty, error, and retry states for API-backed frontend panels.
- Exposes richer operational metrics for validation, mining, broadcast, peer sync, and rejected peer/user payloads.
- Emits structured event logs for mining, resets, peer sync, broadcasts, rejected blocks, and rejected transactions.
- Organizes the frontend into layout, UI, blockchain, wallet, peer, view, hook, and API helper modules.
- Includes CI workflow for frontend build, frontend smoke tests, backend tests, and Docker image build.
- Includes Docker and Docker Compose setup with a multi-node demo profile.
- Includes MockMvc tests for all current APIs.

## Project Structure

```text
blockchain-system/
|-- README.md
|-- docker-compose.yml
|-- frontend/
|   |-- package.json
|   |-- src/
|   |   |-- components/
|   |   |-- hooks/
|   |   |-- lib/
|   |   `-- views/
|   `-- vite.config.js
`-- backend/
    |-- Dockerfile
    |-- README.md
    |-- pom.xml
    `-- src/
        |-- main/
        |   |-- java/com/kna/backend/
        |   `-- resources/
        `-- test/
```

Key areas:

- `backend/`: Spring Boot REST API containing the blockchain implementation.
- `frontend/`: React, Vite, and Tailwind source for the client experience.
- `backend/README.md`: detailed backend documentation, API examples, configuration, and roadmap.
- `backend/src/main/java/com/kna/backend/entity`: `Block`, `Transaction`, and `Wallet` models.
- `backend/src/main/java/com/kna/backend/service`: chain, mining, validation, persistence, balance, and peer sync logic.
- `backend/src/main/java/com/kna/backend/controller`: REST API controllers and error handling.
- `backend/src/main/resources/static`: built frontend assets served by Spring Boot.
- `backend/src/test`: API tests.

## Technology Stack

- Java 25
- Spring Boot 4.0.6
- Maven
- Gson
- H2 database
- JUnit and Spring MockMvc
- React
- Vite
- Tailwind CSS
- Lucide React icons
- Node.js and npm
- Docker and Docker Compose
- Java Security API for RSA signatures and SHA-256 hashing

## Running the Project

Go to the backend directory:

```bash
cd backend
```

Create a local config file:

```bash
cp src/main/resources/application-example.properties src/main/resources/application.properties
```

Run the application:

```bash
mvn spring-boot:run
```

The API runs at:

```text
http://localhost:8080
```

The web client runs from the same Spring Boot app at:

```text
http://localhost:8080/
```

Build the frontend from the repository root:

```bash
cd frontend
npm ci
npm run build
```

The frontend build writes static assets into:

```text
backend/src/main/resources/static
```

Run the test suite:

```bash
mvn test
```

Run with Docker Compose from the repository root:

```bash
docker compose up --build
```

Run a three-node demo from the repository root:

```bash
docker compose --profile multinode up --build
```

The default node is available at `http://localhost:8080`; extra demo nodes are published at `http://localhost:8081` and `http://localhost:8082`.

## Main APIs

Important endpoints:

- `GET /api/blocks`: view the full chain.
- `GET /api/blocks/{index}`: view a block by index.
- `POST /api/blocks`: mine a legacy demo reward block for a text/address receiver.
- `GET /api/wallets/new`: create a wallet.
- `GET /api/wallets/{address}/balance`: view a wallet's available balance.
- `POST /api/transactions`: create and sign a transaction with optional `fee`.
- `GET /api/transactions/pending`: view the pending transaction pool.
- `POST /api/transactions/mine`: mine pending transactions and collect rewards plus fees.
- `GET /api/chain/status`: view chain status.
- `GET /api/chain/validate`: validate the chain.
- `GET /api/chain/forks`: view tracked fork blocks.
- `GET /api/chain/orphans`: view tracked orphan blocks.
- `PUT /api/chain/difficulty`: update mining difficulty.
- `POST /api/chain/tamper`: intentionally tamper with a block.
- `POST /api/chain/reset`: reset chain state.
- `GET /api/ops/health`: view backend health.
- `GET /api/ops/metrics`: view backend metrics.
- `GET /api/node/info`: view node identity and advertised peer capabilities.
- `POST /api/peers`: register a simulated peer or HTTP peer.
- `GET /api/peers`: list registered peers.
- `POST /api/peers/discover`: register peers from base URLs.
- `GET /api/peers/{peerId}/health`: check peer health.
- `DELETE /api/peers/{peerId}`: remove a peer.
- `GET /api/peers/{peerId}/chain`: fetch a peer chain.
- `POST /api/peers/{peerId}/blocks`: mine a demo block on a peer.
- `POST /api/peers/{peerId}/sync`: sync from a peer.
- `POST /api/peers/broadcast/transactions`: broadcast current pending transactions.
- `POST /api/transactions/broadcast`: accept a transaction broadcast from a peer.
- `POST /api/blocks/broadcast`: accept a block broadcast from a peer.
- `GET /api/docs/openapi`: view the OpenAPI document.

## Balance Model

The project now uses a UTXO ledger derived from chain history while keeping the REST API account-like for learning:

- Mining rewards create spendable outputs for the reward receiver.
- A normal transaction spends one or more existing outputs owned by the sender.
- A normal transaction creates a receiver output for `amount` and, when needed, a sender change output.
- The transaction fee is the selected input total minus the output total.
- Pending outgoing transactions are subtracted from the available balance so the sender cannot overspend before mining.

This keeps the UI and request payloads approachable while giving validation Bitcoin-like ledger properties: spent-output rejection, same-block dependency handling, replay protection through unique transaction ids, and canonical signatures over inputs and outputs.

## Current Status

Completed:

- Section A: basic blockchain, genesis block, SHA-256 hashes, nonce, proof-of-work, validation, tamper API, reset API, and difficulty API.
- Section B: transactions, wallets, transaction signing and verification, pending transaction pool, and mining rewards.
- Section C: simulated peers, peer registration, peer chain fetch, longer-valid-chain conflict resolution, and sync demo.
- Section D: unified error responses, validation annotations, OpenAPI document, mining logs, optional persistence, Dockerfile, and Docker Compose.
- Phase 1: transaction fees, miner fee collection, chain-derived wallet balances, insufficient-balance rejection, account-state documentation, URL-safe wallet keys, and transaction count limits per block.
- Phase 2: HTTP peer registration, health checks, peer discovery, peer removal, transaction broadcast, block broadcast, and peer sync timeout/retry handling.
- Phase 3: transaction-history replay validation, cumulative-difficulty chain selection, fork and orphan tracking, duplicate mempool rules, and deterministic block hash serialization.
- Phase 4: normalized database persistence for blocks, transactions, wallets, peers, and mempool state; schema migration tracking; local/test/docker profiles; health and metrics APIs; request tracing; and CI workflow.
- Phase 5: responsive React/Tailwind web client for chain browsing, wallet creation, balance lookup, transaction submission, pending transaction mining, difficulty controls, fork/orphan visibility, and peer conflict resolution demos, frontend source split into focused components, views, hooks, API helpers, and formatting utilities, API tests for all current endpoints.
- Phase 6: UTXO ledger replay, coin selection, change outputs, spent-output validation, same-block transaction dependency validation, stronger transaction canonicalization, and ledger-level hardening tests.
- Phase 7: node identity and capability handshakes, peer scoring and unhealthy-peer eviction, optional scheduled sync, gossip headers and relay, and peer message safeguards for duplicate, stale, malformed, and oversized payloads.
- Phase 8: bearer-token operator/read-only roles, protected admin and peer-management routes, rate limiting for expensive endpoints, request-size boundaries, and security/system smoke tests.
- Phase 9: route-based frontend navigation, deep links, block/transaction/wallet/peer detail pages, consistent API loading/error/empty/retry states, frontend smoke tests, and CI frontend verification.
- Phase 10: richer operational metrics, structured event logs, multi-node Docker Compose demo profiles, frontend-first release image packaging, and production-style configuration/runbook notes.

## Roadmap

The first ten learning phases are complete. The next roadmap keeps the project educational while moving it closer to a realistic distributed ledger lab. Each phase should follow the ECC workflow: plan first, write failing tests before implementation, keep security checks explicit, and update project documentation when behavior changes.

### Phase 11: Test and Quality Baseline

- [x] Measure backend and frontend quality in CI and publish the project coverage target in this README.
- [x] Add focused service-level tests around UTXO replay, broadcast rejection tracking, and persistence restore paths.
- [x] Add frontend component contract tests for wallet, mining, peer, and detail-view states.
- [x] Add a small Playwright E2E suite for the critical browser flow: create wallet, mine funds, send transaction, mine pending transaction, inspect block details.
- [x] Add static checks for accidental secret exposure before release packaging.

Phase 11 quality target: keep backend and frontend coverage at 80% or higher as the suite grows. The CI baseline now runs frontend build, frontend smoke and component contract tests, optional Playwright E2E contracts, static secret scanning, backend tests, and Docker image build.

### Phase 12: API and Domain Cleanup

- [ ] Introduce a consistent API envelope for success, data, error, and metadata responses while preserving existing learning examples.
- [ ] Split large service responsibilities into focused chain, mempool, wallet, ledger, peer, and persistence collaborators.
- [ ] Replace floating-point transaction amounts with a fixed smallest-unit representation to avoid rounding ambiguity.
- [ ] Version the REST API under `/api/v1` and keep compatibility notes for existing endpoints.
- [ ] Generate the OpenAPI document from route contracts or shared metadata instead of maintaining a fully manual document.

### Phase 13: Consensus Research Track

- [ ] Add adjustable consensus policies for longest valid chain, cumulative difficulty, and finality-delay demos.
- [ ] Store competing fork branches with enough metadata to inspect why a branch was accepted, rejected, or kept as a fork.
- [ ] Add orphan reattachment when missing parent blocks arrive from peers.
- [ ] Add deterministic block and transaction serialization tests that protect cross-node compatibility.
- [ ] Document the tradeoffs between the demo consensus model and production blockchain consensus.

### Phase 14: Network Reliability

- [ ] Add peer backoff, quarantine, and recovery states instead of immediate score-only eviction.
- [ ] Add peer inventory messages so nodes can announce block and transaction ids before sending full payloads.
- [ ] Add bounded mempool capacity with eviction rules and clear rejection reasons.
- [ ] Add network-partition demo scripts for the Docker Compose multi-node profile.
- [ ] Add metrics for peer latency, retry count, duplicate gossip messages, and fork adoption events.

### Phase 15: Security Hardening

- [ ] Replace demo bearer tokens with pluggable authentication suitable for local OAuth2/OIDC or signed operator tokens.
- [ ] Add authorization tests for every state-changing endpoint and peer-management route.
- [ ] Add key-rotation guidance and startup validation for required production-style secrets.
- [ ] Add stronger replay protection for peer messages with bounded nonce or timestamp windows.
- [ ] Run dependency and container vulnerability checks in CI before publishing images.

### Phase 16: Productized Operator UI

- [ ] Add an operator settings screen for difficulty, mining limits, peer timeout, retry, and scheduled sync state.
- [ ] Add a transaction builder that explains selected UTXOs, fees, and change outputs before signing.
- [ ] Add fork, orphan, and peer-gossip timelines for easier multi-node debugging.
- [ ] Add an operations dashboard for health, metrics, rate limits, and recent structured events.
- [ ] Add E2E coverage for deep links, responsive layout, and empty/error/retry states.

### Phase 17: Persistence and Migration Maturity

- [ ] Replace the simple schema migration tracker with a standard migration tool such as Flyway or Liquibase.
- [ ] Add backup and restore examples for the H2 demo database volume.
- [ ] Add repository interfaces around persistence so storage backends can be tested independently.
- [ ] Add data-integrity checks on startup for persisted blocks, transactions, wallets, peers, and mempool entries.
- [ ] Document migration and rollback steps in the backend runbook.

### Phase 18: Release and Deployment Readiness

- [ ] Add image tagging, SBOM generation, and reproducible release notes to CI.
- [ ] Add environment-specific configuration examples for local, docker, and production-like deployments.
- [ ] Add smoke tests that run against the built Docker image, not only source-level tests.
- [ ] Add a multi-node release checklist covering secrets, persistence, ports, peer discovery, and health checks.
- [ ] Keep the default deployment safe for demos by binding local examples to localhost and documenting exposed-network risks.

## Notes

`application.properties` is ignored by Git. Use `backend/src/main/resources/application-example.properties` as the template for local configuration.
