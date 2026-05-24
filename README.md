# Blockchain System

Blockchain System is a learning project that demonstrates a simple blockchain built with Spring Boot. The goal is to make core blockchain concepts easy to inspect through REST APIs: blocks, hashes, previous hashes, proof-of-work, transactions, wallets, digital signatures, a pending transaction pool, and mining rewards.

This is not a production blockchain. The project intentionally keeps the architecture small, uses in-memory storage, and exposes simple APIs so the system is easy to test, reset, and extend step by step.

## Features

- Automatically creates a genesis block on startup and after chain resets.
- Stores the blockchain in memory.
- Mines blocks with proof-of-work.
- Supports configurable mining difficulty.
- Validates block hashes, previous hash links, proof-of-work difficulty, and transaction signatures.
- Generates wallets as RSA public/private key pairs.
- Creates transactions with sender, receiver, and amount.
- Signs transactions with private keys.
- Verifies transactions with public keys.
- Keeps new transactions in a pending transaction pool.
- Mines pending transactions into a new block.
- Adds a mining reward transaction for the miner.
- Provides a tamper API to intentionally invalidate the chain for learning.
- Includes MockMvc API tests for all current endpoints.

## Project Structure

```text
blockchain-system/
├── README.md
└── backend/
    ├── README.md
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/kna/backend/
        │   └── resources/
        └── test/
```

Key areas:

- `backend/`: Spring Boot REST API containing the current blockchain implementation.
- `backend/README.md`: detailed backend documentation, API examples, and technical roadmap.
- `backend/src/main/java/com/kna/backend/entity`: `Block`, `Transaction`, and `Wallet` models.
- `backend/src/main/java/com/kna/backend/service`: chain management, pending pool, mining, and validation logic.
- `backend/src/main/java/com/kna/backend/controller`: REST API controller.
- `backend/src/test`: API tests.

## Technology Stack

- Java 25
- Spring Boot 4.0.6
- Maven
- Gson
- JUnit and Spring MockMvc
- Java Security API for RSA signatures and SHA-256 hashing

## Running the Project

Go to the backend directory:

```bash
cd backend
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
- `POST /api/chain/reset`: reset the chain.

Basic workflow:

1. Call `GET /api/wallets/new` twice to create sender and receiver wallets.
2. Call `POST /api/transactions` with the sender public key, receiver public key, amount, and sender private key.
3. Call `GET /api/transactions/pending` to confirm the transaction is waiting to be mined.
4. Call `POST /api/transactions/mine` with the miner reward address.
5. Call `GET /api/chain/validate` to confirm the chain is still valid.

## Roadmap Status

Completed:

- Section A: basic blockchain, genesis block, add block, SHA-256 hash, nonce, proof-of-work, validation, tamper API, reset API, and difficulty API.
- Section B: transactions, wallets, transaction signing and verification, pending transaction pool, and mining rewards.
- API tests for all current endpoints.

Planned next:

- Simulate multiple nodes.
- Add peer registration.
- Synchronize chains between peers.
- Resolve conflicts by choosing the longer valid chain.
- Standardize error responses.
- Add OpenAPI/Swagger documentation.
- Log mining time and nonce count.
- Add optional database persistence.
- Add Dockerfile and docker-compose.

## Notes

The current system uses in-memory storage, so blocks and pending transactions are lost when the application restarts. This is intentional for the learning stage because it makes the project quick to reset and easy to experiment with.
