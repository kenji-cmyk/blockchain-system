# Blockchain System

Blockchain System is a learning project that demonstrates a simple blockchain built with Spring Boot. The goal is to make core blockchain concepts easy to inspect through REST APIs: blocks, hashes, previous hashes, proof-of-work, transactions, wallets, digital signatures, pending transactions, mining rewards, simulated peers, and basic chain synchronization.

This is not a production blockchain. The project intentionally keeps the architecture small and observable so each concept can be tested, broken, reset, and extended step by step.

## Features

- Creates a genesis block on startup and after chain resets.
- Mines blocks with proof-of-work and configurable difficulty.
- Validates block hashes, previous hash links, difficulty, and transaction signatures.
- Generates wallets as RSA public/private key pairs.
- Creates and signs transactions.
- Stores new transactions in a pending transaction pool.
- Mines pending transactions into new blocks with miner rewards.
- Simulates peer nodes inside the same app.
- Resolves conflicts by adopting a longer valid peer chain.
- Exposes a tamper API for validation demos.
- Returns unified JSON error responses.
- Exposes an OpenAPI JSON document at `GET /api/docs/openapi`.
- Logs mining time and nonce count.
- Supports optional file or H2 database persistence.
- Includes Docker and Docker Compose setup.
- Includes MockMvc tests for all current APIs.

## Project Structure

```text
blockchain-system/
├── README.md
├── docker-compose.yml
└── backend/
    ├── Dockerfile
    ├── README.md
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/kna/backend/
        │   └── resources/
        └── test/
```

Key areas:

- `backend/`: Spring Boot REST API containing the blockchain implementation.
- `backend/README.md`: detailed backend documentation, API examples, configuration, and roadmap.
- `backend/src/main/java/com/kna/backend/entity`: `Block`, `Transaction`, and `Wallet` models.
- `backend/src/main/java/com/kna/backend/service`: chain, mining, validation, persistence, and peer sync logic.
- `backend/src/main/java/com/kna/backend/controller`: REST API controllers and error handling.
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
- `GET /api/wallets/new`: create a wallet.
- `POST /api/transactions`: create and sign a transaction.
- `GET /api/transactions/pending`: view the pending transaction pool.
- `POST /api/transactions/mine`: mine pending transactions.
- `GET /api/chain/status`: view chain status.
- `GET /api/chain/validate`: validate the chain.
- `PUT /api/chain/difficulty`: update mining difficulty.
- `POST /api/chain/tamper`: intentionally tamper with a block.
- `POST /api/chain/reset`: reset chain state.
- `POST /api/peers`: register a simulated peer.
- `GET /api/peers`: list simulated peers.
- `GET /api/peers/{peerId}/chain`: fetch a peer chain.
- `POST /api/peers/{peerId}/blocks`: mine a demo block on a peer.
- `POST /api/peers/{peerId}/sync`: sync from a peer.
- `GET /api/docs/openapi`: view the OpenAPI document.

## Current Status

Completed:

- Section A: basic blockchain, genesis block, SHA-256 hashes, nonce, proof-of-work, validation, tamper API, reset API, and difficulty API.
- Section B: transactions, wallets, transaction signing and verification, pending transaction pool, and mining rewards.
- Section C: simulated peers, peer registration, peer chain fetch, longer-valid-chain conflict resolution, and sync demo.
- Section D: unified error responses, validation annotations, OpenAPI document, mining logs, optional persistence, Dockerfile, and Docker Compose.
- API tests for all current endpoints.

## Future Plan

### Phase 1: Make the Blockchain Model More Realistic

- Add transaction fees and miner fee collection.
- Add wallet balances derived from transaction history.
- Reject transactions when the sender balance is insufficient.
- Add a UTXO-style model or account-state model and document the tradeoffs.
- Add block size or transaction count limits.

### Phase 2: Improve Peer Networking

- Move from in-app simulated peers to HTTP-based multi-instance peers.
- Add peer health checks.
- Add peer discovery and peer removal.
- Broadcast pending transactions to peers.
- Broadcast newly mined blocks to peers.
- Add sync retry and timeout handling.

### Phase 3: Strengthen Consensus and Validation

- Validate complete transaction history, not only signatures.
- Add chain cumulative difficulty instead of simple chain length.
- Add fork handling and orphan block tracking.
- Add mempool rules for duplicate transactions.
- Add deterministic serialization for block hashes.

### Phase 4: Improve Persistence and Operations

- Persist blocks, transactions, wallets, peers, and mempool state in normalized tables.
- Add database migrations.
- Add application profiles for local, test, and docker.
- Add health endpoints and metrics.
- Add structured logs and request tracing.
- Add CI workflow for tests and Docker build.

### Phase 5: Add Client Experience

- Build a small web UI for chain browsing.
- Add wallet creation and transaction submission screens.
- Show mining progress, nonce count, and chain validity.
- Visualize peer chains and conflict resolution.

## Notes

`application.properties` is ignored by Git. Use `backend/src/main/resources/application-example.properties` as the template for local configuration.
