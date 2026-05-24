package com.kna.backend.service;

import com.kna.backend.dto.PeerSummary;
import com.kna.backend.dto.SyncResult;
import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.kna.backend.pkg.validate.Validator.isChainValid;

@Service
public class PeerNodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerNodeService.class);

    private final BlockchainService blockchainService;
    private final Map<String, List<Block>> peerChains = new LinkedHashMap<>();

    public PeerNodeService(BlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }

    public synchronized PeerSummary registerPeer(String peerId) {
        validatePeerId(peerId);
        peerChains.putIfAbsent(peerId, new ArrayList<>(blockchainService.getBlocks()));
        return toSummary(peerId);
    }

    public synchronized List<PeerSummary> getPeers() {
        return peerChains.keySet().stream()
                .map(this::toSummary)
                .toList();
    }

    public synchronized List<Block> getPeerChain(String peerId) {
        return List.copyOf(getMutablePeerChain(peerId));
    }

    public synchronized Block mineDemoBlock(String peerId, String minerAddress) {
        validateData(minerAddress, "Miner address must not be blank");

        List<Block> chain = getMutablePeerChain(peerId);
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
        List<Block> peerChain = getMutablePeerChain(peerId);
        int localSizeBefore = blockchainService.getBlocks().size();
        boolean peerValid = isChainValid(
                peerChain,
                blockchainService.getDifficulty(),
                blockchainService.getMaxTransactionsPerBlock()
        );
        boolean adopted = blockchainService.replaceChainIfLongerAndValid(peerChain);
        int localSizeAfter = blockchainService.getBlocks().size();

        String message;
        if (adopted) {
            message = "Local chain replaced with longer valid peer chain";
        } else if (!peerValid) {
            message = "Peer chain is invalid";
        } else {
            message = "Local chain is already at least as long as the peer chain";
        }

        return new SyncResult(peerId, peerChain.size(), localSizeBefore, localSizeAfter, peerValid, adopted, message);
    }

    public synchronized void resetPeers() {
        peerChains.clear();
    }

    private PeerSummary toSummary(String peerId) {
        List<Block> chain = peerChains.get(peerId);
        return new PeerSummary(
                peerId,
                chain.size(),
                isChainValid(
                        chain,
                        blockchainService.getDifficulty(),
                        blockchainService.getMaxTransactionsPerBlock()
                )
        );
    }

    private List<Block> getMutablePeerChain(String peerId) {
        validatePeerId(peerId);
        List<Block> chain = peerChains.get(peerId);
        if (chain == null) {
            throw new IllegalArgumentException("Peer does not exist");
        }
        return chain;
    }

    private void validatePeerId(String peerId) {
        validateData(peerId, "Peer id must not be blank");
    }

    private void validateData(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
