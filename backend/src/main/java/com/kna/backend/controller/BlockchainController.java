package com.kna.backend.controller;

import com.kna.backend.dto.AddBlockRequest;
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
import com.kna.backend.service.BlockchainService;
import com.kna.backend.service.OperationalMetricsService;
import com.kna.backend.service.PeerNodeService;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BlockchainController {

    private final BlockchainService blockchainService;
    private final PeerNodeService peerNodeService;
    private final OperationalMetricsService operationalMetricsService;

    public BlockchainController(
            BlockchainService blockchainService,
            PeerNodeService peerNodeService,
            OperationalMetricsService operationalMetricsService
    ) {
        this.blockchainService = blockchainService;
        this.peerNodeService = peerNodeService;
        this.operationalMetricsService = operationalMetricsService;
    }

    @GetMapping("/blocks")
    public List<Block> getBlocks() {
        return blockchainService.getBlocks();
    }

    @GetMapping("/blocks/{index}")
    public Block getBlock(@PathVariable int index) {
        try {
            return blockchainService.getBlock(index);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public Block addBlock(@Valid @RequestBody AddBlockRequest request) {
        try {
            Block block = blockchainService.addBlock(request.data());
            peerNodeService.broadcastBlock(block);
            return block;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/blocks/broadcast")
    public BroadcastBlockResult acceptBroadcastBlock(
            @RequestBody String body,
            @RequestHeader(value = "X-Gossip-Id", required = false) String gossipId
    ) {
        try {
            return new BroadcastBlockResult(peerNodeService.acceptBroadcastBlock(body, gossipId));
        } catch (PeerNodeService.PeerMessageTooLargeException exception) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, exception.getMessage(), exception);
        } catch (PeerNodeService.DuplicatePeerMessageException exception) {
            return new BroadcastBlockResult(false);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/node/info")
    public NodeInfo getNodeInfo() {
        return peerNodeService.getNodeInfo();
    }

    @GetMapping("/wallets/new")
    public Wallet createWallet() {
        return blockchainService.createWallet();
    }

    @GetMapping("/wallets/{address}/balance")
    public WalletBalance getWalletBalance(@PathVariable String address) {
        try {
            return new WalletBalance(address, blockchainService.getAvailableBalance(address));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/transactions/pending")
    public List<Transaction> getPendingTransactions() {
        return blockchainService.getPendingTransactions();
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        try {
            Transaction transaction = blockchainService.createTransaction(
                    request.sender(),
                    request.receiver(),
                    request.amount(),
                    request.fee(),
                    request.privateKey()
            );
            peerNodeService.broadcastTransaction(transaction);
            return transaction;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/transactions/broadcast")
    public Transaction acceptBroadcastTransaction(
            @RequestBody String body,
            @RequestHeader(value = "X-Gossip-Id", required = false) String gossipId
    ) {
        try {
            return peerNodeService.acceptBroadcastTransaction(body, gossipId);
        } catch (PeerNodeService.PeerMessageTooLargeException exception) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, exception.getMessage(), exception);
        } catch (PeerNodeService.DuplicatePeerMessageException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/transactions/mine")
    @ResponseStatus(HttpStatus.CREATED)
    public Block minePendingTransactions(@Valid @RequestBody MineTransactionsRequest request) {
        try {
            Block block = blockchainService.minePendingTransactions(request.rewardAddress());
            peerNodeService.broadcastBlock(block);
            return block;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/peers")
    @ResponseStatus(HttpStatus.CREATED)
    public PeerSummary registerPeer(@Valid @RequestBody RegisterPeerRequest request) {
        try {
            return peerNodeService.registerPeer(request.peerId(), request.baseUrl());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/peers/discover")
    public List<PeerSummary> discoverPeers(@Valid @RequestBody PeerDiscoveryRequest request) {
        try {
            return peerNodeService.discoverPeers(request.peerUrls());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/peers")
    public List<PeerSummary> getPeers() {
        return peerNodeService.getPeers();
    }

    @GetMapping("/peers/inventory")
    public PeerInventory getPeerInventory() {
        return peerNodeService.getInventory();
    }

    @PostMapping("/peers/inventory")
    public PeerInventoryResponse acceptPeerInventory(@RequestBody PeerInventory inventory) {
        return peerNodeService.acceptInventory(inventory);
    }

    @GetMapping("/peers/{peerId}/health")
    public PeerHealth checkPeerHealth(@PathVariable String peerId) {
        try {
            return peerNodeService.checkHealth(peerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/peers/{peerId}")
    public PeerSummary removePeer(@PathVariable String peerId) {
        try {
            return peerNodeService.removePeer(peerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/peers/broadcast/transactions")
    public BroadcastResult broadcastPendingTransactions() {
        BroadcastResult result = new BroadcastResult(0, 0, 0);
        for (Transaction transaction : blockchainService.getPendingTransactions()) {
            BroadcastResult next = peerNodeService.broadcastTransaction(transaction);
            result = new BroadcastResult(
                    result.peerCount() + next.peerCount(),
                    result.successCount() + next.successCount(),
                    result.failureCount() + next.failureCount()
            );
        }
        return result;
    }

    @GetMapping("/peers/{peerId}/chain")
    public List<Block> getPeerChain(@PathVariable String peerId) {
        try {
            return peerNodeService.getPeerChain(peerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/peers/{peerId}/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public Block minePeerDemoBlock(
            @PathVariable String peerId,
            @Valid @RequestBody MinePeerBlockRequest request
    ) {
        try {
            return peerNodeService.mineDemoBlock(peerId, request.minerAddress());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/peers/{peerId}/sync")
    public SyncResult syncFromPeer(@PathVariable String peerId) {
        try {
            return peerNodeService.syncFromPeer(peerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/chain/validate")
    public BlockchainStatus validateChain() {
        return getStatus();
    }

    @GetMapping("/chain/status")
    public BlockchainStatus getStatus() {
        return new BlockchainStatus(
                blockchainService.getBlocks().size(),
                blockchainService.getDifficulty(),
                blockchainService.getPendingTransactions().size(),
                blockchainService.isValid(),
                blockchainService.getCumulativeDifficulty()
        );
    }

    @GetMapping("/chain/forks")
    public List<BlockReference> getForkBlocks() {
        return blockchainService.getForkBlocks();
    }

    @GetMapping("/chain/orphans")
    public List<BlockReference> getOrphanBlocks() {
        return blockchainService.getOrphanBlocks();
    }

    @GetMapping("/chain/consensus")
    public ConsensusSettings getConsensusSettings() {
        return blockchainService.getConsensusSettings();
    }

    @PutMapping("/chain/consensus")
    public ConsensusSettings updateConsensusSettings(@Valid @RequestBody ConsensusSettingsRequest request) {
        return blockchainService.updateConsensusSettings(request.policy(), request.finalityDelayBlocks());
    }

    @GetMapping("/chain/branches")
    public List<ConsensusBranch> getConsensusBranches() {
        return blockchainService.getConsensusBranches();
    }

    @GetMapping("/ops/health")
    public OperationHealth getHealth() {
        boolean chainValid = blockchainService.isValid();
        return new OperationHealth(
                chainValid ? "UP" : "DEGRADED",
                chainValid,
                blockchainService.getBlocks().size(),
                blockchainService.getPendingTransactions().size(),
                blockchainService.isPersistenceEnabled(),
                blockchainService.getPersistenceType()
        );
    }

    @GetMapping("/ops/metrics")
    public OperationMetrics getMetrics() {
        OperationalMetricsService.Snapshot snapshot = operationalMetricsService.snapshot();
        return new OperationMetrics(
                blockchainService.getBlocks().size(),
                blockchainService.getPendingTransactions().size(),
                blockchainService.getCumulativeDifficulty(),
                blockchainService.getForkBlocks().size(),
                blockchainService.getOrphanBlocks().size(),
                peerNodeService.getPeerCount(),
                snapshot.validationRuns(),
                snapshot.minedBlocks(),
                snapshot.minedTransactions(),
                snapshot.miningNonceTotal(),
                snapshot.miningElapsedMsTotal(),
                snapshot.rejectedTransactions(),
                snapshot.acceptedBroadcastBlocks(),
                snapshot.rejectedBroadcastBlocks(),
                snapshot.peerSyncAttempts(),
                snapshot.peerSyncSuccesses(),
                snapshot.peerSyncAdoptions(),
                snapshot.transactionBroadcastAttempts(),
                snapshot.transactionBroadcastSuccesses(),
                snapshot.transactionBroadcastFailures(),
                snapshot.blockBroadcastAttempts(),
                snapshot.blockBroadcastSuccesses(),
                snapshot.blockBroadcastFailures(),
                snapshot.peerLatencyMsTotal(),
                snapshot.peerRetryAttempts(),
                snapshot.duplicateGossipMessages(),
                snapshot.forkAdoptionEvents()
        );
    }

    @PutMapping("/chain/difficulty")
    public BlockchainStatus updateDifficulty(@Valid @RequestBody DifficultyRequest request) {
        try {
            blockchainService.setDifficulty(request.difficulty());
            return getStatus();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/chain/tamper")
    public BlockchainStatus tamperChain(@Valid @RequestBody TamperBlockRequest request) {
        try {
            blockchainService.tamperBlock(request.index(), request.data());
            return getStatus();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/chain/reset")
    public ApiMessage resetChain() {
        blockchainService.reset();
        peerNodeService.resetPeers();
        return new ApiMessage("Blockchain reset with a new genesis block");
    }
}
