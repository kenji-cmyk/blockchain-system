package com.kna.backend.service;

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

import static com.kna.backend.pkg.validate.Validator.isChainValid;

@Service
public class BlockchainService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockchainService.class);

    private final List<Block> blockchain = new ArrayList<>();
    private final List<Transaction> pendingTransactions = new ArrayList<>();
    private final ChainPersistenceService chainPersistenceService;
    private int difficulty;
    private final double miningReward;

    public BlockchainService(
            @Value("${blockchain.difficulty:3}") int difficulty,
            @Value("${blockchain.mining-reward:10}") double miningReward,
            ChainPersistenceService chainPersistenceService
    ) {
        this.miningReward = miningReward;
        this.chainPersistenceService = chainPersistenceService;
        setDifficulty(difficulty);
        loadOrReset();
    }

    public synchronized List<Block> getBlocks() {
        return List.copyOf(blockchain);
    }

    public synchronized boolean replaceChainIfLongerAndValid(List<Block> candidateChain) {
        if (candidateChain == null || candidateChain.size() <= blockchain.size()) {
            return false;
        }
        if (!isChainValid(candidateChain, difficulty)) {
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
        Transaction transaction = Transaction.miningReward(data, 0.00000001);
        return mineTransactions(List.of(transaction));
    }

    public Wallet createWallet() {
        return CryptoUtil.generateWallet();
    }

    public synchronized Transaction createTransaction(String sender, String receiver, double amount, String privateKey) {
        validateTransactionInput(sender, receiver, amount, privateKey);

        Transaction transaction = new Transaction(sender, receiver, amount);
        transaction.sign(privateKey);

        if (!transaction.isValid()) {
            throw new IllegalArgumentException("Transaction signature is invalid");
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

        List<Transaction> transactionsToMine = new ArrayList<>(pendingTransactions);
        transactionsToMine.add(Transaction.miningReward(rewardAddress, miningReward));
        Block block = mineTransactions(transactionsToMine);
        pendingTransactions.clear();
        chainPersistenceService.saveChain(blockchain);
        return block;
    }

    public synchronized boolean isValid() {
        return isChainValid(blockchain, difficulty);
    }

    public synchronized void tamperBlock(int index, String data) {
        validateData(data);
        getBlock(index).tamperData(data);
    }

    public synchronized void reset() {
        blockchain.clear();
        pendingTransactions.clear();
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

        Block previousBlock = blockchain.getLast();
        Block block = new Block(blockchain.size(), transactions, previousBlock.getHash());
        mineAndLog(block, "local");
        blockchain.add(block);
        chainPersistenceService.saveChain(blockchain);
        return block;
    }

    private void loadOrReset() {
        chainPersistenceService.loadChain()
                .filter(chain -> isChainValid(chain, difficulty))
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

    private void validateTransactionInput(String sender, String receiver, double amount, String privateKey) {
        validateData(sender);
        validateData(receiver);
        validateData(privateKey);
        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than 0");
        }
    }
}
