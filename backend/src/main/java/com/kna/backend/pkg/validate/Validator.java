package com.kna.backend.pkg.validate;

import com.kna.backend.entity.Block;

import java.util.List;

public class Validator {

    public static boolean isChainValid(List<Block> blockChain, int difficulty) {
        String hashTarget = "0".repeat(difficulty);

        for (int i = 0; i < blockChain.size(); ++i) {
            Block currentBlock = blockChain.get(i);

            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                return false;
            }

            if (!currentBlock.getHash().startsWith(hashTarget)) {
                return false;
            }

            String expectedPreviousHash = i == 0 ? "0" : blockChain.get(i - 1).getHash();
            if (!expectedPreviousHash.equals(currentBlock.getPreviousHash())) {
                return false;
            }

            if (currentBlock.getTransactions().stream().anyMatch(transaction -> !transaction.isValid())) {
                return false;
            }
        }

        return true;
    }
}
