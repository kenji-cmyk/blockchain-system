package com.kna.backend.controller;

import com.kna.backend.dto.AddBlockRequest;
import com.kna.backend.dto.ApiEnvelope;
import com.kna.backend.dto.ApiMessage;
import com.kna.backend.dto.BlockReference;
import com.kna.backend.dto.BlockchainStatus;
import com.kna.backend.dto.BroadcastBlockResult;
import com.kna.backend.dto.BroadcastResult;
import com.kna.backend.dto.ConsensusBranch;
import com.kna.backend.dto.ConsensusSettings;
import com.kna.backend.dto.ConsensusSettingsRequest;
import com.kna.backend.dto.CreateTransactionRequest;
import com.kna.backend.dto.DifficultyRequest;
import com.kna.backend.dto.MinePeerBlockRequest;
import com.kna.backend.dto.MineTransactionsRequest;
import com.kna.backend.dto.NodeInfo;
import com.kna.backend.dto.OperationHealth;
import com.kna.backend.dto.OperationMetrics;
import com.kna.backend.dto.PeerDiscoveryRequest;
import com.kna.backend.dto.PeerHealth;
import com.kna.backend.dto.PeerInventory;
import com.kna.backend.dto.PeerInventoryResponse;
import com.kna.backend.dto.PeerSummary;
import com.kna.backend.dto.RegisterPeerRequest;
import com.kna.backend.dto.SyncResult;
import com.kna.backend.dto.TamperBlockRequest;
import com.kna.backend.dto.WalletBalance;
import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.Wallet;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class BlockchainV1Controller {

    private final BlockchainController controller;

    public BlockchainV1Controller(BlockchainController controller) {
        this.controller = controller;
    }

    @GetMapping("/blocks")
    public ApiEnvelope<List<Block>> getBlocks() {
        List<Block> blocks = controller.getBlocks();
        return ApiEnvelope.ok(blocks, Map.of("count", blocks.size()));
    }

    @GetMapping("/blocks/{index}")
    public ApiEnvelope<Block> getBlock(@PathVariable int index) {
        return ApiEnvelope.ok(controller.getBlock(index));
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<Block> addBlock(@Valid @RequestBody AddBlockRequest request) {
        return ApiEnvelope.ok(controller.addBlock(request));
    }

    @PostMapping("/blocks/broadcast")
    public ApiEnvelope<BroadcastBlockResult> acceptBroadcastBlock(
            @RequestBody String body,
            @RequestHeader(value = "X-Gossip-Id", required = false) String gossipId
    ) {
        return ApiEnvelope.ok(controller.acceptBroadcastBlock(body, gossipId));
    }

    @GetMapping("/node/info")
    public ApiEnvelope<NodeInfo> getNodeInfo() {
        return ApiEnvelope.ok(controller.getNodeInfo());
    }

    @GetMapping("/wallets/new")
    public ApiEnvelope<Wallet> createWallet() {
        return ApiEnvelope.ok(controller.createWallet());
    }

    @GetMapping("/wallets/{address}/balance")
    public ApiEnvelope<WalletBalance> getWalletBalance(@PathVariable String address) {
        return ApiEnvelope.ok(controller.getWalletBalance(address));
    }

    @GetMapping("/transactions/pending")
    public ApiEnvelope<List<Transaction>> getPendingTransactions() {
        List<Transaction> transactions = controller.getPendingTransactions();
        return ApiEnvelope.ok(transactions, Map.of("count", transactions.size()));
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<Transaction> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return ApiEnvelope.ok(controller.createTransaction(request));
    }

    @PostMapping("/transactions/broadcast")
    public ApiEnvelope<Transaction> acceptBroadcastTransaction(
            @RequestBody String body,
            @RequestHeader(value = "X-Gossip-Id", required = false) String gossipId
    ) {
        return ApiEnvelope.ok(controller.acceptBroadcastTransaction(body, gossipId));
    }

    @PostMapping("/transactions/mine")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<Block> minePendingTransactions(@Valid @RequestBody MineTransactionsRequest request) {
        return ApiEnvelope.ok(controller.minePendingTransactions(request));
    }

    @PostMapping("/peers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<PeerSummary> registerPeer(@Valid @RequestBody RegisterPeerRequest request) {
        return ApiEnvelope.ok(controller.registerPeer(request));
    }

    @PostMapping("/peers/discover")
    public ApiEnvelope<List<PeerSummary>> discoverPeers(@Valid @RequestBody PeerDiscoveryRequest request) {
        List<PeerSummary> peers = controller.discoverPeers(request);
        return ApiEnvelope.ok(peers, Map.of("count", peers.size()));
    }

    @GetMapping("/peers")
    public ApiEnvelope<List<PeerSummary>> getPeers() {
        List<PeerSummary> peers = controller.getPeers();
        return ApiEnvelope.ok(peers, Map.of("count", peers.size()));
    }

    @GetMapping("/peers/inventory")
    public ApiEnvelope<PeerInventory> getPeerInventory() {
        return ApiEnvelope.ok(controller.getPeerInventory());
    }

    @PostMapping("/peers/inventory")
    public ApiEnvelope<PeerInventoryResponse> acceptPeerInventory(@RequestBody PeerInventory inventory) {
        return ApiEnvelope.ok(controller.acceptPeerInventory(inventory));
    }

    @GetMapping("/peers/{peerId}/health")
    public ApiEnvelope<PeerHealth> checkPeerHealth(@PathVariable String peerId) {
        return ApiEnvelope.ok(controller.checkPeerHealth(peerId));
    }

    @DeleteMapping("/peers/{peerId}")
    public ApiEnvelope<PeerSummary> removePeer(@PathVariable String peerId) {
        return ApiEnvelope.ok(controller.removePeer(peerId));
    }

    @PostMapping("/peers/broadcast/transactions")
    public ApiEnvelope<BroadcastResult> broadcastPendingTransactions() {
        return ApiEnvelope.ok(controller.broadcastPendingTransactions());
    }

    @GetMapping("/peers/{peerId}/chain")
    public ApiEnvelope<List<Block>> getPeerChain(@PathVariable String peerId) {
        List<Block> blocks = controller.getPeerChain(peerId);
        return ApiEnvelope.ok(blocks, Map.of("count", blocks.size()));
    }

    @PostMapping("/peers/{peerId}/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<Block> minePeerDemoBlock(
            @PathVariable String peerId,
            @Valid @RequestBody MinePeerBlockRequest request
    ) {
        return ApiEnvelope.ok(controller.minePeerDemoBlock(peerId, request));
    }

    @PostMapping("/peers/{peerId}/sync")
    public ApiEnvelope<SyncResult> syncFromPeer(@PathVariable String peerId) {
        return ApiEnvelope.ok(controller.syncFromPeer(peerId));
    }

    @GetMapping("/chain/validate")
    public ApiEnvelope<BlockchainStatus> validateChain() {
        return ApiEnvelope.ok(controller.validateChain());
    }

    @GetMapping("/chain/status")
    public ApiEnvelope<BlockchainStatus> getStatus() {
        return ApiEnvelope.ok(controller.getStatus());
    }

    @GetMapping("/chain/forks")
    public ApiEnvelope<List<BlockReference>> getForkBlocks() {
        List<BlockReference> blocks = controller.getForkBlocks();
        return ApiEnvelope.ok(blocks, Map.of("count", blocks.size()));
    }

    @GetMapping("/chain/orphans")
    public ApiEnvelope<List<BlockReference>> getOrphanBlocks() {
        List<BlockReference> blocks = controller.getOrphanBlocks();
        return ApiEnvelope.ok(blocks, Map.of("count", blocks.size()));
    }

    @GetMapping("/chain/consensus")
    public ApiEnvelope<ConsensusSettings> getConsensusSettings() {
        return ApiEnvelope.ok(controller.getConsensusSettings());
    }

    @PutMapping("/chain/consensus")
    public ApiEnvelope<ConsensusSettings> updateConsensusSettings(@Valid @RequestBody ConsensusSettingsRequest request) {
        return ApiEnvelope.ok(controller.updateConsensusSettings(request));
    }

    @GetMapping("/chain/branches")
    public ApiEnvelope<List<ConsensusBranch>> getConsensusBranches() {
        List<ConsensusBranch> branches = controller.getConsensusBranches();
        return ApiEnvelope.ok(branches, Map.of("count", branches.size()));
    }

    @GetMapping("/ops/health")
    public ApiEnvelope<OperationHealth> getHealth() {
        return ApiEnvelope.ok(controller.getHealth());
    }

    @GetMapping("/ops/metrics")
    public ApiEnvelope<OperationMetrics> getMetrics() {
        return ApiEnvelope.ok(controller.getMetrics());
    }

    @PutMapping("/chain/difficulty")
    public ApiEnvelope<BlockchainStatus> updateDifficulty(@Valid @RequestBody DifficultyRequest request) {
        return ApiEnvelope.ok(controller.updateDifficulty(request));
    }

    @PostMapping("/chain/tamper")
    public ApiEnvelope<BlockchainStatus> tamperChain(@Valid @RequestBody TamperBlockRequest request) {
        return ApiEnvelope.ok(controller.tamperChain(request));
    }

    @PostMapping("/chain/reset")
    public ApiEnvelope<ApiMessage> resetChain() {
        return ApiEnvelope.ok(controller.resetChain());
    }
}
