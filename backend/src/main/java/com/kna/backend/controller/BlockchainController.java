package com.kna.backend.controller;

import com.kna.backend.dto.AddBlockRequest;
import com.kna.backend.dto.ApiMessage;
import com.kna.backend.dto.BlockchainStatus;
import com.kna.backend.dto.CreateTransactionRequest;
import com.kna.backend.dto.DifficultyRequest;
import com.kna.backend.dto.MinePeerBlockRequest;
import com.kna.backend.dto.MineTransactionsRequest;
import com.kna.backend.dto.PeerSummary;
import com.kna.backend.dto.RegisterPeerRequest;
import com.kna.backend.dto.SyncResult;
import com.kna.backend.dto.TamperBlockRequest;
import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.Wallet;
import com.kna.backend.service.BlockchainService;
import com.kna.backend.service.PeerNodeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public BlockchainController(BlockchainService blockchainService, PeerNodeService peerNodeService) {
        this.blockchainService = blockchainService;
        this.peerNodeService = peerNodeService;
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
            return blockchainService.addBlock(request.data());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/wallets/new")
    public Wallet createWallet() {
        return blockchainService.createWallet();
    }

    @GetMapping("/transactions/pending")
    public List<Transaction> getPendingTransactions() {
        return blockchainService.getPendingTransactions();
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        try {
            return blockchainService.createTransaction(
                    request.sender(),
                    request.receiver(),
                    request.amount(),
                    request.privateKey()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/transactions/mine")
    @ResponseStatus(HttpStatus.CREATED)
    public Block minePendingTransactions(@Valid @RequestBody MineTransactionsRequest request) {
        try {
            return blockchainService.minePendingTransactions(request.rewardAddress());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/peers")
    @ResponseStatus(HttpStatus.CREATED)
    public PeerSummary registerPeer(@Valid @RequestBody RegisterPeerRequest request) {
        try {
            return peerNodeService.registerPeer(request.peerId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/peers")
    public List<PeerSummary> getPeers() {
        return peerNodeService.getPeers();
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
                blockchainService.isValid()
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
