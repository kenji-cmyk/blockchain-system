package com.kna.backend.service;

import com.kna.backend.dto.BlockReference;
import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.Wallet;
import com.kna.backend.pkg.utils.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.kna.backend.pkg.validate.Validator.cumulativeDifficulty;
import static com.kna.backend.pkg.validate.Validator.isChainValid;

@Service
public class BlockchainService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockchainService.class);

    private final List<Block> blockchain = new ArrayList<>();
    private final List<Transaction> pendingTransactions = new ArrayList<>();
    private final List<Block> forkBlocks = new ArrayList<>();
    private final List<Block> orphanBlocks = new ArrayList<>();
    private final ChainPersistenceService chainPersistenceService;
    private int difficulty;
    private final double miningReward;
    private final int maxTransactionsPerBlock;

    public BlockchainService(
            @Value("${blockchain.difficulty:3}") int difficulty,
            @Value("${blockchain.mining-reward:10}") double miningReward,
            @Value("${blockchain.max-transactions-per-block:5}") int maxTransactionsPerBlock,
            ChainPersistenceService chainPersistenceService
    ) {
        this.miningReward = miningReward;
        if (maxTransactionsPerBlock < 2) {
            throw new IllegalArgumentException("Max transactions per block must be at least 2");
        }
        this.maxTransactionsPerBlock = maxTransactionsPerBlock;
        this.chainPersistenceService = chainPersistenceService;
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
            return false;
        }
        if (cumulativeDifficulty(candidateChain) <= cumulativeDifficulty(blockchain)) {
            rememberCandidateBlocks(candidateChain);
            return false;
        }

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
        return CryptoUtil.generateWallet();
    }

    public synchronized Transaction createTransaction(String sender, String receiver, double amount, double fee, String privateKey) {
        validateTransactionInput(sender, receiver, amount, fee, privateKey);

        Transaction transaction = new Transaction(sender, receiver, amount, fee);
        transaction.sign(privateKey);
        return addPendingTransaction(transaction);
    }

    public synchronized Transaction addPendingTransaction(Transaction transaction) {
        if (!transaction.isValid()) {
            throw new IllegalArgumentException("Transaction signature is invalid");
        }

        boolean duplicate = pendingTransactions.stream()
                .anyMatch(existing -> existing.getTransactionId().equals(transaction.getTransactionId()));
        if (duplicate || isTransactionCommitted(transaction.getTransactionId())) {
            return transaction;
        }

        double availableBalance = getAvailableBalance(transaction.getSender());
        double totalCost = transaction.getAmount() + transaction.getFee();
        if (availableBalance < totalCost) {
            throw new IllegalArgumentException("Sender balance is insufficient");
        }

        pendingTransactions.add(transaction);
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
        chainPersistenceService.saveChain(blockchain);
        return block;
    }

    public synchronized boolean isValid() {
        return isChainValid(blockchain, difficulty, maxTransactionsPerBlock, miningReward);
    }

    public synchronized boolean acceptBroadcastBlock(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block must not be null");
        }

        Block previousBlock = blockchain.getLast();
        if (block.getIndex() != blockchain.size()) {
            rememberBlock(block);
            return false;
        }
        if (!previousBlock.getHash().equals(block.getPreviousHash())) {
            rememberBlock(block);
            return false;
        }

        List<Block> candidateChain = new ArrayList<>(blockchain);
        candidateChain.add(block);
        if (!isChainValid(candidateChain, difficulty, maxTransactionsPerBlock, miningReward)) {
            rememberBlock(block);
            return false;
        }

        blockchain.add(block);
        removeMinedPendingTransactions(block);
        chainPersistenceService.saveChain(blockchain);
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
        Block genesisBlock = new Block(0, List.of(Transaction.miningReward("GENESIS", miningReward)), "0");
        mineAndLog(genesisBlock, "genesis");
        blockchain.add(genesisBlock);
        chainPersistenceService.saveChain(blockchain);
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
        for (Block block : blockchain) {
            for (Transaction transaction : block.getTransactions()) {
                if (transaction.isMiningReward()) {
                    if (address.equals(transaction.getReceiver())) {
                        balance += transaction.getAmount();
                    }
                    continue;
                }

                if (address.equals(transaction.getSender())) {
                    balance -= transaction.getAmount() + transaction.getFee();
                }
                if (address.equals(transaction.getReceiver())) {
                    balance += transaction.getAmount();
                }
            }
        }
        return balance;
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
        return cumulativeDifficulty(blockchain);
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

    public synchronized void setDifficulty(int difficulty) {
        if (difficulty < 0 || difficulty > 6) {
            throw new IllegalArgumentException("Difficulty must be between 0 and 6");
        }
        this.difficulty = difficulty;
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

        Block previousBlock = blockchain.getLast();
        Block block = new Block(blockchain.size(), transactions, previousBlock.getHash());
        mineAndLog(block, "local");
        blockchain.add(block);
        chainPersistenceService.saveChain(blockchain);
        return block;
    }

    private void removeMinedPendingTransactions(Block block) {
        List<String> minedTransactionIds = block.getTransactions()
                .stream()
                .filter(transaction -> !transaction.isMiningReward())
                .map(Transaction::getTransactionId)
                .toList();
        pendingTransactions.removeIf(transaction -> minedTransactionIds.contains(transaction.getTransactionId()));
    }

    private void loadOrReset() {
        chainPersistenceService.loadChain()
                .filter(chain -> isChainValid(chain, difficulty, maxTransactionsPerBlock, miningReward))
                .ifPresentOrElse(
                        chain -> {
                            blockchain.clear();
                            blockchain.addAll(chain);
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

        LOGGER.info(
                "Mined {} block index={} difficulty={} nonceCount={} elapsedMs={} hash={}",
                source,
                block.getIndex(),
                difficulty,
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
    }

    private boolean isTransactionCommitted(String transactionId) {
        return blockchain.stream()
                .flatMap(block -> block.getTransactions().stream())
                .anyMatch(transaction -> transaction.getTransactionId().equals(transactionId));
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
