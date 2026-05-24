# Blockchain Learning Backend

This Spring Boot backend demonstrates a simple in-memory blockchain for learning core concepts: blocks, hashes, previous hashes, nonce, proof-of-work mining, chain validation, signed transactions, wallets, a pending transaction pool, mining rewards, peer nodes, and basic chain synchronization.

## Current Status

- Runtime: Java 25, Spring Boot 4.0.6, Maven.
- Storage: in-memory by default. Optional file or H2 database persistence can be enabled with configuration.
- Block data: each block contains a list of `Transaction` objects.
- Wallets: generated RSA public/private key pairs returned as Base64 strings.
- Transactions: sender, receiver, amount, timestamp, transaction id, and digital signature.
- Consensus checks: each block hash must match its content, each `previousHash` must link to the previous block, every hash must satisfy the configured proof-of-work difficulty, and every transaction signature must be valid.
- Genesis block: created automatically on startup and when the reset API is called.
- Peer sync: simulated peers run inside the same application. A peer has its own in-memory chain and can be used to demo conflict resolution without starting multiple app instances.
- Error handling: API errors return a unified JSON format.
- Observability: mining logs include block source, index, difficulty, nonce count, elapsed time, and hash.
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

Run with Docker Compose from the repository root:

```bash
docker compose up --build
```

The Docker Compose setup enables H2 database persistence and stores data in a Docker volume.

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

### OpenAPI Document

```http
GET /api/docs/openapi
```

This returns an OpenAPI 3.0 JSON document for the available learning APIs.

### Register a Simulated Peer

This creates a peer node inside the same application and initializes its chain from the current local chain.

```http
POST /api/peers
Content-Type: application/json

{
  "peerId": "node-b"
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

### Fetch a Peer Chain

```http
GET /api/peers/{peerId}/chain
```

### Mine a Demo Block on a Peer

This mines a reward-only block on the selected simulated peer. It is useful for making the peer chain longer than the local chain so conflict resolution can be tested.

```http
POST /api/peers/{peerId}/blocks
Content-Type: application/json

{
  "minerAddress": "BASE64_PUBLIC_KEY"
}
```

### Sync From a Peer

This compares the local chain with the selected peer chain. The local chain is replaced only when the peer chain is both valid and longer.

```http
POST /api/peers/{peerId}/sync
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

### Legacy Demo Block Endpoint

This endpoint is kept for the basic learning flow from section A. It mines a new block with a tiny system transaction whose receiver is the supplied text.

```http
POST /api/blocks
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

Only the chain is persisted. Pending transactions and simulated peers remain in-memory learning state.

## Code Structure

- `entity/Block.java`: block model, transaction list, hash calculation, mining, and tamper marker.
- `entity/Transaction.java`: transaction model, signing, signature verification, and mining reward transactions.
- `entity/Wallet.java`: public/private key pair response model.
- `service/BlockchainService.java`: in-memory chain, pending transaction pool, block mining, validation, difficulty, and reset logic.
- `service/ChainPersistenceService.java`: optional file or H2 database persistence for the chain.
- `service/PeerNodeService.java`: simulated peer nodes, peer chains, peer mining, and conflict resolution.
- `controller/BlockchainController.java`: REST API for blocks, wallets, transactions, mining, and chain operations.
- `controller/ApiExceptionHandler.java`: unified API error responses.
- `controller/OpenApiController.java`: OpenAPI document endpoint.
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

- [x] Simulate multiple nodes in the same app or through multiple instances.
- [x] API to register peers.
- [x] API to fetch a chain from a peer.
- [x] Rule to choose the longer valid chain.
- [x] Conflict resolution demo.

### Section D: Code Quality and Observability

- [x] Add a unified exception response format.
- [x] Add request validation annotations.
- [x] Add OpenAPI/Swagger.
- [x] Add mining time and nonce count logs.
- [x] Add optional database persistence.
- [x] Add Dockerfile and docker-compose.
