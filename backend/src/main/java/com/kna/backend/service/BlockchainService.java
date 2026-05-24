package com.kna.backend.service;

import com.kna.backend.entity.Block;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.kna.backend.pkg.validate.Validator.isChainValid;

@Service
public class BlockchainService {

    private final List<Block> blockchain = new ArrayList<>();
    private int difficulty;

    public BlockchainService(@Value("${blockchain.difficulty:3}") int difficulty) {
        setDifficulty(difficulty);
        reset();
    }

    public synchronized List<Block> getBlocks() {
        return List.copyOf(blockchain);
    }

    public synchronized Block getBlock(int index) {
        if (index < 0 || index >= blockchain.size()) {
            throw new IllegalArgumentException("Block index does not exist");
        }
        return blockchain.get(index);
    }

    public synchronized Block addBlock(String data) {
        validateData(data);
        Block previousBlock = blockchain.getLast();
        Block block = new Block(blockchain.size(), data, previousBlock.getHash());
        block.mineBlock(difficulty);
        blockchain.add(block);
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
        Block genesisBlock = new Block(0, "Genesis Block", "0");
        genesisBlock.mineBlock(difficulty);
        blockchain.add(genesisBlock);
    }

    public synchronized int getDifficulty() {
        return difficulty;
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
}
