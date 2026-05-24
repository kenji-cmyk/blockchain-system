# Blockchain System

Blockchain System is a learning project that demonstrates a simple blockchain built with Spring Boot. The goal is to make core blockchain concepts easy to inspect through REST APIs: blocks, hashes, previous hashes, proof-of-work, signed transactions, wallets, balances, fees, mining rewards, pending transactions, simulated peers, HTTP peers, and basic chain synchronization.

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
- Derives wallet balances from committed chain history.
- Rejects transactions when the sender cannot cover amount plus fee.
- Stores new transactions in a pending transaction pool.
- Shows available wallet balance after subtracting pending outgoing transactions.
- Mines pending transactions into new blocks with miner rewards plus collected fees.
- Limits the number of transactions that can be included in one block.
- Supports simulated peer nodes inside the same app.
- Supports HTTP-based peer registration for multi-instance demos.
- Checks peer health and removes peers from the local registry.
- Discovers peers from configured peer URLs.
- Broadcasts pending transactions and newly mined blocks to HTTP peers.
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
- Serves a responsive luminous dark web UI for chain browsing, wallets, transactions, mining, and peer sync demos.
- Includes CI workflow for backend tests and Docker image build.
- Includes Docker and Docker Compose setup.
- Includes MockMvc tests for all current APIs.

## Project Structure

```text
blockchain-system/
|-- README.md
|-- docker-compose.yml
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
- `backend/README.md`: detailed backend documentation, API examples, configuration, and roadmap.
- `backend/src/main/java/com/kna/backend/entity`: `Block`, `Transaction`, and `Wallet` models.
- `backend/src/main/java/com/kna/backend/service`: chain, mining, validation, persistence, balance, and peer sync logic.
- `backend/src/main/java/com/kna/backend/controller`: REST API controllers and error handling.
- `backend/src/main/resources/static`: browser client for the Phase 5 client experience.
- `backend/src/test`: API tests.

## Technology Stack

- Java 25
- Spring Boot 4.0.6
- Maven
- Gson
- H2 database
- JUnit and Spring MockMvc
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

Run the test suite:

```bash
mvn test
```

Run with Docker Compose from the repository root:

```bash
docker compose up --build
```

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

The project currently uses an account-state model derived from chain history:

- Mining rewards add balance to the reward receiver.
- A normal transaction subtracts `amount + fee` from the sender.
- A normal transaction adds `amount` to the receiver.
- Pending outgoing transactions are subtracted from the available balance so the sender cannot overspend before mining.

This is simpler than a UTXO model and fits the learning API well because balances can be explained by replaying the chain from genesis. The tradeoff is that it does not model individual spendable outputs, coin selection, or UTXO set validation the way Bitcoin-like systems do. A UTXO model is a good future step when the project moves toward stronger consensus validation.

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
- Phase 5: responsive web client for chain browsing, wallet creation, balance lookup, transaction submission, pending transaction mining, difficulty controls, fork/orphan visibility, and peer conflict resolution demos.
- API tests for all current endpoints.

## Future Plan

The core learning roadmap through Phase 5 is complete. Future work can move toward a UTXO model, richer peer visualization, authenticated admin controls, or a dedicated frontend build if the client grows beyond a lightweight embedded UI.

## Notes

`application.properties` is ignored by Git. Use `backend/src/main/resources/application-example.properties` as the template for local configuration.
