package com.kna.backend.service;

import com.kna.backend.consensus.ConsensusPolicy;
import com.kna.backend.dto.BlockReference;
import com.kna.backend.dto.ConsensusBranch;
import com.kna.backend.dto.ConsensusSettings;
import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.TransactionInput;
import com.kna.backend.entity.TransactionOutput;
import com.kna.backend.entity.UtxoEntry;
import com.kna.backend.entity.UtxoKey;
import com.kna.backend.entity.Wallet;
import com.kna.backend.pkg.utils.CryptoUtil;
import com.kna.backend.pkg.money.MoneyUnits;
import com.kna.backend.pkg.validate.UtxoLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.kna.backend.pkg.validate.Validator.cumulativeDifficulty;
import static com.kna.backend.pkg.validate.Validator.isChainValid;

@Service
public class BlockchainService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockchainService.class);

    private final List<Block> blockchain = new ArrayList<>();
    private final List<Transaction> pendingTransactions = new ArrayList<>();
    private final List<Block> forkBlocks = new ArrayList<>();
    private final List<Block> orphanBlocks = new ArrayList<>();
    private final List<ConsensusBranch> consensusBranches = new ArrayList<>();
    private final ChainPersistenceService chainPersistenceService;
    private final OperationalMetricsService operationalMetricsService;
    private int difficulty;
    private ConsensusPolicy consensusPolicy;
    private int finalityDelayBlocks;
    private final double miningReward;
    private final int maxTransactionsPerBlock;

    public BlockchainService(
            @Value("${blockchain.difficulty:3}") int difficulty,
            @Value("${blockchain.mining-reward:10}") double miningReward,
            @Value("${blockchain.max-transactions-per-block:5}") int maxTransactionsPerBlock,
            @Value("${blockchain.consensus.policy:cumulative-difficulty}") String consensusPolicy,
            @Value("${blockchain.consensus.finality-delay-blocks:0}") int finalityDelayBlocks,
            ChainPersistenceService chainPersistenceService,
            OperationalMetricsService operationalMetricsService
    ) {
        this.miningReward = miningReward;
        if (maxTransactionsPerBlock < 2) {
            throw new IllegalArgumentException("Max transactions per block must be at least 2");
        }
        this.maxTransactionsPerBlock = maxTransactionsPerBlock;
        this.chainPersistenceService = chainPersistenceService;
        this.operationalMetricsService = operationalMetricsService;
        this.consensusPolicy = ConsensusPolicy.fromKey(consensusPolicy);
        setFinalityDelayBlocks(finalityDelayBlocks);
        setDifficulty(difficulty);
        loadOrReset();
    }

    public synchronized List<Block> getBlocks() {
        return List.copyOf(blockchain);
    }

    public synchronized boolean replaceChainIfLongerAndValid(List<Block> candidateChain) {
        return replaceChainIfStrongerAndValid(candidateChain);
    }

    public synchronized boolean replaceChainIfStrongerAndValid(List<Block> candidateChain) {
        if (candidateChain == null || candidateChain.isEmpty()) {
            return false;
        }
        if (!isChainValid(candidateChain, difficulty, maxTransactionsPerBlock, miningReward)) {
            rememberCandidateBlocks(candidateChain);
            recordConsensusBranch(candidateChain, "rejected", "Candidate chain is invalid");
            return false;
        }

        if (rewritesFinalizedBlocks(candidateChain)) {
            rememberCandidateBlocks(candidateChain);
            recordConsensusBranch(candidateChain, "rejected", "Candidate chain would reorganize finalized blocks");
            return false;
        }

        if (!isCandidateStronger(candidateChain)) {
            rememberCandidateBlocks(candidateChain);
            recordConsensusBranch(candidateChain, "fork", notSelectedReason(candidateChain));
            return false;
        }

        recordConsensusBranch(candidateChain, "accepted", "Candidate chain accepted by " + consensusPolicy.key());
        blockchain.clear();
        blockchain.addAll(candidateChain);
        pendingTransactions.clear();
        chainPersistenceService.saveChain(blockchain);
        return true;
    }

    public synchronized Block getBlock(int index) {
        if (index < 0 || index >= blockchain.size()) {
            throw new IllegalArgumentException("Block index does not exist");
        }
        return blockchain.get(index);
    }

    public synchronized Block addBlock(String data) {
        validateData(data);
        Transaction transaction = Transaction.miningReward(data, miningReward);
        return mineTransactions(List.of(transaction));
    }

    public Wallet createWallet() {
        Wallet wallet = CryptoUtil.generateWallet();
        chainPersistenceService.saveWallet(wallet);
        return wallet;
    }

    public synchronized Transaction createTransaction(String sender, String receiver, double amount, double fee, String privateKey) {
        try {
            validateTransactionInput(sender, receiver, amount, fee, privateKey);
            Transaction transaction = createUtxoTransaction(sender, receiver, amount, fee);
            transaction.sign(privateKey);
            return addPendingTransaction(transaction);
        } catch (IllegalArgumentException exception) {
            operationalMetricsService.recordRejectedTransaction();
            LOGGER.warn("event=transaction_rejected source=local reason=\"{}\"", exception.getMessage());
            throw exception;
        }
    }

    public synchronized Transaction addPendingTransaction(Transaction transaction) {
        if (!transaction.isValid()) {
            operationalMetricsService.recordRejectedTransaction();
            LOGGER.warn("event=transaction_rejected source=peer reason=\"Transaction signature is invalid\"");
            throw new IllegalArgumentException("Transaction signature is invalid");
        }

        boolean duplicate = pendingTransactions.stream()
                .anyMatch(existing -> existing.getTransactionId().equals(transaction.getTransactionId()));
        if (duplicate || isTransactionCommitted(transaction.getTransactionId())) {
            return transaction;
        }

        Map<UtxoKey, UtxoEntry> pendingLedger = ledgerAfterPendingTransactions();
        if (!UtxoLedger.applyPendingTransaction(transaction, pendingLedger)) {
            operationalMetricsService.recordRejectedTransaction();
            LOGGER.warn("event=transaction_rejected source=peer transactionId={} reason=\"Sender balance is insufficient\"", transaction.getTransactionId());
            throw new IllegalArgumentException("Sender balance is insufficient");
        }

        pendingTransactions.add(transaction);
        chainPersistenceService.savePendingTransactions(pendingTransactions);
        return transaction;
    }

    public synchronized List<Transaction> getPendingTransactions() {
        return List.copyOf(pendingTransactions);
    }

    public synchronized Block minePendingTransactions(String rewardAddress) {
        validateData(rewardAddress);
        if (pendingTransactions.isEmpty()) {
            throw new IllegalArgumentException("There are no pending transactions to mine");
        }

        int pendingLimit = maxTransactionsPerBlock - 1;
        List<Transaction> transactionsToMine = new ArrayList<>(
                pendingTransactions.subList(0, Math.min(pendingLimit, pendingTransactions.size()))
        );
        double collectedFees = transactionsToMine.stream()
                .mapToDouble(Transaction::getFee)
                .sum();
        transactionsToMine.add(Transaction.miningReward(rewardAddress, miningReward + collectedFees));
        Block block = mineTransactions(transactionsToMine);
        pendingTransactions.subList(0, transactionsToMine.size() - 1).clear();
        chainPersistenceService.savePendingTransactions(pendingTransactions);
        chainPersistenceService.saveChain(blockchain);
        return block;
    }

    public synchronized boolean isValid() {
        operationalMetricsService.recordValidation();
        return isChainValid(blockchain, difficulty, maxTransactionsPerBlock, miningReward);
    }

    public synchronized boolean acceptBroadcastBlock(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block must not be null");
        }

        Block previousBlock = blockchain.getLast();
        if (block.getIndex() != blockchain.size()) {
            rememberBlock(block);
            recordSingleBlockBranch(block, "orphan", "Block index is not the next expected index");
            operationalMetricsService.recordBroadcastBlockRejected();
            LOGGER.warn("event=block_rejected source=peer reason=\"unexpected index\" index={} expectedIndex={} hash={}", block.getIndex(), blockchain.size(), block.getHash());
            return false;
        }
        if (!previousBlock.getHash().equals(block.getPreviousHash())) {
            rememberBlock(block);
            recordSingleBlockBranch(block, "fork", "Block points to a competing fork parent");
            operationalMetricsService.recordBroadcastBlockRejected();
            LOGGER.warn("event=block_rejected source=peer reason=\"previous hash mismatch\" index={} hash={}", block.getIndex(), block.getHash());
            return false;
        }

        List<Block> candidateChain = new ArrayList<>(blockchain);
        candidateChain.add(block);
        if (!isChainValid(candidateChain, difficulty, maxTransactionsPerBlock, miningReward)) {
            rememberBlock(block);
            recordConsensusBranch(candidateChain, "rejected", "Candidate chain is invalid");
            operationalMetricsService.recordBroadcastBlockRejected();
            LOGGER.warn("event=block_rejected source=peer reason=\"candidate chain invalid\" index={} hash={}", block.getIndex(), block.getHash());
            return false;
        }

        blockchain.add(block);
        removeMinedPendingTransactions(block);
        recordConsensusBranch(new ArrayList<>(blockchain), "accepted", "Broadcast block extends the local chain");
        reattachKnownOrphans();
        chainPersistenceService.savePendingTransactions(pendingTransactions);
        chainPersistenceService.saveChain(blockchain);
        operationalMetricsService.recordBroadcastBlockAccepted();
        LOGGER.info("event=block_accepted source=peer index={} hash={}", block.getIndex(), block.getHash());
        return true;
    }

    public synchronized void tamperBlock(int index, String data) {
        validateData(data);
        getBlock(index).tamperData(data);
    }

    public synchronized void reset() {
        blockchain.clear();
        pendingTransactions.clear();
        forkBlocks.clear();
        orphanBlocks.clear();
        consensusBranches.clear();
        Block genesisBlock = new Block(0, List.of(Transaction.miningReward("GENESIS", miningReward)), "0");
        mineAndLog(genesisBlock, "genesis");
        blockchain.add(genesisBlock);
        chainPersistenceService.saveChain(blockchain);
        chainPersistenceService.savePendingTransactions(pendingTransactions);
        operationalMetricsService.resetWindow();
        LOGGER.info("event=chain_reset chainSize={} pendingTransactions=0", blockchain.size());
    }

    public synchronized int getDifficulty() {
        return difficulty;
    }

    public double getMiningReward() {
        return miningReward;
    }

    public int getMaxTransactionsPerBlock() {
        return maxTransactionsPerBlock;
    }

    public synchronized double getBalance(String address) {
        validateData(address);
        double balance = 0;
        try {
            balance = UtxoLedger.balanceOf(UtxoLedger.replay(blockchain, miningReward), address);
            return balance;
        } catch (IllegalArgumentException exception) {
            return balance;
        }
    }

    public synchronized double getAvailableBalance(String address) {
        double balance = getBalance(address);
        double pendingOutgoing = pendingTransactions.stream()
                .filter(transaction -> address.equals(transaction.getSender()))
                .mapToDouble(transaction -> transaction.getAmount() + transaction.getFee())
                .sum();
        return balance - pendingOutgoing;
    }

    public synchronized long getCumulativeDifficulty() {
        return cumulativeDifficulty(blockchain, difficulty);
    }

    public synchronized ConsensusSettings getConsensusSettings() {
        return new ConsensusSettings(consensusPolicy.key(), finalityDelayBlocks);
    }

    public synchronized ConsensusSettings updateConsensusSettings(String policy, int finalityDelayBlocks) {
        this.consensusPolicy = ConsensusPolicy.fromKey(policy);
        setFinalityDelayBlocks(finalityDelayBlocks);
        return getConsensusSettings();
    }

    public synchronized List<ConsensusBranch> getConsensusBranches() {
        return List.copyOf(consensusBranches);
    }

    public synchronized List<BlockReference> getForkBlocks() {
        return forkBlocks.stream()
                .map(this::toReference)
                .toList();
    }

    public synchronized List<BlockReference> getOrphanBlocks() {
        return orphanBlocks.stream()
                .map(this::toReference)
                .toList();
    }

    public boolean isPersistenceEnabled() {
        return chainPersistenceService.isEnabled();
    }

    public String getPersistenceType() {
        return chainPersistenceService.getType();
    }

    public synchronized void setDifficulty(int difficulty) {
        if (difficulty < 0 || difficulty > 6) {
            throw new IllegalArgumentException("Difficulty must be between 0 and 6");
        }
        this.difficulty = difficulty;
    }

    private void setFinalityDelayBlocks(int finalityDelayBlocks) {
        if (finalityDelayBlocks < 0) {
            throw new IllegalArgumentException("Finality delay blocks must be greater than or equal to 0");
        }
        this.finalityDelayBlocks = finalityDelayBlocks;
    }

    private void validateData(String data) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("Block data must not be blank");
        }
    }

    private Block mineTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            if (!transaction.isValid()) {
                throw new IllegalArgumentException("Block contains an invalid transaction");
            }
        }
        if (transactions.size() > maxTransactionsPerBlock) {
            throw new IllegalArgumentException("Block exceeds the transaction count limit");
        }
        List<Block> candidateChain = new ArrayList<>(blockchain);
        Block previousBlock = blockchain.getLast();
        Block block = new Block(blockchain.size(), transactions, previousBlock.getHash());
        mineAndLog(block, "local");
        candidateChain.add(block);

        if (!isChainValid(candidateChain, difficulty, maxTransactionsPerBlock, miningReward)) {
            throw new IllegalArgumentException("Block contains invalid ledger transitions");
        }
        blockchain.add(block);
        chainPersistenceService.saveChain(blockchain);
        return block;
    }

    private Transaction createUtxoTransaction(String sender, String receiver, double amount, double fee) {
        Map<UtxoKey, UtxoEntry> ledger = ledgerAfterPendingTransactions();
        double requiredAmount = amount + fee;
        double selectedAmount = 0;
        List<TransactionInput> inputs = new ArrayList<>();

        for (UtxoEntry entry : ledger.values()) {
            if (!sender.equals(entry.owner())) {
                continue;
            }
            inputs.add(new TransactionInput(entry.transactionId(), entry.outputIndex()));
            selectedAmount += entry.amount();
            if (selectedAmount + 0.00000001 >= requiredAmount) {
                break;
            }
        }

        if (selectedAmount + 0.00000001 < requiredAmount) {
            throw new IllegalArgumentException("Sender balance is insufficient");
        }

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(new TransactionOutput(receiver, amount));
        double change = selectedAmount - requiredAmount;
        if (change > 0.00000001) {
            outputs.add(new TransactionOutput(sender, change));
        }

        return new Transaction(sender, receiver, amount, fee, inputs, outputs);
    }

    private Map<UtxoKey, UtxoEntry> ledgerAfterPendingTransactions() {
        Map<UtxoKey, UtxoEntry> ledger = UtxoLedger.mutableCopy(UtxoLedger.replay(blockchain, miningReward));
        for (Transaction pendingTransaction : pendingTransactions) {
            if (!UtxoLedger.applyPendingTransaction(pendingTransaction, ledger)) {
                throw new IllegalArgumentException("Pending transaction pool contains invalid ledger transitions");
            }
        }
        return ledger;
    }

    private void removeMinedPendingTransactions(Block block) {
        List<String> minedTransactionIds = block.getTransactions()
                .stream()
                .filter(transaction -> !transaction.isMiningReward())
                .map(Transaction::getTransactionId)
                .toList();
        pendingTransactions.removeIf(transaction -> minedTransactionIds.contains(transaction.getTransactionId()));
        chainPersistenceService.savePendingTransactions(pendingTransactions);
    }

    private void loadOrReset() {
        chainPersistenceService.loadChain()
                .filter(chain -> isChainValid(chain, difficulty, maxTransactionsPerBlock, miningReward))
                .ifPresentOrElse(
                        chain -> {
                            blockchain.clear();
                            blockchain.addAll(chain);
                            pendingTransactions.clear();
                            pendingTransactions.addAll(chainPersistenceService.loadPendingTransactions().orElse(List.of()));
                        },
                        this::reset
                );
    }

    private void mineAndLog(Block block, String source) {
        long start = System.currentTimeMillis();
        int nonceBefore = block.getNonce();
        block.mineBlock(difficulty);
        long elapsedMs = System.currentTimeMillis() - start;
        int nonceCount = block.getNonce() - nonceBefore;
        operationalMetricsService.recordMinedBlock(block.getTransactions().size(), nonceCount, elapsedMs);

        LOGGER.info(
                "event=block_mined source={} index={} difficulty={} transactionCount={} nonceCount={} elapsedMs={} hash={}",
                source,
                block.getIndex(),
                difficulty,
                block.getTransactions().size(),
                nonceCount,
                elapsedMs,
                block.getHash()
        );
    }

    private void validateTransactionInput(String sender, String receiver, double amount, double fee, String privateKey) {
        validateData(sender);
        validateData(receiver);
        validateData(privateKey);
        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than 0");
        }
        if (fee < 0) {
            throw new IllegalArgumentException("Transaction fee must be greater than or equal to 0");
        }
        try {
            MoneyUnits.toUnits(amount);
            MoneyUnits.toUnits(fee);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Transaction amount and fee support up to 8 decimal places", exception);
        }
    }

    private boolean isTransactionCommitted(String transactionId) {
        return blockchain.stream()
                .flatMap(block -> block.getTransactions().stream())
                .anyMatch(transaction -> transaction.getTransactionId().equals(transactionId));
    }

    private boolean isCandidateStronger(List<Block> candidateChain) {
        return switch (consensusPolicy) {
            case LONGEST_CHAIN -> candidateChain.size() > blockchain.size();
            case CUMULATIVE_DIFFICULTY -> cumulativeDifficulty(candidateChain, difficulty) > cumulativeDifficulty(blockchain, difficulty);
        };
    }

    private String notSelectedReason(List<Block> candidateChain) {
        return switch (consensusPolicy) {
            case LONGEST_CHAIN -> "Valid competing fork was kept because it is not longer than the local chain";
            case CUMULATIVE_DIFFICULTY ->
                    "Valid competing fork was kept because it does not have more cumulative difficulty";
        };
    }

    private boolean rewritesFinalizedBlocks(List<Block> candidateChain) {
        if (finalityDelayBlocks <= 0) {
            return false;
        }
        int finalizedIndex = blockchain.size() - 1 - finalityDelayBlocks;
        if (finalizedIndex < 0) {
            return false;
        }
        if (candidateChain.size() <= finalizedIndex) {
            return true;
        }
        for (int index = 0; index <= finalizedIndex; index++) {
            if (!blockchain.get(index).getHash().equals(candidateChain.get(index).getHash())) {
                return true;
            }
        }
        return false;
    }

    private void reattachKnownOrphans() {
        boolean attached;
        do {
            attached = false;
            Block nextOrphan = nextAttachableOrphan();
            if (nextOrphan == null) {
                return;
            }

            List<Block> candidateChain = new ArrayList<>(blockchain);
            candidateChain.add(nextOrphan);
            orphanBlocks.removeIf(block -> block.getHash().equals(nextOrphan.getHash()));

            if (!isChainValid(candidateChain, difficulty, maxTransactionsPerBlock, miningReward)) {
                rememberBlock(nextOrphan);
                recordConsensusBranch(candidateChain, "rejected", "Orphan could not be reattached because the candidate chain is invalid");
                continue;
            }

            blockchain.add(nextOrphan);
            removeMinedPendingTransactions(nextOrphan);
            operationalMetricsService.recordBroadcastBlockAccepted();
            recordConsensusBranch(new ArrayList<>(blockchain), "accepted", "Orphan reattached after missing parent arrived");
            LOGGER.info("event=block_accepted source=orphan_reattach index={} hash={}", nextOrphan.getIndex(), nextOrphan.getHash());
            attached = true;
        } while (attached);
    }

    private Block nextAttachableOrphan() {
        String expectedPreviousHash = blockchain.getLast().getHash();
        int expectedIndex = blockchain.size();
        return orphanBlocks.stream()
                .filter(block -> block.getIndex() == expectedIndex)
                .filter(block -> expectedPreviousHash.equals(block.getPreviousHash()))
                .findFirst()
                .orElse(null);
    }

    private void recordSingleBlockBranch(Block block, String fallbackStatus, String reason) {
        String status = blockchain.stream().anyMatch(existing -> existing.getHash().equals(block.getPreviousHash()))
                ? "fork"
                : fallbackStatus;
        String resolvedReason = "fork".equals(status) && !reason.contains("fork")
                ? "Block is a valid competing fork candidate"
                : reason;
        ConsensusBranch branch = new ConsensusBranch(
                UUID.randomUUID().toString(),
                status,
                resolvedReason,
                consensusPolicy.key(),
                finalityDelayBlocks,
                1,
                cumulativeDifficulty(List.of(block), difficulty),
                blockchain.size(),
                cumulativeDifficulty(blockchain, difficulty),
                parentIndex(block.getPreviousHash()),
                parentHash(block.getPreviousHash()),
                toReference(block),
                Instant.now()
        );
        addConsensusBranch(branch);
    }

    private void recordConsensusBranch(List<Block> candidateChain, String status, String reason) {
        if (candidateChain == null || candidateChain.isEmpty()) {
            return;
        }
        Block tip = candidateChain.getLast();
        ConsensusBranch branch = new ConsensusBranch(
                UUID.randomUUID().toString(),
                status,
                reason,
                consensusPolicy.key(),
                finalityDelayBlocks,
                candidateChain.size(),
                cumulativeDifficulty(candidateChain, difficulty),
                blockchain.size(),
                cumulativeDifficulty(blockchain, difficulty),
                commonAncestorIndex(candidateChain),
                commonAncestorHash(candidateChain),
                toReference(tip),
                Instant.now()
        );
        addConsensusBranch(branch);
    }

    private void addConsensusBranch(ConsensusBranch branch) {
        boolean duplicate = consensusBranches.stream()
                .anyMatch(existing -> existing.tip().hash().equals(branch.tip().hash())
                        && existing.status().equals(branch.status())
                        && existing.reason().equals(branch.reason()));
        if (!duplicate) {
            consensusBranches.add(branch);
        }
        if (consensusBranches.size() > 50) {
            consensusBranches.removeFirst();
        }
    }

    private Integer commonAncestorIndex(List<Block> candidateChain) {
        int commonIndex = -1;
        int max = Math.min(blockchain.size(), candidateChain.size());
        for (int index = 0; index < max; index++) {
            if (!blockchain.get(index).getHash().equals(candidateChain.get(index).getHash())) {
                break;
            }
            commonIndex = index;
        }
        return commonIndex == -1 ? null : commonIndex;
    }

    private String commonAncestorHash(List<Block> candidateChain) {
        Integer index = commonAncestorIndex(candidateChain);
        return index == null ? null : blockchain.get(index).getHash();
    }

    private Integer parentIndex(String parentHash) {
        for (int index = 0; index < blockchain.size(); index++) {
            if (blockchain.get(index).getHash().equals(parentHash)) {
                return index;
            }
        }
        return null;
    }

    private String parentHash(String parentHash) {
        return parentIndex(parentHash) == null ? null : parentHash;
    }

    private void rememberCandidateBlocks(List<Block> candidateChain) {
        candidateChain.stream()
                .filter(block -> blockchain.stream().noneMatch(existing -> existing.getHash().equals(block.getHash())))
                .forEach(this::rememberBlock);
    }

    private void rememberBlock(Block block) {
        if (blockchain.stream().anyMatch(existing -> existing.getHash().equals(block.getPreviousHash()))) {
            addUnique(forkBlocks, block);
            return;
        }
        addUnique(orphanBlocks, block);
    }

    private void addUnique(List<Block> blocks, Block block) {
        boolean duplicate = blocks.stream().anyMatch(existing -> existing.getHash().equals(block.getHash()));
        if (!duplicate) {
            blocks.add(block);
        }
    }

    private BlockReference toReference(Block block) {
        return new BlockReference(block.getIndex(), block.getHash(), block.getPreviousHash());
    }
}
