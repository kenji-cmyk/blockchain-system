package com.kna.backend.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.kna.backend.dto.BroadcastResult;
import com.kna.backend.dto.NodeInfo;
import com.kna.backend.dto.PersistedPeer;
import com.kna.backend.dto.PeerHealth;
import com.kna.backend.dto.PeerInventory;
import com.kna.backend.dto.PeerInventoryResponse;
import com.kna.backend.dto.PeerSummary;
import com.kna.backend.dto.SyncResult;
import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.kna.backend.pkg.validate.Validator.cumulativeDifficulty;
import static com.kna.backend.pkg.validate.Validator.isChainValid;

@Service
public class PeerNodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerNodeService.class);
    private static final Type BLOCK_LIST_TYPE = new TypeToken<List<Block>>() {
    }.getType();

    private final BlockchainService blockchainService;
    private final ChainPersistenceService chainPersistenceService;
    private final OperationalMetricsService operationalMetricsService;
    private final Map<String, PeerNode> peers = new LinkedHashMap<>();
    private final Gson gson = new Gson();
    private final HttpClient httpClient;
    private final Duration timeout;
    private final int retryAttempts;
    private final String nodeId;
    private final int quarantineScore;
    private final Duration backoffDuration;
    private final int maxMessageBytes;
    private final boolean scheduledSyncEnabled;
    private final Set<String> seenGossipIds = new HashSet<>();
    private final List<String> localCapabilities = List.of(
            "node-info",
            "health-check",
            "peer-discovery",
            "scheduled-sync",
            "gossip-broadcast",
            "block-broadcast",
            "transaction-broadcast",
            "utxo-ledger",
            "consensus-policy",
            "orphan-reattach",
            "peer-inventory",
            "peer-quarantine"
    );

    public PeerNodeService(
            BlockchainService blockchainService,
            ChainPersistenceService chainPersistenceService,
            OperationalMetricsService operationalMetricsService,
            @Value("${blockchain.peer.timeout-ms:1500}") long timeoutMs,
            @Value("${blockchain.peer.retry-attempts:2}") int retryAttempts,
            @Value("${blockchain.node.id:}") String configuredNodeId,
            @Value("${blockchain.peer.quarantine-score:${blockchain.peer.eviction-score:-3}}") int quarantineScore,
            @Value("${blockchain.peer.backoff-ms:30000}") long backoffMs,
            @Value("${blockchain.peer.max-message-bytes:65536}") int maxMessageBytes,
            @Value("${blockchain.peer.scheduled-sync.enabled:false}") boolean scheduledSyncEnabled
    ) {
        this.blockchainService = blockchainService;
        this.chainPersistenceService = chainPersistenceService;
        this.operationalMetricsService = operationalMetricsService;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.retryAttempts = Math.max(1, retryAttempts);
        this.nodeId = configuredNodeId == null || configuredNodeId.isBlank()
                ? "node-" + UUID.randomUUID()
                : configuredNodeId.strip();
        this.quarantineScore = quarantineScore;
        this.backoffDuration = Duration.ofMillis(Math.max(1, backoffMs));
        this.maxMessageBytes = Math.max(128, maxMessageBytes);
        this.scheduledSyncEnabled = scheduledSyncEnabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        loadPersistedPeers();
    }

    public synchronized PeerSummary registerPeer(String peerId, String baseUrl) {
        validatePeerId(peerId);
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        PeerNode peer = new PeerNode(
                peerId,
                normalizedBaseUrl,
                normalizedBaseUrl == null ? new ArrayList<>(blockchainService.getBlocks()) : new ArrayList<>()
        );
        if (normalizedBaseUrl == null) {
            peer.recordHandshake(new NodeInfo(peerId, appVersion(), localCapabilities, peer.simulatedChain().size(), cumulativeDifficulty(peer.simulatedChain(), blockchainService.getDifficulty())));
        } else {
            handshake(peer);
        }
        peers.putIfAbsent(peerId, peer);
        savePeers();
        return toSummary(peerId);
    }

    public synchronized List<PeerSummary> discoverPeers(List<String> peerUrls) {
        if (peerUrls == null || peerUrls.isEmpty()) {
            throw new IllegalArgumentException("Peer URLs must not be empty");
        }
        return peerUrls.stream()
                .map(this::normalizeBaseUrl)
                .map(baseUrl -> registerPeer(peerIdFromUrl(baseUrl), baseUrl))
                .toList();
    }

    public synchronized List<PeerSummary> getPeers() {
        return peers.keySet().stream()
                .map(this::toSummary)
                .toList();
    }

    public synchronized int getPeerCount() {
        return peers.size();
    }

    public synchronized PeerSummary removePeer(String peerId) {
        validatePeerId(peerId);
        PeerNode removed = peers.remove(peerId);
        if (removed == null) {
            throw new IllegalArgumentException("Peer does not exist");
        }
        savePeers();
        return toSummary(removed);
    }

    public synchronized PeerHealth checkHealth(String peerId) {
        PeerNode peer = getPeer(peerId);
        return checkHealth(peer);
    }

    public synchronized List<Block> getPeerChain(String peerId) {
        PeerNode peer = getPeer(peerId);
        if (peer.isHttp()) {
            return fetchPeerChain(peer);
        }
        return List.copyOf(peer.simulatedChain());
    }

    public synchronized Block mineDemoBlock(String peerId, String minerAddress) {
        validateData(minerAddress, "Miner address must not be blank");

        PeerNode peer = getPeer(peerId);
        if (peer.isHttp()) {
            String response = postJson(peer.baseUrl() + "/api/blocks", "{\"data\":\"%s\"}".formatted(minerAddress));
            return gson.fromJson(response, Block.class);
        }

        List<Block> chain = peer.simulatedChain();
        Block previousBlock = chain.getLast();
        Transaction reward = Transaction.miningReward(minerAddress, blockchainService.getMiningReward());
        Block block = new Block(chain.size(), List.of(reward), previousBlock.getHash());
        long start = System.currentTimeMillis();
        int nonceBefore = block.getNonce();
        block.mineBlock(blockchainService.getDifficulty());
        long elapsedMs = System.currentTimeMillis() - start;
        int nonceCount = block.getNonce() - nonceBefore;
        chain.add(block);

        LOGGER.info(
                "Mined peer block peerId={} index={} difficulty={} nonceCount={} elapsedMs={} hash={}",
                peerId,
                block.getIndex(),
                blockchainService.getDifficulty(),
                nonceCount,
                elapsedMs,
                block.getHash()
        );

        return block;
    }

    public synchronized SyncResult syncFromPeer(String peerId) {
        PeerNode peer = getPeer(peerId);
        List<Block> peerChain = peer.isHttp() ? fetchPeerChain(peer) : peer.simulatedChain();
        int localSizeBefore = blockchainService.getBlocks().size();
        boolean peerValid = isChainValid(
                peerChain,
                blockchainService.getDifficulty(),
                blockchainService.getMaxTransactionsPerBlock(),
                blockchainService.getMiningReward()
        );
        boolean adopted = blockchainService.replaceChainIfStrongerAndValid(peerChain);
        int localSizeAfter = blockchainService.getBlocks().size();

        String message;
        if (adopted) {
            message = "Local chain replaced with stronger valid peer chain";
        } else if (!peerValid) {
            message = "Peer chain is invalid";
        } else if (cumulativeDifficulty(peerChain, blockchainService.getDifficulty()) <= blockchainService.getCumulativeDifficulty()) {
            message = "Local chain has at least as much cumulative difficulty as the peer chain";
        } else {
            message = "Peer chain was not adopted";
        }

        operationalMetricsService.recordPeerSync(peerValid, adopted);
        LOGGER.info(
                "event=peer_sync peerId={} peerChainSize={} localSizeBefore={} localSizeAfter={} peerValid={} adopted={}",
                peerId,
                peerChain.size(),
                localSizeBefore,
                localSizeAfter,
                peerValid,
                adopted
        );
        return new SyncResult(peerId, peerChain.size(), localSizeBefore, localSizeAfter, peerValid, adopted, message);
    }

    public synchronized BroadcastResult broadcastTransaction(Transaction transaction) {
        return broadcastJson("/api/transactions/broadcast", gson.toJson(transaction));
    }

    public synchronized BroadcastResult broadcastBlock(Block block) {
        return broadcastJson("/api/blocks/broadcast", gson.toJson(block));
    }

    public synchronized NodeInfo getNodeInfo() {
        return new NodeInfo(
                nodeId,
                appVersion(),
                localCapabilities,
                blockchainService.getBlocks().size(),
                blockchainService.getCumulativeDifficulty()
        );
    }

    public synchronized PeerInventory getInventory() {
        return new PeerInventory(
                blockchainService.getBlocks()
                        .stream()
                        .map(Block::getHash)
                        .toList(),
                blockchainService.getPendingTransactions()
                        .stream()
                        .map(Transaction::getTransactionId)
                        .toList()
        );
    }

    public synchronized PeerInventoryResponse acceptInventory(PeerInventory inventory) {
        List<String> missingBlockHashes = safeInventoryIds(inventory == null ? null : inventory.blockHashes())
                .stream()
                .filter(hash -> !isCommittedBlock(hash))
                .toList();
        List<String> missingTransactionIds = safeInventoryIds(inventory == null ? null : inventory.transactionIds())
                .stream()
                .filter(transactionId -> !isKnownTransaction(transactionId))
                .toList();
        return new PeerInventoryResponse(missingBlockHashes, missingTransactionIds);
    }

    public synchronized Transaction acceptBroadcastTransaction(String body, String gossipId) {
        validatePeerMessage(body, gossipId);
        try {
            Transaction transaction = gson.fromJson(body, Transaction.class);
            if (transaction == null) {
                throw new IllegalArgumentException("Malformed peer message");
            }
            Transaction acceptedTransaction = blockchainService.addPendingTransaction(transaction);
            broadcastJson("/api/transactions/broadcast", body, gossipIdOrNew(gossipId));
            return acceptedTransaction;
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Malformed peer message", exception);
        }
    }

    public synchronized boolean acceptBroadcastBlock(String body, String gossipId) {
        validatePeerMessage(body, gossipId);
        try {
            Block block = gson.fromJson(body, Block.class);
            if (block == null) {
                throw new IllegalArgumentException("Malformed peer message");
            }
            if (block.getIndex() < blockchainService.getBlocks().size()
                    || isCommittedBlock(block.getHash())) {
                operationalMetricsService.recordBroadcastBlockRejected();
                LOGGER.warn("event=block_rejected source=peer reason=\"stale or duplicate\" index={} hash={}", block.getIndex(), block.getHash());
                return false;
            }
            boolean accepted = blockchainService.acceptBroadcastBlock(block);
            if (accepted) {
                broadcastJson("/api/blocks/broadcast", body, gossipIdOrNew(gossipId));
            }
            return accepted;
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Malformed peer message", exception);
        }
    }

    public synchronized void resetPeers() {
        peers.clear();
        savePeers();
    }

    @Scheduled(fixedDelayString = "${blockchain.peer.scheduled-sync.interval-ms:30000}")
    public void scheduledPeerRefreshAndSync() {
        if (!scheduledSyncEnabled) {
            return;
        }

        List<String> peerIds;
        synchronized (this) {
            peerIds = List.copyOf(peers.keySet());
        }
        for (String peerId : peerIds) {
            try {
                checkHealth(peerId);
                syncFromPeer(peerId);
            } catch (IllegalArgumentException exception) {
                LOGGER.debug("Scheduled peer refresh skipped peerId={}", peerId, exception);
            }
        }
    }

    private BroadcastResult broadcastJson(String path, String body) {
        return broadcastJson(path, body, UUID.randomUUID().toString());
    }

    private BroadcastResult broadcastJson(String path, String body, String gossipId) {
        int peerCount = 0;
        int successCount = 0;
        for (PeerNode peer : peers.values()) {
            if (!peer.isHttp()) {
                continue;
            }
            peerCount++;
            if (!peer.canAttempt(Instant.now())) {
                LOGGER.info("Skipped quarantined peer broadcast peerId={} path={} backoffUntil={}", peer.peerId(), path, peer.backoffUntilText());
                continue;
            }
            try {
                if (peerNeedsPayload(peer, path, body, gossipId)) {
                    postJson(peer.baseUrl() + path, body, gossipId);
                }
                successCount++;
                recordSuccess(peer);
            } catch (IllegalArgumentException exception) {
                recordFailure(peer);
                quarantineIfUnhealthy(peer);
                LOGGER.warn("Could not broadcast {} to peer {}", path, peer.peerId(), exception);
            }
        }
        int failureCount = peerCount - successCount;
        operationalMetricsService.recordBroadcast(path, peerCount, successCount, failureCount);
        LOGGER.info(
                "event=peer_broadcast path={} peerCount={} successCount={} failureCount={}",
                path,
                peerCount,
                successCount,
                failureCount
        );
        return new BroadcastResult(peerCount, successCount, failureCount);
    }

    private String gossipIdOrNew(String gossipId) {
        return gossipId == null || gossipId.isBlank() ? UUID.randomUUID().toString() : gossipId;
    }

    private PeerSummary toSummary(String peerId) {
        return toSummary(getPeer(peerId));
    }

    private PeerSummary toSummary(PeerNode peer) {
        if (peer.isHttp()) {
            PeerHealth health = checkHealth(peer);
            int chainSize = 0;
            boolean valid = false;
            if (health.healthy()) {
                try {
                    com.kna.backend.dto.BlockchainStatus status = gson.fromJson(health.message(), com.kna.backend.dto.BlockchainStatus.class);
                    chainSize = status.size();
                    valid = status.valid();
                } catch (Exception exception) {
                    LOGGER.warn("Could not parse peer health status for {}", peer.peerId(), exception);
                }
            }
            return new PeerSummary(
                    peer.peerId(),
                    chainSize,
                    valid,
                    peer.baseUrl(),
                    health.healthy(),
                    "http",
                    peer.nodeId(),
                    peer.capabilities(),
                    peer.score(),
                    peer.failureCount(),
                    peer.lastSeenAtText(),
                    peer.stateText(),
                    peer.backoffUntilText(),
                    peer.lastLatencyMs()
            );
        }

        List<Block> chain = peer.simulatedChain();
        return new PeerSummary(
                peer.peerId(),
                chain.size(),
                isChainValid(
                        chain,
                        blockchainService.getDifficulty(),
                        blockchainService.getMaxTransactionsPerBlock(),
                        blockchainService.getMiningReward()
                ),
                null,
                true,
                "simulated",
                peer.nodeId(),
                peer.capabilities(),
                peer.score(),
                peer.failureCount(),
                peer.lastSeenAtText(),
                peer.stateText(),
                peer.backoffUntilText(),
                peer.lastLatencyMs()
        );
    }

    private PeerHealth checkHealth(PeerNode peer) {
        if (!peer.isHttp()) {
            return new PeerHealth(peer.peerId(), null, true, "Simulated peer is healthy");
        }

        Instant now = Instant.now();
        if (!peer.canAttempt(now)) {
            return new PeerHealth(
                    peer.peerId(),
                    peer.baseUrl(),
                    false,
                    "Peer is quarantined until " + peer.backoffUntilText()
            );
        }
        if (peer.isQuarantined()) {
            peer.beginRecovery();
        }

        try {
            long start = System.currentTimeMillis();
            String response = getString(peer.baseUrl() + "/api/chain/status");
            peer.recordLatency(System.currentTimeMillis() - start);
            recordSuccess(peer);
            return new PeerHealth(peer.peerId(), peer.baseUrl(), true, response);
        } catch (IllegalArgumentException exception) {
            recordFailure(peer);
            PeerHealth health = new PeerHealth(peer.peerId(), peer.baseUrl(), false, exception.getMessage());
            quarantineIfUnhealthy(peer);
            return health;
        }
    }

    private List<Block> fetchPeerChain(PeerNode peer) {
        String response = getString(peer.baseUrl() + "/api/blocks");
        List<Block> chain = gson.fromJson(response, BLOCK_LIST_TYPE);
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("Peer returned an empty chain");
        }
        return chain;
    }

    private String getString(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        return sendWithRetry(request);
    }

    private String postJson(String url, String body, String gossipId) {
        if (body != null && body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxMessageBytes) {
            throw new IllegalArgumentException("Peer message exceeds " + maxMessageBytes + " bytes");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("X-Node-Id", nodeId)
                .header("X-Gossip-Id", gossipId)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendWithRetry(request);
    }

    private String postJson(String url, String body) {
        return postJson(url, body, UUID.randomUUID().toString());
    }

    private String sendWithRetry(HttpRequest request) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                long start = System.currentTimeMillis();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    operationalMetricsService.recordPeerLatency(System.currentTimeMillis() - start);
                    return response.body();
                }
                throw new IllegalArgumentException("Peer returned HTTP " + response.statusCode());
            } catch (Exception exception) {
                lastException = exception;
                if (attempt < retryAttempts) {
                    operationalMetricsService.recordPeerRetry();
                }
            }
        }
        throw new IllegalArgumentException("Peer request failed: " + lastException.getMessage(), lastException);
    }

    private void handshake(PeerNode peer) {
        try {
            String response = getString(peer.baseUrl() + "/api/node/info");
            NodeInfo nodeInfo = gson.fromJson(response, NodeInfo.class);
            if (nodeInfo == null || nodeInfo.nodeId() == null || nodeInfo.nodeId().isBlank()) {
                throw new IllegalArgumentException("Peer node info is invalid");
            }
            peer.recordHandshake(nodeInfo);
            recordSuccess(peer);
        } catch (RuntimeException exception) {
            LOGGER.warn("Peer handshake failed peerId={}", peer.peerId(), exception);
            recordFailure(peer);
        }
    }

    private void validatePeerMessage(String body, String gossipId) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Malformed peer message");
        }
        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxMessageBytes) {
            throw new PeerMessageTooLargeException("Peer message exceeds " + maxMessageBytes + " bytes");
        }
        if (gossipId != null && !gossipId.isBlank() && !seenGossipIds.add(gossipId)) {
            operationalMetricsService.recordDuplicateGossipMessage();
            throw new DuplicatePeerMessageException("Duplicate peer gossip message");
        }
    }

    private boolean isCommittedBlock(String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        return blockchainService.getBlocks()
                .stream()
                .anyMatch(block -> block.getHash().equals(hash));
    }

    private boolean isKnownTransaction(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return false;
        }
        boolean pending = blockchainService.getPendingTransactions()
                .stream()
                .anyMatch(transaction -> transaction.getTransactionId().equals(transactionId));
        if (pending) {
            return true;
        }
        return blockchainService.getBlocks()
                .stream()
                .flatMap(block -> block.getTransactions().stream())
                .anyMatch(transaction -> transaction.getTransactionId().equals(transactionId));
    }

    private void recordSuccess(PeerNode peer) {
        peer.recordSuccess();
    }

    private void recordFailure(PeerNode peer) {
        peer.recordFailure();
    }

    private void quarantineIfUnhealthy(PeerNode peer) {
        if (peer.score() <= quarantineScore) {
            peer.quarantine(backoffDuration);
            savePeers();
            LOGGER.warn("Quarantined unhealthy peer peerId={} score={} backoffUntil={}", peer.peerId(), peer.score(), peer.backoffUntilText());
        }
    }

    private boolean peerNeedsPayload(PeerNode peer, String path, String body, String gossipId) {
        PeerInventory inventory = inventoryForPayload(path, body);
        if (inventory == null) {
            return true;
        }
        try {
            String response = postJson(peer.baseUrl() + "/api/peers/inventory", gson.toJson(inventory), gossipIdOrNew(gossipId));
            PeerInventoryResponse inventoryResponse = gson.fromJson(response, PeerInventoryResponse.class);
            if (inventoryResponse == null) {
                return true;
            }
            if (!inventory.blockHashes().isEmpty()) {
                String blockHash = inventory.blockHashes().getFirst();
                return safeInventoryIds(inventoryResponse.missingBlockHashes()).contains(blockHash);
            }
            if (!inventory.transactionIds().isEmpty()) {
                String transactionId = inventory.transactionIds().getFirst();
                return safeInventoryIds(inventoryResponse.missingTransactionIds()).contains(transactionId);
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.debug("Peer inventory preflight failed peerId={} path={}", peer.peerId(), path, exception);
            return true;
        }
    }

    private PeerInventory inventoryForPayload(String path, String body) {
        try {
            if ("/api/blocks/broadcast".equals(path)) {
                Block block = gson.fromJson(body, Block.class);
                if (block == null || block.getHash() == null || block.getHash().isBlank()) {
                    return null;
                }
                return new PeerInventory(List.of(block.getHash()), List.of());
            }
            if ("/api/transactions/broadcast".equals(path)) {
                Transaction transaction = gson.fromJson(body, Transaction.class);
                if (transaction == null || transaction.getTransactionId() == null || transaction.getTransactionId().isBlank()) {
                    return null;
                }
                return new PeerInventory(List.of(), List.of(transaction.getTransactionId()));
            }
            return null;
        } catch (JsonSyntaxException exception) {
            return null;
        }
    }

    private List<String> safeInventoryIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private String appVersion() {
        Package packageInfo = PeerNodeService.class.getPackage();
        String version = packageInfo == null ? null : packageInfo.getImplementationVersion();
        return version == null || version.isBlank() ? "0.0.1" : version;
    }

    private PeerNode getPeer(String peerId) {
        validatePeerId(peerId);
        PeerNode peer = peers.get(peerId);
        if (peer == null) {
            throw new IllegalArgumentException("Peer does not exist");
        }
        return peer;
    }

    private void validatePeerId(String peerId) {
        validateData(peerId, "Peer id must not be blank");
    }

    private void validateData(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Peer base URL must be absolute");
        }
        return normalized;
    }

    private String peerIdFromUrl(String baseUrl) {
        URI uri = URI.create(baseUrl);
        int port = uri.getPort();
        String portText = port == -1 ? "" : "-" + port;
        return (uri.getHost() + portText).replace(".", "-");
    }

    private void loadPersistedPeers() {
        chainPersistenceService.loadPeers()
                .orElse(List.of())
                .forEach(peer -> {
                    PeerNode node = new PeerNode(
                            peer.peerId(),
                            peer.baseUrl(),
                            peer.baseUrl() == null ? new ArrayList<>(blockchainService.getBlocks()) : new ArrayList<>()
                    );
                    if (peer.baseUrl() == null) {
                        node.recordHandshake(new NodeInfo(peer.peerId(), appVersion(), localCapabilities, node.simulatedChain().size(), cumulativeDifficulty(node.simulatedChain(), blockchainService.getDifficulty())));
                    }
                    peers.put(peer.peerId(), node);
                });
    }

    private void savePeers() {
        List<PersistedPeer> persistedPeers = peers.values()
                .stream()
                .map(peer -> new PersistedPeer(peer.peerId(), peer.baseUrl()))
                .toList();
        chainPersistenceService.savePeers(persistedPeers);
    }

    public static class PeerMessageTooLargeException extends IllegalArgumentException {
        public PeerMessageTooLargeException(String message) {
            super(message);
        }
    }

    public static class DuplicatePeerMessageException extends IllegalArgumentException {
        public DuplicatePeerMessageException(String message) {
            super(message);
        }
    }

    private static final class PeerNode {
        private final String peerId;
        private final String baseUrl;
        private final List<Block> simulatedChain;
        private String nodeId;
        private List<String> capabilities = List.of();
        private int score;
        private int failureCount;
        private Instant lastSeenAt;
        private PeerState state = PeerState.ACTIVE;
        private Instant backoffUntil;
        private Long lastLatencyMs;
        private int recoverySuccessCount;

        private PeerNode(String peerId, String baseUrl, List<Block> simulatedChain) {
            this.peerId = peerId;
            this.baseUrl = baseUrl;
            this.simulatedChain = simulatedChain;
            this.nodeId = peerId;
        }

        boolean isHttp() {
            return baseUrl != null;
        }

        void recordHandshake(NodeInfo nodeInfo) {
            this.nodeId = nodeInfo.nodeId();
            this.capabilities = List.copyOf(nodeInfo.capabilities() == null ? List.of() : nodeInfo.capabilities());
            this.lastSeenAt = Instant.now();
        }

        void recordSuccess() {
            score++;
            lastSeenAt = Instant.now();
            failureCount = 0;
            if (state == PeerState.RECOVERING) {
                recoverySuccessCount++;
                if (recoverySuccessCount >= 2) {
                    state = PeerState.ACTIVE;
                    backoffUntil = null;
                }
                return;
            }
            state = PeerState.ACTIVE;
            backoffUntil = null;
        }

        void recordFailure() {
            score--;
            failureCount++;
        }

        void quarantine(Duration backoffDuration) {
            state = PeerState.QUARANTINED;
            backoffUntil = Instant.now().plus(backoffDuration);
            recoverySuccessCount = 0;
        }

        boolean canAttempt(Instant now) {
            return state != PeerState.QUARANTINED
                    || backoffUntil == null
                    || !now.isBefore(backoffUntil);
        }

        boolean isQuarantined() {
            return state == PeerState.QUARANTINED;
        }

        void beginRecovery() {
            state = PeerState.RECOVERING;
            recoverySuccessCount = 0;
        }

        void recordLatency(long elapsedMs) {
            lastLatencyMs = Math.max(0, elapsedMs);
        }

        String peerId() {
            return peerId;
        }

        String baseUrl() {
            return baseUrl;
        }

        List<Block> simulatedChain() {
            return simulatedChain;
        }

        String nodeId() {
            return nodeId;
        }

        List<String> capabilities() {
            return List.copyOf(capabilities);
        }

        int score() {
            return score;
        }

        int failureCount() {
            return failureCount;
        }

        String lastSeenAtText() {
            return lastSeenAt == null ? null : lastSeenAt.toString();
        }

        String stateText() {
            return state.value();
        }

        String backoffUntilText() {
            return backoffUntil == null ? null : backoffUntil.toString();
        }

        Long lastLatencyMs() {
            return lastLatencyMs;
        }
    }

    private enum PeerState {
        ACTIVE("active"),
        QUARANTINED("quarantined"),
        RECOVERING("recovering");

        private final String value;

        PeerState(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
