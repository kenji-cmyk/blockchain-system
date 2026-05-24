# Blockchain Learning Backend

This Spring Boot backend demonstrates a simple in-memory blockchain for learning core concepts: blocks, hashes, previous hashes, nonce, proof-of-work mining, chain validation, signed transactions, wallets, a pending transaction pool, and mining rewards.

## Current Status

- Runtime: Java 25, Spring Boot 4.0.6, Maven.
- Storage: in-memory. Restarting the app clears the chain and pending transactions.
- Block data: each block contains a list of `Transaction` objects.
- Wallets: generated RSA public/private key pairs returned as Base64 strings.
- Transactions: sender, receiver, amount, timestamp, transaction id, and digital signature.
- Consensus checks: each block hash must match its content, each `previousHash` must link to the previous block, every hash must satisfy the configured proof-of-work difficulty, and every transaction signature must be valid.
- Genesis block: created automatically on startup and when the reset API is called.
- Default difficulty: `blockchain.difficulty=3`.
- Default mining reward: `blockchain.mining-reward=10`.

## Running the Project

```bash
mvn spring-boot:run
```

By default, the API runs at:

```text
http://localhost:8080
```

Run tests:

```bash
mvn test
```

## API

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
  "privateKey": "BASE64_PRIVATE_KEY"
}
```

### View Pending Transactions

```http
GET /api/transactions/pending
```

### Mine Pending Transactions

This mines all pending transactions into a new block and adds a mining reward transaction for the given reward address.

```http
POST /api/transactions/mine
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
```

### Legacy Demo Block Endpoint

This endpoint is kept for the basic learning flow from section A. It mines a new block with a tiny system transaction whose receiver is the supplied text.

```http
POST /api/blocks
Content-Type: application/json

{
  "data": "Learn block hash"
}
```

## Code Structure

- `entity/Block.java`: block model, transaction list, hash calculation, mining, and tamper marker.
- `entity/Transaction.java`: transaction model, signing, signature verification, and mining reward transactions.
- `entity/Wallet.java`: public/private key pair response model.
- `service/BlockchainService.java`: in-memory chain, pending transaction pool, block mining, validation, difficulty, and reset logic.
- `controller/BlockchainController.java`: REST API for blocks, wallets, transactions, mining, and chain operations.
- `pkg/utils/StringUtil.java`: SHA-256 helper.
- `pkg/utils/CryptoUtil.java`: RSA wallet generation, signing, and signature verification.
- `pkg/validate/Validator.java`: chain, proof-of-work, previous hash, and transaction validation.
- `dto/*`: request/response records for the API.
- `BackendApplicationTests.java`: MockMvc tests for block, chain, wallet, transaction, and mining workflows.

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

### Section C: Simple Nodes and Sync

- [ ] Simulate multiple nodes in the same app or through multiple instances.
- [ ] API to register peers.
- [ ] API to fetch a chain from a peer.
- [ ] Rule to choose the longer valid chain.
- [ ] Conflict resolution demo.

### Section D: Code Quality and Observability

- [ ] Add a unified exception response format.
- [ ] Add request validation annotations.
- [ ] Add OpenAPI/Swagger.
- [ ] Add mining time and nonce count logs.
- [ ] Add optional database persistence.
- [ ] Add Dockerfile and docker-compose.
